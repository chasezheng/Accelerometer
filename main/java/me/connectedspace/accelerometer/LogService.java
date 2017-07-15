package me.connectedspace.accelerometer;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static android.os.SystemClock.elapsedRealtime;
import static android.os.SystemClock.uptimeMillis;
import static android.support.v4.app.NotificationCompat.CATEGORY_SERVICE;
import static android.support.v4.app.NotificationCompat.PRIORITY_MAX;
import static java.lang.Math.max;
import static java.lang.System.currentTimeMillis;

public class LogService extends Service {
    private Handler handler;
    SensorManager sensorManager;
    Sensor accelerometer;
    private NotificationManager nManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private static final String TAG = "serviceLog";

    @Override
    public void onCreate() {
        super.onCreate();
        configuration = new Configuration();
        handler = new Handler(getMainLooper());
        logger = new Logger();

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        configuration.makeNotification(false, "Sensor range:\n" + String.valueOf(accelerometer.getMaximumRange()));
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
        LogService getService() {return LogService.this;}
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
            LogService.this.stopSelf();
        }
        return true;
    }

    //logging behaviors
    Configuration configuration;
    private Logger logger;
    private LoggingSession loggingSession;

    final class Configuration {
        private Calendar calendar = Calendar.getInstance();
        private SimpleDateFormat fileTimeFormat = new SimpleDateFormat("E MMM dd HH:mm:ss.SSS zzz yyyy", Locale.US);
        private SimpleDateFormat timeStampFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        private SimpleDateFormat fileNameFormat = new SimpleDateFormat("HH-mm-ss MM" +
                "M-dd", Locale.US);
        private File dir = new File(Environment.getExternalStorageDirectory(), "Accelerometer");
        private long sleepTime; //updated by checkIfSlept
        private long bootTime;
        private int interval = 10;
        private boolean scheduled;
        private String macAddr;

        private Configuration() {
            dir.mkdir();
            bootTime = (currentTimeMillis() - elapsedRealtime()
                    - elapsedRealtime() + currentTimeMillis()) / 2; //There can be a delay between calling one time and then the other.
            sleepTime = (elapsedRealtime() - uptimeMillis()
                    - uptimeMillis() + elapsedRealtime()) / 2;
            WifiManager wifiMan = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo wifiInf = wifiMan.getConnectionInfo();
            macAddr = wifiInf.getMacAddress().replaceAll(":", "");
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
            try {
                wakeLock.release();
            } catch (Exception e){
                //do nothing
            }
        }

        void set(int hour, int minute, int second, int milli, int inter) {
            Log.v(TAG, "configuration set");
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Accelerometer running.");
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
            return calendar.getTimeInMillis();
        }

        private void checkIfSlept() {
            long newSleepTime = (elapsedRealtime() - uptimeMillis()
                    - uptimeMillis() + elapsedRealtime()) / 2;
            long delta = newSleepTime - sleepTime;
            Log.v(TAG, "checkIfSlept " + String.valueOf(delta));
            configuration.sleepTime = newSleepTime;
            if (scheduled && delta > 3) {
                String logMsg = "#Device slept for " + String.valueOf(delta)
                        + "ms. Is battery optimization turned off for the app?";
                makeNotification(false, logMsg);
            }

        }

        private void makeNotification(boolean onGoing, String text) {
            Log.v(TAG, "makeNotification");
            Intent intent = new Intent(getApplicationContext(), MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
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
            logFile = new File(configuration.dir, configuration.fileNameFormat.format(date) + " "
                    + configuration.macAddr + ".txt");
            try {
                logFile.createNewFile();
                fileStream = new FileOutputStream(logFile, true);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // add header to file
            String header = "# Started @" + configuration.fileTimeFormat.format(date) + "\r\n";
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
            }, scheduledTime - configuration.bootTime - configuration.sleepTime - 1000); //start 1000 ms early

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
                    + " " + configuration.timeStampFormat.format(new Date(currentTime + configuration.bootTime))
                    + " " + logMsg +"\r\n";
            try {
                loggingSession.fileStream.write(data.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
            logMsg = "";
        }
    }
}
