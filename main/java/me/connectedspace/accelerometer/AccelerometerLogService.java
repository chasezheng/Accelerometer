package me.connectedspace.accelerometer;

import android.app.PendingIntent;
import android.app.Service;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
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

import static android.support.v4.app.NotificationCompat.CATEGORY_SERVICE;
import static android.support.v4.app.NotificationCompat.PRIORITY_MAX;

public class AccelerometerLogService extends Service {

    private boolean mIsServiceStarted = false;
    private Context mContext = getApplicationContext();
    private SensorManager mSensorManager = null;
    private Sensor mSensor;
    private File mLogFile = null;
    private FileOutputStream mFileStream = null;
    private AccelerometerLogService mReference = null;
    private Float[] mValues = null;
    private long mTimeStamp = 0;
    private ExecutorService mExecutor = null;

    @Override
    public void onCreate() {
        super.onCreate();

        Toast.makeText(getApplication(), "Service onCreate", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (isServiceStarted() == false) {

            mContext = getApplication();
            mReference = this;
            mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mValues = new Float[]{0f, 0f, 0f};

            mTimeStamp = System.currentTimeMillis();
            mExecutor = Executors.newSingleThreadExecutor();

            setupFolderAndFile();
            startLogging();
        }

        makePersistentNotification();

        //set started to true
        mIsServiceStarted = true;

        return Service.START_STICKY;
    }

    private void setupFolderAndFile() {
        mLogFile = new File(Environment.getExternalStorageDirectory().toString()
                + "/" + AppConstants.APP_LOG_FOLDER_NAME + "/test.txt");

        try {
            mFileStream = new FileOutputStream(mLogFile, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void startLogging() {

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mSensorManager.registerListener(
                        new SensorEventListener() {
                            @Override
                            public void onSensorChanged(SensorEvent sensorEvent) {
                                mTimeStamp = System.currentTimeMillis();
                                mValues[0] = sensorEvent.values[0];
                                mValues[1] = sensorEvent.values[1];
                                mValues[2] = sensorEvent.values[2];

                                String formatted = String.valueOf(mTimeStamp)
                                        + "\t" + String.valueOf(mValues[0])
                                        + "\t" + String.valueOf(mValues[1])
                                        + "\t" + String.valueOf(mValues[2])
                                        + "\r\n";

                                try {
                                    mFileStream.write(formatted.getBytes());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onAccuracyChanged(Sensor sensor, int i) {

                            }
                        }, mSensor, SensorManager.SENSOR_DELAY_FASTEST
                );
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        //Flush and close file stream
        if (mFileStream != null) {
            try {
                mFileStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                mFileStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Toast.makeText(mContext, "Service onDestroy", Toast.LENGTH_LONG).show();
        mIsServiceStarted = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Indicates if service is already started or not
     *
     *
     */
    public boolean isServiceStarted()
        {return mIsServiceStarted;
    }

    private void makePersistentNotification() {
        Intent mIntent = new Intent(mContext, MainActivity.class);
        PendingIntent mPendingIntent = PendingIntent.getActivity(mContext, 01, intent, Intent.FLAG_ACTIVITY_CLEAR_TASK);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setContentTitle("Accelerometer")
                        .setContentText("Currently running.")
                        .setContentIntent(mpendingIntent)
                        .setPriority(PRIORITY_MAX)
                        .setCategory(CATEGORY_SERVICE);
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.notify(1, mBuilder.build());
    }
}

