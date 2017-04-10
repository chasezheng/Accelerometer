package me.connectedspace.accelerometer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.support.v7.widget.SwitchCompat;

import me.connectedspace.accelerometer.AccelerometerLogService.accelBinder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    Context appContext;
    private boolean serviceBound;
    private AccelerometerLogService loggingService;
    private Intent bindIntent;
    private TextView currentTime, currentAccel, averageInter;
    private EditText hourText, minuteText, secText, millisecText, interval;
    private SwitchCompat switch1;
    private Handler handler;
    private Runnable viewUpdate;
    Calendar serviceCalendar;

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");
        appContext = getApplicationContext();
        setContentView(R.layout.activity_main);
        currentTime = (TextView) findViewById (R.id.currentTime);
        currentAccel = (TextView) findViewById (R.id.currentAccel);
        averageInter = (TextView) findViewById(R.id.averageInterval);
        hourText = (EditText) findViewById(R.id.hourText);
        minuteText = (EditText) findViewById(R.id.minuteText);
        secText = (EditText) findViewById(R.id.secText);
        millisecText = (EditText) findViewById(R.id.millisecText);
        interval = (EditText) findViewById(R.id.interval);
        switch1 = (SwitchCompat) findViewById(R.id.switch1);
        handler = new Handler(getMainLooper());

        ActivityCompat.requestPermissions(MainActivity.this,
                new String[] {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                100);

        viewUpdate = new Runnable() {
            @Override
            public void run() {
                currentTime.setText(dateFormatter.format(new Date(System.currentTimeMillis())));
                if (serviceBound) {
                    currentAccel.setText(String.valueOf(average(loggingService.pastX))
                            + "\n" + String.valueOf(average(loggingService.pastY))
                            + "\n" + String.valueOf(average(loggingService.pastZ)));
                    averageInter.setText(String.valueOf(average(loggingService.pastFreq)));
                    handler.postDelayed(this, 100);
                } else {
                    currentAccel.setText("Service not started.");
                }
            }
        };

        switch1.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (serviceBound) {
                            if (isChecked && !serviceCalendar.isSet(Calendar.MILLISECOND)) {
                                loggingService.setSchedule(Integer.parseInt(hourText.getText().toString()),
                                        Integer.parseInt(minuteText.getText().toString()),
                                        Integer.parseInt(secText.getText().toString()),
                                        Integer.parseInt(millisecText.getText().toString()),
                                        Integer.parseInt(interval.getText().toString()));
                            } else if (!isChecked && serviceCalendar.isSet(Calendar.MILLISECOND)) {
                                loggingService.cancelLogging();
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
        bindIntent = new Intent(this, AccelerometerLogService.class);
        startService(bindIntent);
        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");

        //Update view
        Calendar calendar = Calendar.getInstance();
        if (!serviceBound || !serviceCalendar.isSet(Calendar.MILLISECOND)) {
            hourText.setText(String.valueOf(calendar.get(Calendar.HOUR_OF_DAY)), TextView.BufferType.EDITABLE);
            minuteText.setText(String.valueOf(calendar.get(Calendar.MINUTE)), TextView.BufferType.EDITABLE);
            secText.setText(String.valueOf(calendar.get(Calendar.SECOND)), TextView.BufferType.EDITABLE);
            millisecText.setText(String.valueOf(0), TextView.BufferType.EDITABLE);
            interval.setText(String.valueOf(0), TextView.BufferType.EDITABLE);
        }

        handler.postDelayed(viewUpdate, 10);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");
        handler.removeCallbacks(viewUpdate);
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
            bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler = null;
        Log.v(TAG, "onDestroy");
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(TAG, "serviceDisconnected");
            serviceBound = false;
            loggingService = null;
            handler.removeCallbacks(viewUpdate);
        }
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "service connected");
            accelBinder binder = (accelBinder) service;
            loggingService = binder.getService();
            serviceBound = true;

            //One-time view update
            serviceCalendar = loggingService.calendar;
            if (serviceCalendar.isSet(Calendar.MILLISECOND)) {
                hourText.setText(String.valueOf(serviceCalendar.get(Calendar.HOUR_OF_DAY)), TextView.BufferType.EDITABLE);
                minuteText.setText(String.valueOf(serviceCalendar.get(Calendar.MINUTE)), TextView.BufferType.EDITABLE);
                secText.setText(String.valueOf(serviceCalendar.get(Calendar.SECOND)), TextView.BufferType.EDITABLE);
                millisecText.setText(String.valueOf(serviceCalendar.get(Calendar.MILLISECOND)), TextView.BufferType.EDITABLE);
                interval.setText(String.valueOf(loggingService.getSampleInterval()));
            }

            //Repeated view update
            handler.removeCallbacks(viewUpdate);
            handler.postDelayed(viewUpdate, 10);
        }
    };

    private long average (long[] array) {
        long sum = 0;
        for(long num : array) {sum = sum + num;}

        return sum/array.length;
    }

    private float average (float[] array) {
        float sum = 0;
        for(float num : array) {sum = sum + num;}

        return sum/array.length;
    }
}




