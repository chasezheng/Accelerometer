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
import android.widget.Toast;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.app.NotificationManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.content.Intent.FILL_IN_ACTION;
import static android.support.v4.app.NotificationCompat.CATEGORY_SERVICE;
import static android.support.v4.app.NotificationCompat.PRIORITY_MAX;

public class AccelerometerLogService extends Service implements SensorEventListener {
    boolean serviceStarted = false;
    Context appContext = getApplicationContext();
    SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    float[] acceleration = {0f, 0f, 0f};
    long timeStamp;
    private File logFile;
    private FileOutputStream fileStream;
    private ExecutorService executor;
    class accelBinder extends Binder {
        AccelerometerLogService getService() {
            return AccelerometerLogService.this;
        }
    }
    private IBinder binder = new accelBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        Toast.makeText(appContext, "Service onCreate", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent serviceIntent, int flags, int startId) {

        if (!serviceStarted) {

            timeStamp = System.currentTimeMillis();
            executor = Executors.newSingleThreadExecutor();

            setupFolderAndFile();
            startLogging();
        }

        sensorManager.registerListener(this, sensorManager.getDefaultSensor
                (Sensor.TYPE_ACCELEROMETER), 100000); // 100000 being the sampling interval in microseconds

        PendingIntent servicePendingIntent = PendingIntent.getActivity(appContext, 1, serviceIntent, FILL_IN_ACTION);
        makePersistentNotification(servicePendingIntent);

        //set started to true
        serviceStarted = true;

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

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

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // keeping running
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    private void makePersistentNotification(PendingIntent pendingIntent) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setContentTitle("Accelerometer")
                        .setContentText("Currently running.")
                        .setContentIntent(pendingIntent)
                        .setPriority(PRIORITY_MAX)
                        .setCategory(CATEGORY_SERVICE);
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.notify(1, mBuilder.build());
    }

    private void setupFolderAndFile() {
        logFile = new File(Environment.getExternalStorageDirectory().toString()
                + "/" + "accelerometer" + "/test.txt");

        try {
            fileStream = new FileOutputStream(logFile, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void startLogging() {

        executor.execute(new Runnable() {
            @Override
            public void run() {
                sensorManager.registerListener(
                        new SensorEventListener() {
                            @Override
                            public void onSensorChanged(SensorEvent sensorEvent) {
                                timeStamp = System.currentTimeMillis();
                                acceleration[0] = sensorEvent.values[0];
                                acceleration[1] = sensorEvent.values[1];
                                acceleration[2] = sensorEvent.values[2];

                                String formatted = String.valueOf(timeStamp)
                                        + "\t" + String.valueOf(acceleration[0])
                                        + "\t" + String.valueOf(acceleration[1])
                                        + "\t" + String.valueOf(acceleration[2])
                                        + "\r\n";

                                try {
                                    fileStream.write(formatted.getBytes());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onAccuracyChanged(Sensor accelerometer, int i) {

                            }
                        }, accelerometer, SensorManager.SENSOR_DELAY_FASTEST
                );
            }
        });
    }
}
