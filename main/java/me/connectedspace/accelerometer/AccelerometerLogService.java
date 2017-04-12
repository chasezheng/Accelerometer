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
import android.os.PowerManager;
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

import static android.os.SystemClock.uptimeMillis;
import static android.support.v4.app.NotificationCompat.CATEGORY_SERVICE;
import static android.support.v4.app.NotificationCompat.PRIORITY_MAX;
import static java.lang.Math.max;
import static java.lang.System.currentTimeMillis;

public class AccelerometerLogService extends Service {
    Context appContext;
    private Handler handler;
    SensorManager sensorManager;
    Sensor accelerometer;
    private NotificationManager nManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private Logger logger;
    private LoggingSession loggingSession = null;
    private File dir;
    Configuration configuration;

    private static final String TAG = "serviceLog";

    //service life cycle
    @Override
    public void onCreate() {
        super.onCreate();
        appContext=getApplicationContext();
        configuration = new Configuration();
        handler = new Handler(getMainLooper());
        logger = new Logger();
        dir = new File(Environment.getExternalStorageDirectory(), "Accelerometer");
        dir.mkdir();

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Accelerometer running.");
        //// TODO: 4/10/2017 is ignoring battery optimization?
        nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Log.v(TAG, "onCreate");
        Toast.makeText(appContext, "Service created", Toast.LENGTH_SHORT).show();
        configuration.clear();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartComment");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        if (loggingSession != null) {loggingSession.close();}
    }

    //setup binding behaviors
    class accelBinder extends Binder {
        AccelerometerLogService getService() {return AccelerometerLogService.this;}
    }
    private final IBinder binder = new accelBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        return binder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.v(TAG, "onRebind");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(TAG, "onUnbind");
        if (!configuration.scheduled) {
            AccelerometerLogService.this.stopSelf();
        }
        return true;
    }


    final class Configuration {
        private Calendar calendar;
        private SimpleDateFormat fileTimeFormat;
        private SimpleDateFormat fileNameFormat;
        private int interval;
        private boolean scheduled;

        private Configuration() {
            calendar = Calendar.getInstance();
            fileTimeFormat = new SimpleDateFormat("E MMM dd HH:mm:ss.S zzz yyyy", Locale.US);
            fileNameFormat = new SimpleDateFormat("MMM-dd HH-mm ss.S", Locale.US);
        }

        void clear() {
            scheduled = false;
            calendar.clear(Calendar.HOUR_OF_DAY);
            calendar.clear(Calendar.MINUTE);
            calendar.clear(Calendar.SECOND);
            calendar.clear(Calendar.MILLISECOND);
            if (loggingSession != null) {
                loggingSession.close();
            }
            nManager.cancelAll();
            if (wakeLock.isHeld()) {wakeLock.release();}
        }

        void set(int hour, int minute, int second, int milli, int inter) {
            Log.v(TAG, "configuration set");
            wakeLock.acquire();
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, second);
            calendar.set(Calendar.MILLISECOND, milli);
            interval = inter;
            scheduled = true;
            loggingSession = new LoggingSession();
        }

        void set(long time, int inter) {
            wakeLock.acquire();
            calendar.setTimeInMillis(time);
            interval = inter;
            scheduled = true;
            loggingSession = new LoggingSession();
        }

        int[] get() {
            if (!scheduled) {
                return new int[5];
            }
            return new int[] {calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE),
                    calendar.get(Calendar.SECOND), calendar.get(Calendar.MILLISECOND), interval};
        }

        boolean scheduled() {return scheduled;}

        long getTimeInMillis() {
            if (!scheduled) {
                return 0;
            }
            return calendar.getTimeInMillis();
        }

        private void setTimeInMillis(long time) {
            calendar.setTimeInMillis(time);
        }
    }

    private class LoggingSession {
        private File logFile;
        private FileOutputStream fileStream;

        private LoggingSession() {
            long timeDiff = currentTimeMillis() - uptimeMillis();

            //setup file
            long time = max(currentTimeMillis(), configuration.getTimeInMillis());
            logger.startTime = time - timeDiff;
            logFile = new File(dir, configuration.fileNameFormat.format(new Date(time)) + ".txt");
            boolean fileCreated = false;
            try {fileCreated = logFile.createNewFile();}
            catch (IOException e) {e.printStackTrace();}
            if (fileCreated) {
                try {fileStream = new FileOutputStream(logFile, true);}
                catch (FileNotFoundException e) {e.printStackTrace();}
            }
            // add header to file
            String header = "# Started @" + configuration.fileTimeFormat.format(new Date(time)) + "\n";
            try {
                fileStream.write(header.getBytes());
                logger.fileStream = fileStream;
            } catch (IOException e) {
                e.printStackTrace();
            }
            //Schedule logging
            handler.postAtTime(new Runnable() {
                @Override
                public void run() {
                    sensorManager.registerListener(logger, accelerometer, configuration.interval*1000);
                }
            }, time - timeDiff);

            Log.v(TAG, "Start logging session");
            makeNotification();
            configuration.setTimeInMillis(time);
        }

        private void makeNotification() {
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
                            .setWhen(configuration.getTimeInMillis());
            nManager.notify(1, builder.build());
        }

        private void close() {
            sensorManager.unregisterListener(logger);
            //Flush and close file stream
            if (fileStream != null) {
                try {
                    String footer = "# Completed @"
                            + configuration.fileTimeFormat.format(new Date(currentTimeMillis()));
                    fileStream.write(footer.getBytes());
                    fileStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (uptimeMillis() <= logger.startTime) {logFile.delete();}
            logger.fileStream = null;
            loggingSession = null;
        }
    } //Logging sessions are instantiated as a result of setting configurations.

    private class Logger implements SensorEventListener {
        private FileOutputStream fileStream;
        private String logMsg = "";
        private long startTime;

        @Override
        public void onAccuracyChanged(Sensor arg0, int arg1) {
            Toast.makeText(appContext, "Accuracy changed to " + String.valueOf(arg1), Toast.LENGTH_LONG).show();
            logMsg = "#Accuracy changed to " + String.valueOf(arg1);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            long currentTime = uptimeMillis();
            String data = String.valueOf(event.values[0])
                    + " " + String.valueOf(event.values[1])
                    + " " + String.valueOf(event.values[2])
                    + " " + String.valueOf(currentTime - startTime)
                    + " " + logMsg +"\n";
            try {
                fileStream.write(data.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
            logMsg = "";
        }
    }
}
