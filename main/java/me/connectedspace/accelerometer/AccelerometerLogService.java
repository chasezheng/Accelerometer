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

import static android.os.SystemClock.elapsedRealtime;
import static android.os.SystemClock.sleep;
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
    private File dir;
    private static final String TAG = "serviceLog";

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
        configuration.clear();
    }

    //binding behaviors
    private final IBinder binder = new accelBinder();

    class accelBinder extends Binder {
        AccelerometerLogService getService() {return AccelerometerLogService.this;}
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        configuration.checkIfSlept();
        return binder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.v(TAG, "onRebind");
        configuration.checkIfSlept();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(TAG, "onUnbind");
        if (!configuration.scheduled) {
            AccelerometerLogService.this.stopSelf();
        }
        return true;
    }

    //logging behaviors
    Configuration configuration;
    private Logger logger;
    private LoggingSession loggingSession = null;

    final class Configuration {
        private Calendar calendar;
        private SimpleDateFormat fileTimeFormat;
        private SimpleDateFormat fileNameFormat;
        private long sleepTime; //updated by checkIfSlept
        private long bootTime;
        private int interval;
        private boolean scheduled;

        private Configuration() {
            calendar = Calendar.getInstance();
            fileTimeFormat = new SimpleDateFormat("E MMM dd HH:mm:ss.S zzz yyyy", Locale.US);
            fileNameFormat = new SimpleDateFormat("MMM-dd HH-mm ss.S", Locale.US);
            bootTime = (currentTimeMillis() - elapsedRealtime()
                    - elapsedRealtime() + currentTimeMillis()) / 2; //There can be a delay between calling one time and then the other.
        }

        void clear() {
            checkIfSlept();
            scheduled = false;
            calendar.clear(Calendar.HOUR_OF_DAY);
            calendar.clear(Calendar.MINUTE);
            calendar.clear(Calendar.SECOND);
            calendar.clear(Calendar.MILLISECOND);
            if (loggingSession != null) {
                loggingSession.close();
                loggingSession = null;
            }
            nManager.cancel(1);
            if (wakeLock.isHeld()) {wakeLock.release();}
        }

        void set(int hour, int minute, int second, int milli, int inter) {
            Log.v(TAG, "configuration set");
            wakeLock.acquire();
            scheduled = true;
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, second);
            calendar.set(Calendar.MILLISECOND, milli);
            calendar.setTimeInMillis(max(currentTimeMillis(), configuration.getTimeInMillis()));
            interval = inter;
            loggingSession = new LoggingSession(configuration.getTimeInMillis());
            makeNotification(true, "Currently running.");
            sleepTime = (elapsedRealtime() - uptimeMillis()
                    - uptimeMillis() + elapsedRealtime()) / 2;
        }

        void set(long time, int inter) {
            wakeLock.acquire();
            scheduled = true;
            calendar.setTimeInMillis(time);
            interval = inter;
            calendar.setTimeInMillis(max(currentTimeMillis(), configuration.getTimeInMillis()));
            loggingSession = new LoggingSession(configuration.getTimeInMillis());
            makeNotification(true, "Currently running.");
            sleepTime = (elapsedRealtime() - uptimeMillis()
                    - uptimeMillis() + elapsedRealtime()) / 2;
        }

        int[] get() {
            if (!scheduled) {
                return new int[5];
            }
            return new int[] {calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE),
                    calendar.get(Calendar.SECOND), calendar.get(Calendar.MILLISECOND), interval};
        }

        private int get(int field) {return calendar.get(field);}

        boolean scheduled() {return scheduled;}

        long getTimeInMillis() {
            if (!scheduled) {
                return 0;
            }
            return calendar.getTimeInMillis();
        }

        private void checkIfSlept() {
            long newSleepTime = (elapsedRealtime() - uptimeMillis()
                    - uptimeMillis() + elapsedRealtime()) / 2;
            long delta = newSleepTime - sleepTime;
            Log.v(TAG, "checkIfSlept " + String.valueOf(delta));
            configuration.sleepTime = newSleepTime;
            if (scheduled && delta > 0) {
                String logMsg = "#Device slept for " + String.valueOf(delta)
                        + "ms. Is battery optimization turned off for the app?";
                makeNotification(false, logMsg);
            }

        }

        private void makeNotification(boolean onGoing, String text) {
            Log.v(TAG, "makeNotification");
            Intent intent = new Intent(appContext, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(appContext, 0,
                    intent, 0);
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(getApplicationContext())
                            .setContentTitle("Accelerometer")
                            .setContentText(text)
                            .setContentIntent(pendingIntent)
                            .setPriority(PRIORITY_MAX)
                            .setSmallIcon(R.drawable.notification)
                            .setCategory(CATEGORY_SERVICE)
                            .setOngoing(onGoing)
                            .setAutoCancel(!onGoing)
                            .setWhen(configuration.getTimeInMillis());
            nManager.notify(onGoing ? 1 : 0, builder.build());
        }

        public long timeToElapsedTime(long time) {
            return time - bootTime;
        }
    }

    private class LoggingSession {
        private File logFile;
        private FileOutputStream fileStream;

        private LoggingSession(long scheduledTime) {
            //scheduledTime is in milliseconds since the epoch
            //setup file
            Date date = new Date(scheduledTime);
            logFile = new File(dir, configuration.fileNameFormat.format(date) + ".txt");
            boolean fileCreated = false;
            try {fileCreated = logFile.createNewFile();}
            catch (IOException e) {e.printStackTrace();}
            if (fileCreated) {
                try {fileStream = new FileOutputStream(logFile, true);}
                catch (FileNotFoundException e) {e.printStackTrace();}
            }
            // add header to file
            String header = "# Started @" + configuration.fileTimeFormat.format(date) + "\n";
            try {
                fileStream.write(header.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
            //Schedule logging
            logger.startTime = configuration.timeToElapsedTime(scheduledTime);
            handler.postAtTime(new Runnable() {
                @Override
                public void run() {
                    sensorManager.registerListener(logger, accelerometer, configuration.interval*1000);
                }
            }, scheduledTime - configuration.bootTime - configuration.sleepTime);

            Log.v(TAG, "Start logging session");
        }

        private void close() {
            sensorManager.unregisterListener(logger);
            //Close the file stream
            long time = currentTimeMillis();
            if (fileStream != null) {
                try {
                    String footer = "# Completed @"
                            + configuration.fileTimeFormat.format(new Date(time));
                    fileStream.write(footer.getBytes());
                    fileStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (elapsedRealtime() <= logger.startTime) {logFile.delete();}
        }
    } //Logging sessions are instantiated by setting configurations.

    private class Logger implements SensorEventListener {
        private String logMsg = "";
        private long startTime;

        @Override
        public void onAccuracyChanged(Sensor arg0, int arg1) {
            logMsg = "#Accuracy changed to " + String.valueOf(arg1);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            long currentTime = elapsedRealtime();
            String data = String.valueOf(event.values[0])
                    + " " + String.valueOf(event.values[1])
                    + " " + String.valueOf(event.values[2])
                    + " " + String.valueOf(currentTime - startTime)
                    + " " + logMsg +"\n";
            try {
                loggingSession.fileStream.write(data.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
            logMsg = "";
        }
    }
}
