package me.connectedspace.accelerometer;

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
import android.app.Notification;
import android.app.NotificationManager;
import android.support.v4.app.NotificationCompat;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static me.connectedspace.accelerometer.R.mipmap.ic_launcher;

/**
 * Created by mzhen on 3/28/2017.
 */

public class AccelerometerLogService extends Service {

    private boolean mIsServiceStarted = false;
    private Context mContext = null;
    private SensorManager mSensorManager = null;
    private Sensor mSensor;
    private File mLogFile = null;
    private FileOutputStream mFileStream = null;
    private AccelerometerLogService mReference = null;
    private Float[] mValues = null;
    private long mTimeStamp = 0;
    private ExecutorService mExecutor = null;

    /**
     * Default empty constructor needed by Android OS
     */
    public AccelerometerLogService() {
        super();
    }
    /**
     * Constructor which takes context as argument
     *
     * @param context
     */
    public AccelerometerLogService(Context context) {
        super();

        if (context != null)
            mContext = context;
        else
            mContext = getBaseContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Toast.makeText(getBaseContext(), "Service onCreate", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (isServiceStarted() == false) {

            mContext = getBaseContext();
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
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(ic_launcher)
                        .setContentTitle("Accelerometer")
                        .setContentText("Currently running.");
    }
}

