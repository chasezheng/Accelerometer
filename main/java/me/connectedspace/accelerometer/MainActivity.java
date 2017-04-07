package me.connectedspace.accelerometer;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Spinner;
import static android.content.Intent.FILL_IN_ACTION;
import me.connectedspace.accelerometer.AccelerometerLogService.accelBinder;

public class MainActivity extends AppCompatActivity {
    Context appContext = getApplicationContext();
    TextView currentTime = (TextView) findViewById (R.id.currentTime);
    TextView currentAccel = (TextView) findViewById (R.id.currentAccel);
    Spinner hourSpinner = (Spinner) findViewById(R.id.hourSpinner);
    Spinner minuteSpinner = (Spinner) findViewById(R.id.minuteSpinner);
    EditText millisecText = (EditText) findViewById(R.id.millisecText);
    AppCompatButton button1 = (AppCompatButton) findViewById(R.id.button1);
    AccelerometerLogService loggingService;
    private Intent bindIntent = new Intent(this, AccelerometerLogService.class);
    boolean serviceBound = false;

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            accelBinder binder = (accelBinder) service;
            loggingService = binder.getService();
            serviceBound = true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //create user interface
        currentTime.setText(String.valueOf(System.currentTimeMillis()));
        ArrayAdapter<CharSequence> hoursAdapter = ArrayAdapter.createFromResource(this,
                R.array.hours_array, android.R.layout.simple_spinner_item);
        hoursAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hourSpinner.setAdapter(hoursAdapter);
        ArrayAdapter<CharSequence> minutesAdapter = ArrayAdapter.createFromResource(this,
                R.array.minutes_array, android.R.layout.simple_spinner_item);
        minutesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        minuteSpinner.setAdapter(minutesAdapter);

        //create logging service
        startService(bindIntent);
        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        button1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //add data to bindIntent
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (!loggingService.serviceStarted) {
            startService(bindIntent);
            bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else if (!serviceBound) {
            bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}




