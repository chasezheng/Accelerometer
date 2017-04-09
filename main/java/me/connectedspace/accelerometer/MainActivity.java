package me.connectedspace.accelerometer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.view.View;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

import me.connectedspace.accelerometer.AccelerometerLogService.accelBinder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    Context appContext;
    private boolean serviceBound = false;
    private AccelerometerLogService loggingService;
    private Intent bindIntent;
    private TextView currentTime;
    private TextView currentAccel;
    private TextView averageInter;
    private EditText hourText;
    private EditText minuteText;
    private EditText secText;
    private EditText millisecText;
    private AppCompatButton button1;
    private Handler handler;
    private Calendar calendar = Calendar.getInstance();

    private Runnable viewUpdate;

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
        button1 = (AppCompatButton) findViewById(R.id.button1);
        handler = new Handler(getMainLooper());

        button1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //invoking service method
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");
        //create logging service
        bindIntent = new Intent(this, AccelerometerLogService.class);
        startService(bindIntent);
        if (!bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            Log.v(TAG, "service not started");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
        hourText.setText(String.valueOf(calendar.get(Calendar.HOUR_OF_DAY)), TextView.BufferType.EDITABLE);
        minuteText.setText(String.valueOf(calendar.get(Calendar.MINUTE)), TextView.BufferType.EDITABLE);
        secText.setText(String.valueOf(calendar.get(Calendar.SECOND)), TextView.BufferType.EDITABLE);
        millisecText.setText(String.valueOf(0), TextView.BufferType.EDITABLE);
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
        Log.v(TAG, "beginning onRestart");
        if (!loggingService.serviceStarted()) {
            startService(bindIntent);
            bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else if (!serviceBound) {
            bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
        Log.v(TAG, "finishing onRestart");
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
            serviceBound = false;
            handler.removeCallbacks(viewUpdate);
        }
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "service connected");
            accelBinder binder = (accelBinder) service;
            loggingService = binder.getService();
            serviceBound = true;

            handler.removeCallbacks(viewUpdate);
            viewUpdate = new Runnable() {
                @Override
                public void run() {
                    currentTime.setText(dateFormatter.format(new Date(System.currentTimeMillis())));
                    currentAccel.setText(String.valueOf(average(loggingService.pastX))
                            + "\n" + String.valueOf(average(loggingService.pastY))
                            + "\n" + String.valueOf(average(loggingService.pastZ)));
                    averageInter.setText(String.valueOf(average(loggingService.pastFreq)));
                    handler.postDelayed(this, 100);
                }
            };
            handler.postDelayed(viewUpdate, 10);
        }
    };

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

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




