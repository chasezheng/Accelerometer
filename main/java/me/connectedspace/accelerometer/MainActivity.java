package me.connectedspace.accelerometer;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.widget.TextView;
import android.support.v7.widget.AppCompatButton;
import android.widget.Spinner;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    TextView currentTime = (TextView) findViewById (R.id.currentTime);
    TextView currentAccel = (TextView) findViewById (R.id.currentAccel);
    Context mContext = getApplicationContext();
    Intent serviceIntent = new Intent(mContext, Service.class);
    PendingIntent servicePendingIntent = PendingIntent.getActivity(mContext, 1, serviceIntent);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        currentTime.setText(String.valueOf(System.currentTimeMillis()));

        SensorManager sensorManager;
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor
                (Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        return true;
        }
    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub

        }
    @Override
    public void onSensorChanged(SensorEvent event) {

        }
    }

}


