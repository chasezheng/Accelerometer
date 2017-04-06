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
import android.widget.EditText;
import android.widget.TextView;
import android.support.v7.widget.AppCompatButton;
import android.widget.Spinner;
import android.view.View;
import android.widget.ArrayAdapter;

import static android.content.Intent.FILL_IN_ACTION;

public class MainActivity extends AppCompatActivity {

    Context appContext = getApplicationContext();

    TextView currentTime = (TextView) findViewById (R.id.currentTime);
    TextView currentAccel = (TextView) findViewById (R.id.currentAccel);
    Spinner hourSpinner = (Spinner) findViewById(R.id.hourSpinner);
    Spinner minuteSpinner = (Spinner) findViewById(R.id.minuteSpinner);
    EditText millisecText = (EditText) findViewById(R.id.millisecText);
    AppCompatButton button1 = (AppCompatButton) findViewById(R.id.button1);

    Intent serviceIntent = new Intent(appContext, Service.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        currentTime.setText(String.valueOf(System.currentTimeMillis()));

        button1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
            }
        });

        }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // keeping running
        return true;
    }

}




