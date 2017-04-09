package me.connectedspace.accelerometer;

import android.app.PendingIntent;
import android.app.Service;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.app.NotificationManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

import static android.content.Intent.FILL_IN_ACTION;
import static android.hardware.SensorManager.SENSOR_DELAY_FASTEST;
import static android.support.v4.app.NotificationCompat.CATEGORY_SERVICE;
import static android.support.v4.app.NotificationCompat.PRIORITY_MAX;

public class AccelerometerLogService extends Service implements SensorEventListener {
    private boolean serviceStarted = false;
    Context appContext;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long scheduledTime = 2*System.currentTimeMillis(); //default value is forever away
    private FileOutputStream fileStream;
    private PendingIntent servicePendingIntent;
    private static final String TAG = "serviceLog";
    public float[] pastX = new float[100];
    public float[] pastY = new float[100];
    public float[] pastZ = new float[100];
    public long[] pastFreq = new long[100];
    private int index = 0;
    private long previousTime;

    //service life cycle
    public boolean serviceStarted() {return serviceStarted;}

    @Override
    public void onCreate() {
        super.onCreate();
        appContext=getApplicationContext();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Log.v(TAG, "onCreate");
        Toast.makeText(appContext, "Service onCreate", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartComment");
        serviceStarted = true;
        sensorManager.registerListener(this, sensorManager.getDefaultSensor
                (Sensor.TYPE_ACCELEROMETER), SENSOR_DELAY_FASTEST);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        Log.v(TAG, "onDestroy");

        //Flush and close file stream
        if (fileStream != null) {
            try {
                fileStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                fileStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Toast.makeText(appContext, "Service onDestroy", Toast.LENGTH_LONG).show();
        serviceStarted = false;
    }

    //setup binding behaviors
    class accelBinder extends Binder {
        AccelerometerLogService getService() {return AccelerometerLogService.this;}
    }
    private final IBinder binder = new accelBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        servicePendingIntent = PendingIntent.getActivity(appContext, 1, intent, FILL_IN_ACTION);
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(TAG, "onUnbind");
        return true;
    }

    //setup logging behaviors
    SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-dd HH.mm.ss", Locale.US);
    public void scheduleTime(long time) {
        makePersistentNotification(servicePendingIntent);
        setupFolderAndFile(time);
        scheduledTime = time;
        previousTime = time;
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        Log.v(TAG, "onAccuracyChanged");
        // report and adjust accuracy!
    }

    @Override
    public void onSensorChanged(@NonNull SensorEvent event) {
        long currentTime = System.currentTimeMillis();
        pastX[index]=event.values[0];
        pastY[index]=event.values[1];
        pastZ[index]=event.values[2];
        pastFreq[index]= currentTime - previousTime;

        // logging if past scheduled time
        if (System.currentTimeMillis() >= scheduledTime) {
            String formatted = String.valueOf(pastX[index])
                    + " " + String.valueOf(pastY[index])
                    + " " + String.valueOf(pastZ[index])
                    + " " + String.valueOf(currentTime - scheduledTime) + "\n";
            try {
                fileStream.write(formatted.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        index = (index+1)%100;
        previousTime=currentTime;
    }

    private void makePersistentNotification(PendingIntent pendingIntent) {
        Log.v(TAG, "makePersistentNotification");
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setContentTitle("Accelerometer")
                        .setContentText("Currently running.")
                        .setContentIntent(pendingIntent)
                        .setPriority(PRIORITY_MAX)
                        .setCategory(CATEGORY_SERVICE);
        NotificationManager notifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notifyMgr.notify(1, builder.build());
    }

    private void setupFolderAndFile(long time) {
        Log.v(TAG, "setupFolderAndFile");
        File logFile;
        logFile = new File(Environment.getExternalStorageDirectory().toString()
                + "/accelerometer/" + dateFormatter.format(new Date(time)) + ".txt");

        try {
            fileStream = new FileOutputStream(logFile, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
