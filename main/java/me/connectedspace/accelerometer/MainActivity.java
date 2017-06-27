package me.connectedspace.accelerometer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.text.InputType;
import android.util.Log;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import me.connectedspace.accelerometer.LogService.accelBinder;

import static android.content.Intent.ACTION_BATTERY_LOW;
import static android.os.SystemClock.uptimeMillis;
import static java.lang.System.currentTimeMillis;

public class MainActivity extends AppCompatActivity {
    Context appContext;
    private boolean serviceBound;
    private LogService loggingService;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(TAG, "serviceDisconnected");
            serviceBound = false;
            viewUpdater.stop();
            loggingService = null;
        }
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "service connected");
            accelBinder binder = (accelBinder) service;
            loggingService = binder.getService();
            serviceBound = true;
            viewUpdater.run();
        }
    };
    private Intent bindIntent;
    private TextView currentTime, currentAccel, averageInter;
    private EditText hourText, minuteText, secText, millisecText, intervalText;
    private SwitchCompat switch1;
    private ViewUpdater viewUpdater;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        appContext = getApplicationContext();
        viewUpdater = new ViewUpdater();
        setContentView(R.layout.activity_main);
        currentTime = (TextView) findViewById (R.id.currentTime);
        currentAccel = (TextView) findViewById (R.id.currentAccel);
        averageInter = (TextView) findViewById(R.id.averageInterval);
        hourText = (EditText) findViewById(R.id.hourText);
        minuteText = (EditText) findViewById(R.id.minuteText);
        secText = (EditText) findViewById(R.id.secText);
        millisecText = (EditText) findViewById(R.id.millisecText);
        intervalText = (EditText) findViewById(R.id.interval);
        switch1 = (SwitchCompat) findViewById(R.id.switch1);

        ActivityCompat.requestPermissions(MainActivity.this,
                new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.BODY_SENSORS,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WAKE_LOCK,
                            Manifest.permission.ACCESS_WIFI_STATE},
                100);

        switch1.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (serviceBound) {
                            if (isChecked && !loggingService.configuration.scheduled()) {
                                isIgnoringBatteryOptimizations();
                                loggingService.configuration.set(Integer.parseInt(hourText.getText().toString()),
                                        Integer.parseInt(minuteText.getText().toString()),
                                        Integer.parseInt(secText.getText().toString()),
                                        Integer.parseInt(millisecText.getText().toString()),
                                        Integer.parseInt(intervalText.getText().toString()));
                                viewUpdater.run();
                            } else if (!isChecked && loggingService.configuration.scheduled()) {
                                loggingService.configuration.clear();
                            }
                        }
                    }
                }
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");
        //create logging service
        bindIntent = new Intent(this, LogService.class);
        startService(bindIntent);
        bindService(bindIntent, serviceConnection, Context.BIND_DEBUG_UNBIND);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
        viewUpdater.run();

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");
        viewUpdater.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(TAG, "onStop");
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.v(TAG, "onRestart");
        if (!serviceBound) {
            startService(bindIntent);
            bindService(bindIntent, serviceConnection, Context.BIND_DEBUG_UNBIND);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
    }

    private final class ViewUpdater implements SensorEventListener {
        private Handler handler;
        private Runnable runnable;
        private Runnable batteryCheck;
        private float[] pastX = new float[100];
        private float[] pastY = new float[100];
        private float[] pastZ = new float[100];
        private long[] pastFreq = new long[100];
        private int index;
        private long previousTime;
        private int hour, minute, second, milli; //the numbers displayed on UI
        private int interval = 10;
        private SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

        private ViewUpdater() {
            handler = new Handler(getMainLooper());
            runnable = new Runnable() {
                @Override
                public void run() {
                    currentTime.setText(dateFormatter.format(new Date(currentTimeMillis())));
                    if (serviceBound) {
                        currentAccel.setText(String.valueOf(average(pastX))
                                + "\n" + String.valueOf(average(pastY))
                                + "\n" + String.valueOf(average(pastZ)));
                        averageInter.setText(String.valueOf(average(pastFreq)));
                        handler.postDelayed(this, 200);
                    } else {
                        currentAccel.setText("Service not started.");
                    }
                }
            };

            batteryCheck = new Runnable() {
                @Override
                public void run() {
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = registerReceiver(null, ifilter);

                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int percent = (level*100)/scale;

                    if (percent < 5) {
                        Toast.makeText(MainActivity.this,
                                "Batter level is low.", Toast.LENGTH_LONG)
                                .show();
                        switch1.setChecked(false);
                    } else {
                        handler.postDelayed(this, 120000);
                    }
                }
            };
        }

        private void run() {
            this.stop();
            if (!serviceBound || !loggingService.configuration.scheduled()) {
                Calendar calendar = Calendar.getInstance();
                hour = calendar.get(Calendar.HOUR_OF_DAY);
                minute = calendar.get(Calendar.MINUTE);
                second = calendar.get(Calendar.SECOND);
            } else {
                hour = loggingService.configuration.get()[0];
                minute = loggingService.configuration.get()[1];
                second = loggingService.configuration.get()[2];
                milli = loggingService.configuration.get()[3];
                interval = loggingService.configuration.get()[4];
                switch1.setChecked(true);
            }
            hourText.setText(String.valueOf(hour));
            minuteText.setText(String.valueOf(minute));
            secText.setText(String.valueOf(second));
            millisecText.setText(String.valueOf(milli));
            intervalText.setText(String.valueOf(interval));

            //Register listener and display sensor value
            if (serviceBound) {
                loggingService.sensorManager.unregisterListener(this);
                previousTime = uptimeMillis();
                loggingService.sensorManager.registerListener(this,
                        loggingService.accelerometer, interval*1000);
                handler.postDelayed(runnable, 10);
            }
            handler.postDelayed(batteryCheck, 10);
        }

        private void stop() {
            handler.removeCallbacks(runnable);
            handler.removeCallbacks(batteryCheck);
            if (serviceBound) {loggingService.sensorManager.unregisterListener(this);}
        }

        @Override
        public void onAccuracyChanged(Sensor arg0, int arg1) {
            if (arg1 != 3) {
                Toast.makeText(MainActivity.this,
                        "Accuracy changed to " + String.valueOf(arg1), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            long currentTime = uptimeMillis();
            pastX[index]=event.values[0];
            pastY[index]=event.values[1];
            pastZ[index]=event.values[2];
            pastFreq[index]= currentTime - previousTime;

            index = (index+1)%100;
            previousTime=currentTime;
        }

        private float average (long[] array) {
            long sum = 0;
            for(long num : array) {sum = sum + num;}

            return ((float) sum)/array.length;
        }

        private float average (float[] array) {
            float sum = 0;
            for(float num : array) {sum = sum + num;}

            return sum/array.length;
        }
    }

    private boolean isIgnoringBatteryOptimizations(){
        if (Build.VERSION.SDK_INT >= 23) {
            if (!((PowerManager) getSystemService(Context.POWER_SERVICE))
                    .isIgnoringBatteryOptimizations("me.connectedspace.accelerometer")){
                Toast.makeText(MainActivity.this,
                        "Please disable battery optimization to allow app running in the background.", Toast.LENGTH_LONG)
                        .show();
                return false;
            }
        }
        return true;
    }
}
