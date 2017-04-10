package me.connectedspace.accelerometer;

import android.app.PendingIntent;
import android.app.Service;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
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
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

import static android.support.v4.app.NotificationCompat.CATEGORY_SERVICE;
import static android.support.v4.app.NotificationCompat.PRIORITY_MAX;
import static java.lang.Math.max;

public class AccelerometerLogService extends Service implements SensorEventListener {
    Context appContext;
    private Runnable autoTimeout;
    private Handler handler;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private NotificationManager nManager;
    private File logFile;
    private FileOutputStream fileStream;
    private String logMsg = ""; //Message to be included in logfile when defined.
                                // comment sign needs to be prefixed
    SimpleDateFormat fileTimeFormat;
    public float[] pastX = new float[100];
    public float[] pastY = new float[100];
    public float[] pastZ = new float[100];
    public long[] pastFreq = new long[100];
    private int sampleInterval; //in millisecond
    private int index;
    private long previousTime;
    Calendar calendar = Calendar.getInstance();

    private static final String TAG = "serviceLog";

    //service life cycle
    @Override
    public void onCreate() {
        super.onCreate();
        appContext=getApplicationContext();
        fileTimeFormat = new SimpleDateFormat("E MMM dd HH:mm:ss.S zzz yyyy", Locale.US);
        calendar.clear(Calendar.HOUR_OF_DAY);
        calendar.clear(Calendar.MINUTE);
        calendar.clear(Calendar.SECOND);
        calendar.clear(Calendar.MILLISECOND);
        nManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Log.v(TAG, "onCreate");
        Toast.makeText(appContext, "Service created", Toast.LENGTH_SHORT).show();

        handler = new Handler(getMainLooper());
        autoTimeout = new Runnable() {
            @Override
            public void run() {AccelerometerLogService.this.onDestroy();}
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartComment");
        handler.postDelayed(autoTimeout, 3*60*1000);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        Log.v(TAG, "onDestroy");

        //Add file footer

        //Flush and close file stream
        if (fileStream != null) {
            try {
                String footer = "# Completed @"
                        + fileTimeFormat.format(new Date(System.currentTimeMillis()));
                fileStream.write(footer.getBytes());
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

        //Cancel notification
        nManager.cancelAll();
        Toast.makeText(appContext, "Service destroyed", Toast.LENGTH_LONG).show();
    }

    //setup binding behaviors
    class accelBinder extends Binder {
        AccelerometerLogService getService() {return AccelerometerLogService.this;}
    }
    private final IBinder binder = new accelBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        handler.removeCallbacks(autoTimeout);
        sensorManager.registerListener(this, accelerometer, sampleInterval*1000);
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(TAG, "onUnbind");
        if (!calendar.isSet(Calendar.MILLISECOND)) {
            sensorManager.unregisterListener(this);
            handler.postDelayed(autoTimeout, 3*60*1000);
        }
        return false;
    }

    //setup logging behaviors
    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        Log.v(TAG, "onAccuracyChanged");
        Toast.makeText(appContext, "Accuracy changed to " + String.valueOf(arg1), Toast.LENGTH_LONG).show();

        //log accuracy change
        logMsg = "#Accuracy changed to " + String.valueOf(arg1);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long currentTime = System.currentTimeMillis();
        pastX[index]=event.values[0];
        pastY[index]=event.values[1];
        pastZ[index]=event.values[2];
        pastFreq[index]= currentTime - previousTime;

        // logging if past scheduled time
        if (calendar.isSet(Calendar.MILLISECOND) && fileStream != null
                && System.currentTimeMillis() >= calendar.getTimeInMillis()) {
            String data = String.valueOf(pastX[index])
                    + " " + String.valueOf(pastY[index])
                    + " " + String.valueOf(pastZ[index])
                    + " " + String.valueOf(currentTime - calendar.getTimeInMillis())
                    + " " + logMsg +"\n";
            try {
                fileStream.write(data.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        logMsg = "";
        index = (index+1)%100;
        previousTime=currentTime;
    }

    private void makePersistentNotification() {
        Log.v(TAG, "makePersistentNotification");
        Intent intent = new Intent(appContext, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(appContext, 0,
                intent, 0);
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setContentTitle("Accelerometer")
                        .setContentText("Currently running.")
                        .setContentIntent(pendingIntent)
                        .setPriority(PRIORITY_MAX)
                        .setSmallIcon(R.drawable.notification)
                        .setCategory(CATEGORY_SERVICE)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setWhen(calendar.getTimeInMillis());
        nManager.notify(1, builder.build());
    }

    private void setupFolderAndFile(long time) {
        Log.v(TAG, "setupFolderAndFile");

        File dir = new File(Environment.getExternalStorageDirectory(), "Accelerometer");
        dir.mkdir();

        logFile = new File(dir,
                new SimpleDateFormat("MMM dd HH mm ss S", Locale.US).format(new Date(time)) + ".txt");
        boolean fileCreated = false;

        try {fileCreated = logFile.createNewFile();}
        catch (IOException e) {
            e.printStackTrace();
        }

        if (fileCreated) {
            try {
                fileStream = new FileOutputStream(logFile, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        //Add header to logfile
        String header = "# Started @" + fileTimeFormat.format(new Date(time)) + "\n";

        try {
            fileStream.write(header.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSchedule(int hour, int minute, int second, int milli, int interval) {
        sensorManager.unregisterListener(this);
        sensorManager.registerListener(this, accelerometer, interval*1000);

        long currentTime = System.currentTimeMillis();
        calendar.set(calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DATE), hour, minute, second);
        long time = max(calendar.getTimeInMillis() + milli, currentTime);
        setupFolderAndFile(time);
        previousTime = time;

        calendar.setTimeInMillis(time);
        calendar.set(Calendar.MILLISECOND, (int) (time % 1000));
        sampleInterval = interval;
        makePersistentNotification();
    }

    public void cancelLogging() {
        //Unset calendar to stop logging
        calendar.clear(Calendar.MILLISECOND);

        //Flush and close file stream
        if (fileStream != null) {
            try {
                String footer = "# Completed @"
                        + fileTimeFormat.format(new Date(System.currentTimeMillis()));
                fileStream.write(footer.getBytes());
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
        if (System.currentTimeMillis() <= calendar.getTimeInMillis() + 1000) {
            logFile.delete();
        }

        //Cancel notification
        nManager.cancelAll();
    }

    public int getSampleInterval() {return sampleInterval;}
}
