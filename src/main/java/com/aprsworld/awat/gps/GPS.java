package com.aprsworld.awat.gps;

import android.annotation.SuppressLint;
import android.app.Service;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Notification;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import android.hardware.*;
import android.location.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

import android.os.*;
import android.telephony.*;

@SuppressWarnings("AccessStaticViaInstance")
public class GPS extends Service {
    /* Location manager params */
    private LocationManager lm;
    private LocListen ll;
    private AlarmManager am;
    private PendingIntent amintent;
    private LocAlarm recvr;
    private PendingIntent tointent;
    private LocTimeout recvTimeout;
    private long timeout;

    /* Date formatting */
    private SimpleDateFormat dateformatter;

    /* Notification */
    private NotificationManager notman;
    private Notification notif;
    private PendingIntent notintent;

    /* RPC */
    private LocBinder binder;
    public LocIF rpc_if;
    private URL target_url;
    boolean update_now;

    /* Our position data from last read */
    Location location;

    /* Battery info */
    private BatteryState bat_rcvr;
    int bat_level;
    boolean charger;


    /* Other info */
    float bat_temp;
    long uptime;
    long freespace;

    private int signal;
    @SuppressWarnings("FieldCanBeLocal")
    private TelephonyManager tManager;
    @SuppressWarnings("FieldCanBeLocal")
    private SignalState signal_rcvr;

    private OrientationState orientation;

    /* Environment Sensors */
    private Map<String, SensorState> sensors;

    /* Prefs */
    private Preferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);
        System.out.println("GPS Service onCreate.");

        setupLocationListener();

        setupAlarms();

        setupBattery();

        setupSignal();

        // Setup sensors;
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensors = new HashMap<>();
        sensors.put("ambient_temp", new SensorState(sensorManager, Sensor.TYPE_AMBIENT_TEMPERATURE));
        sensors.put("ambient_light", new SensorState(sensorManager, Sensor.TYPE_LIGHT));
        sensors.put("pressure", new SensorState(sensorManager, Sensor.TYPE_PRESSURE));
        sensors.put("humidity", new SensorState(sensorManager, Sensor.TYPE_RELATIVE_HUMIDITY));

        /* Get prefs */
        this.prefs = new Preferences(this);
        this.prefs.load();
        reconfigure();

        /* Create formatter */
        dateformatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'", Locale.US);
        dateformatter.setTimeZone(new SimpleTimeZone(0, "UTC"));

        /* Create binder */
        this.binder = new LocBinder(this);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        System.out.println("GPS Service onDestroy.");

        /* Remove alarms and unhook locator */
        this.am.cancel(this.amintent);
        this.am.cancel(this.tointent);
        this.lm.removeUpdates(this.ll);
        unregisterReceiver(this.recvr);
        unregisterReceiver(this.bat_rcvr);
        unregisterReceiver(this.recvTimeout);
        if (orientation != null) {
            orientation.stop();
            orientation = null;
        }
        for (SensorState sensor : sensors.values()) {
            sensor.stop();
        }

        if (this.notman != null) {
            /* Remove notification */
            this.notman.cancelAll();
        }
    }


    @Override
    public IBinder onBind(Intent i) {
        return this.binder;
    }

    @Override
    public boolean onUnbind(Intent i) {
        this.rpc_if = null;
        return false;
    }


    private void startLocator()
            throws IllegalArgumentException {
        if (this.prefs.provider == 1)
            this.lm.requestLocationUpdates(this.lm.GPS_PROVIDER, 0, 0,
                    this.ll);
        else
            this.lm.requestLocationUpdates(this.lm.NETWORK_PROVIDER, 0, 0,
                    this.ll);

        // start sensors
        if (this.orientation != null) {
            this.orientation.start();
        }
        for (SensorState sensor : sensors.values()) {
            sensor.start();
        }
    }

    public void triggerUpdate() {
        if (this.prefs.continuous_mode) {
            /* If we are in continuous mode just send last location */
            locationUpdate();
        } else {
            /* Polling mode */

            this.lm.removeUpdates(this.ll);
            this.location = null;

            try {
                startLocator();
            } catch (IllegalArgumentException e) {
                System.out.println("AWAT: " + e.toString());
                return;
            }

            /* Start timeout alarm */
            long current = System.currentTimeMillis();
            if (current >= this.timeout || this.update_now) {
                this.timeout = System.currentTimeMillis();
                this.timeout += this.prefs.gps_timeout;
                this.timeout += 100; /* delay a bit to avoid a race */
                this.update_now = false;
            }
            if (Build.VERSION.SDK_INT >= 19) {
                this.am.setExact(this.am.RTC_WAKEUP, this.timeout, this.tointent);
            } else {
                this.am.set(this.am.RTC_WAKEUP, this.timeout, this.tointent);
            }
        }
    }

    private void setupLocationListener() {
        /* Set up the position listener */
        this.ll = new LocListen();
        this.lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    private void setupNotif() {
        /* Show state in notif bar */
        if (this.notman != null) {
            /* Already set up */
            return;
        }

        /* Set up a persistent notification */
        this.notman = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        this.notintent =
                PendingIntent.getActivity(this, 0,
                        new Intent(this, AWAT.class), 0);
        Notification.Builder builder = new Notification.Builder(this);
        this.notif = builder.setContentIntent(this.notintent)
                .setSmallIcon(R.drawable.notif_icon)
                .setTicker(getString(R.string.app_name))
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setContentTitle("GPS Location")
                .setContentText("Waiting...").build();
        this.notman.notify(1, notif);
        this.startForeground(1, this.notif);
    }

    private void setupAlarms() {
        /* Prepare alarm manager */
        this.am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        /* Setup location update alarm */
        this.recvr = new LocAlarm();
        registerReceiver(this.recvr,
                new IntentFilter(LocAlarm.class.toString()),
                null, null);
        Intent i = new Intent(LocAlarm.class.toString());
        this.amintent = PendingIntent.getBroadcast(this, 0, i, 0);

        /* Setup timeout alarm */
        this.recvTimeout = new LocTimeout();
        registerReceiver(this.recvTimeout,
                new IntentFilter(LocTimeout.class.toString()),
                null, null);
        i = new Intent(LocTimeout.class.toString());
        this.tointent = PendingIntent.getBroadcast(this, 0, i, 0);

    }

    private void setupBattery() {
        /* Setup location update alarm */
        this.bat_rcvr = new BatteryState();
        registerReceiver(this.bat_rcvr,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                null, null);
    }

    private void setupSignal() {
        this.tManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        this.signal_rcvr = new SignalState();
        this.tManager.listen(this.signal_rcvr, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    private void setupAlarm() {
        long current = System.currentTimeMillis();
        long timeout = this.prefs.update_interval;
        long next = current + timeout - current % timeout;
        if (this.prefs.improve_accuracy && !this.prefs.continuous_mode) {
            this.timeout = next;
            next -= this.prefs.gps_timeout;
            if (next <= current) {
                next = current + timeout * 2 - current % timeout;
                if (this.timeout <= current) {
                    this.timeout = next;
                }
                next -= this.prefs.gps_timeout;
            }
        }

        if (Build.VERSION.SDK_INT >= 19) {
            this.am.setExact(this.am.RTC_WAKEUP, next, this.amintent);
        } else {
            this.am.set(this.am.RTC_WAKEUP, next, this.amintent);
        }
    }

    private void doTimeout() {
        if (System.currentTimeMillis() >= this.timeout) {
            System.out.println("AWAT: Doing timeout");

            if (this.prefs.improve_accuracy || this.location == null) {
                locationUpdate();
            }

            // stop sensors
            this.lm.removeUpdates(this.ll);
            if (this.orientation != null) {
                this.orientation.stop();
            }
            for (SensorState sensor : sensors.values()) {
                sensor.stop();
            }
        }
    }


    public void reloadPrefs() {
        this.prefs.load();
        reconfigure();
    }

    private void reconfigure() {
        System.out.println("AWAT doing reconfig");
        /* Update the request times */
        this.lm.removeUpdates(ll);

        /* Reset update alarms */
        this.setupAlarm();

        /* Fix notifs */
        setupNotif();

        /* Start/stop sensors */
        if (prefs.send_orientation) {
            if (orientation == null) {
                orientation = new OrientationState();
            }
        } else {
            orientation.stop();
            orientation = null;
        }
        for (Map.Entry<String, SensorState> entry : sensors.entrySet()) {
            entry.getValue().enable(this.prefs.prefs.getBoolean("send_" + entry.getKey(), false));
        }

        /* Set URL */
        this.target_url = null;
        try {
            this.target_url = new URL(this.prefs.target_url);
        } catch (MalformedURLException e) {
            System.out.println("AWAT: " + e.toString());
            this.target_url = null;
        }

        /* For continuous mode, start updating */
        if (this.prefs.continuous_mode) {
            try {
                startLocator();
            } catch (IllegalArgumentException e) {
                System.out.println("AWAT: "
                        + "Can't start locator in continuous mode: "
                        + e.toString());
                //noinspection UnnecessaryReturnStatement
                return;
            }
        }
    }

    /* Send a request to the URL and post some data */
    @SuppressLint("HardwareIds")
    private void postLocation() {
        boolean do_notif = false;

        /* No url, don't do anything */
        if (this.target_url == null)
            return;

        System.out.println("AWAT sending HTTP poke");

        /* Prepare connection and request */
        HttpURLConnection con;
        try {
            con = (HttpURLConnection) this.target_url.openConnection();
        } catch (IOException e) {
            System.out.println("AWAT: " + e.toString());
            if (this.rpc_if != null)
                this.rpc_if.onError(e.toString());
            return;
        }

        try {
            con.setRequestMethod("POST");
        } catch (ProtocolException e) {
            System.out.println("AWAT: " + e.toString());
            if (this.rpc_if != null)
                this.rpc_if.onError(e.toString());
            return;
        }

        con.setUseCaches(false);
        con.setDoOutput(true);
        con.setDoInput(true);

        /* If HTTP response is to be used in notif bar */
        if (this.prefs.http_resp_in_notif_bar) {
            this.setupNotif();
            do_notif = true;
        }

        /* Build request data */
        StringBuilder req = new StringBuilder();
        req.append("update=1");

        if (this.prefs.send_build_date) {
            req.append("&build_date=");
            req.append(BuildConfig.BUILD_TIME);
        }
        if (this.prefs.send_build_version) {
            req.append("&build_version=");
            try {
                req.append(URLEncoder.encode(BuildConfig.VERSION_NAME, "utf-8"));
            } catch (UnsupportedEncodingException ignored) { }
        }
        if (this.prefs.send_build_versioncode) {
            req.append("&build_versioncode=");
            req.append(BuildConfig.VERSION_CODE);
        }
        if (this.prefs.send_build_type) {
            req.append("&build_type=");
            try {
                req.append(URLEncoder.encode(BuildConfig.BUILD_TYPE, "utf-8"));
            } catch (UnsupportedEncodingException ignored) { }
        }
        if (this.prefs.send_build_git_hash) {
            req.append("&build_git_hash=");
            req.append(BuildConfig.BUILD_GIT_HASH);
        }
        if (this.prefs.send_build_git_clean) {
            req.append("&build_git_clean=");
            req.append(BuildConfig.BUILD_GIT_CLEAN);
        }

        if (this.location != null) {
            req.append("&latitude=");
            req.append(this.location.getLatitude());

            req.append("&longitude=");
            req.append(this.location.getLongitude());

            req.append("&accuracy=");
            req.append(this.location.getAccuracy());

            if (this.prefs.send_altitude) {
                req.append("&altitude=");
                req.append(this.location.getAltitude());
            }

            if (this.prefs.send_provider) {
                req.append("&provider=");
                req.append(this.location.getProvider());
            }

            if (this.prefs.send_bearing) {
                req.append("&bearing=");
                req.append(this.location.getBearing());
            }

            if (this.prefs.send_speed) {
                req.append("&speed=");
                req.append(this.location.getSpeed());
            }

            if (this.prefs.send_time) {
                Date date = new Date(this.location.getTime());
                req.append("&time=");
                req.append(this.dateformatter.format(date));
            }

            if (this.prefs.send_extras) {
                Bundle extras = this.location.getExtras();
                for (String key : extras.keySet()) {
                    //noinspection CatchMayIgnoreException
                    try {
                        Object value_obj;
                        String value;
                        req.append("&gnss_");
                        req.append(URLEncoder.encode(key, "utf-8"));
                        req.append("=");
                        value_obj = extras.get(key);
                        if (value_obj != null) {
                            value = value_obj.toString();
                        } else {
                            value = "";
                        }
                        req.append(URLEncoder.encode(value, "utf-8"));
                    } catch (Exception e) {
                    }
                }
            }
        }

        if (this.prefs.send_systime) {
            req.append("&systime=");
            req.append(this.dateformatter.format(new Date()));
        }

        /* Add orientation status if configured */
        if (this.prefs.send_orientation) {
            req.append("&orientation=");
            req.append(Arrays.toString(orientation.getCurrentValue()));
        }

        /* Add battery status if configured */
        if (this.prefs.send_batt_status) {
            req.append("&battlevel=");
            req.append(this.bat_level);
            if (this.charger) {
                req.append("&charging=1");
            } else {
                req.append("&charging=0");
            }
        }

        /* Add secret if configured */
        if (this.prefs.secret != null) {
            req.append("&secret=");
            req.append(this.prefs.secret);
        }

        /* Add device id */
        if (this.prefs.send_devid) {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (tm != null) {
                req.append("&deviceid=");
                req.append(tm.getDeviceId());
            }
        }

        /* Add subscriber id */
        if (this.prefs.send_subscrid) {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (tm != null) {
                req.append("&subscriberid=");
                req.append(tm.getSubscriberId());
            }
        }

        /* Add temperature */
        if (this.prefs.send_batt_temp) {
            req.append("&temperatureBatteryC=");
            req.append(this.bat_temp);
        }

        /* Add System Uptime */
        if (this.prefs.send_uptime) {
            req.append("&uptimeMilliseconds=");
            req.append(this.uptime);
        }

        /* Add Free Space */
        if (this.prefs.send_freespace) {
            req.append("&freespaceInternalMegabytes=");
            req.append(this.freespace);
        }

        /* Add Signal Strength */
        if (this.prefs.send_signal) {
            req.append("&signalStrengthLevel=");
            req.append(this.signal);
        }

        /* Add Screen State */
        if (this.prefs.send_screen) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                req.append("&screen=");
                if (Build.VERSION.SDK_INT >= 21) {
                    req.append(pm.isInteractive());
                } else {
                    req.append(pm.isScreenOn());
                }
            }
        }

        /* Sensors */
        for (Map.Entry<String, SensorState> entry : sensors.entrySet()) {
            String name = entry.getKey();
            SensorState sensor = entry.getValue();
            if (this.prefs.prefs.getBoolean("send_" + name, false)) {
                req.append("&");
                req.append(name);
                req.append("=");
                req.append(sensor.value);
            }
        }

        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setRequestProperty("Content-Length", "" + req.length());

        /* Connect and write */
        StringBuilder response = new StringBuilder();
        try {
            con.connect();

            DataOutputStream wr;
            wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(req.toString());
            wr.flush();
            wr.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
            if (do_notif) {
                response.append(br.readLine());
            }
            br.close();
        } catch (IOException e) {
            System.out.println("AWAT: " + e.toString());
            if (this.rpc_if != null)
                this.rpc_if.onError(e.toString());
            return;
        }
        con.disconnect();

        System.out.println("AWAT sent HTTP poke");

        /* Set notification if we have it */
        if (this.notif != null && do_notif) {
            Notification.Builder builder = new Notification.Builder(this);
            this.notif = builder.setContentIntent(this.notintent)
                    .setSmallIcon(R.drawable.notif_icon)
                    .setTicker(getString(R.string.app_name))
                    .setWhen(System.currentTimeMillis())
                    .setOngoing(true)
                    .setContentTitle("POST Response")
                    .setContentText(response.toString()).build();
            this.notman.notify(1, this.notif);
        }

        this.location = null;
    }

    private void locationUpdate() {
        /* Location update triggered */
        Location loc = this.location;
        //if (loc == null)
        //	return;

        /* Update extra info */
        this.uptime = SystemClock.uptimeMillis();
        StatFs stats = new StatFs((Environment.getDataDirectory().getAbsolutePath()));
        if (Build.VERSION.SDK_INT >= 18) {
            this.freespace = stats.getAvailableBlocksLong() * stats.getBlockSizeLong() / 1024 / 1024;
        } else {
            this.freespace = stats.getAvailableBlocks() * stats.getBlockSize() / 1024 / 1024;
        }

        /* Change notification */
        if (loc != null &&
                !this.prefs.http_resp_in_notif_bar) {
            this.setupNotif();
            String txt = loc.getLatitude() + ", "
                    + loc.getLongitude() + ", "
                    + (int) loc.getAccuracy() + "m";
            Notification.Builder builder = new Notification.Builder(GPS.this);
            this.notif = builder.setContentIntent(GPS.this.notintent)
                    .setSmallIcon(R.drawable.notif_icon)
                    .setTicker(getString(R.string.app_name))
                    .setWhen(System.currentTimeMillis())
                    .setOngoing(true)
                    .setContentTitle("GPS Location")
                    .setContentText(txt).build();
            this.notman.notify(1, GPS.this.notif);
        }

        /* Call to UI */
        if (this.rpc_if != null && loc != null) { // XXX Change onLocation
            this.rpc_if.onLocation(loc.getProvider(), loc,
                    this.bat_level,
                    this.charger,
                    this.bat_temp,
                    this.uptime,
                    this.freespace);
        }

        /* Post to server */
        this.postLocation();
    }

    /**************************************************************************
     * Helper classes for timed locator interaction
     *************************************************************************/
    class LocAlarm extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent i) {
            System.out.println("AWAT: Alarm!");
            GPS.this.setupAlarm();
            GPS.this.triggerUpdate();
        }
    }

    class LocTimeout extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent i) {
            if (GPS.this.prefs.continuous_mode) {
                System.out.println("AWAT: Ignored timeout");
            } else {
                System.out.println("AWAT: Received Timeout!");
                GPS.this.doTimeout();
            }
        }
    }

    class BatteryState extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent i) {
            float level;
            level = i.getIntExtra("level", 0);
            level /= i.getIntExtra("scale", 100);
            GPS.this.bat_level = (int) (level * 100);
            GPS.this.charger = i.getIntExtra("plugged", 1) != 0;
            System.out.printf("AWAT: Battery state change: %d%% %b\n",
                    GPS.this.bat_level, GPS.this.charger);

            // DAR
            GPS.this.bat_temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -999) / 10.0f;
            System.out.printf("AWAT: Battery temp: %f%%\n", GPS.this.bat_temp);
            // !DAR
        }
    }

    class SignalState extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength strength) {
            super.onSignalStrengthsChanged(strength);
            if (Build.VERSION.SDK_INT >= 23) {
                GPS.this.signal = strength.getLevel();
            } else {
                GPS.this.signal = -1;
            }
        }
    }

    static class SensorState implements SensorEventListener {
        private boolean enabled, updating;
        private final SensorManager sensorManager;
        private final int sensorType;
        private Sensor sensor;
        float value;

        SensorState (SensorManager manager, int type) {
            sensorManager = manager;
            sensorType = type;
            sensor = sensorManager.getDefaultSensor(sensorType);
        }

        boolean isInvalid() {
            return (sensor == null);
        }

        void enable(boolean enable) {
            if (enable && !enabled) {
                if (sensor == null)
                    sensor = sensorManager.getDefaultSensor(sensorType);
                enabled = true;
            } else if (!enable && enabled) {
                stop();
                enabled = false;
            }
        }

        @SuppressWarnings("UnusedReturnValue")
        boolean start() {
            // already updating?
            if (updating) {
                return true;
            }

            // reset state
            value = Float.NaN;

            // enabled?
            if (!enabled)
                return false;

            // valid sensor?
            if (isInvalid()) {
                return false;
            }

            // listen for changes
            updating = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            return updating;
        }

        void stop() {
            // actually updating?
            if (!updating) {
                return;
            }

            // valid sensor?
            if (isInvalid()) {
                return;
            }

            // stop listening for changes
            sensorManager.unregisterListener(this);
            updating = false;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            value = event.values[0];
        }

        @Override
        public void onAccuracyChanged (Sensor sensor, int accuracy) {}
    }

    class OrientationState implements SensorEventListener {
        private SensorManager sensorManager;
        private Sensor accelerometer, magneticField;
        private final float[] accelerometerReading = new float[3];
        private final float[] magnetometerReading = new float[3];
        private final float[] rotationMatrix = new float[9];
        private final float[] orientationAngles = new float[3];

        @SuppressWarnings("UnusedReturnValue")
        boolean start() {
            if (sensorManager == null) {
                sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                if (sensorManager == null) {
                    return false;
                }
            }

            if (accelerometer == null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (accelerometer != null) {
                    sensorManager.registerListener(this, accelerometer, 60000000);
                }
            }
            if (magneticField == null) {
                magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                if (magneticField != null) {
                    sensorManager.registerListener(this, magneticField, 60000000);
                }
            }

            if (magneticField == null || accelerometer == null) {
                stop();
                return false;
            }

            return true;
        }

        void stop () {
            if (sensorManager == null) {
                return;
            }
            sensorManager.unregisterListener(this);
            magneticField = accelerometer = null;
            Arrays.fill(accelerometerReading, 0.f);
            Arrays.fill(magnetometerReading, 0.f);
            Arrays.fill(rotationMatrix, 0.f);
            Arrays.fill(orientationAngles, 0.f);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, accelerometerReading,
                        0, accelerometerReading.length);
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetometerReading,
                        0, magnetometerReading.length);
            }
        }

        @Override
        public void onAccuracyChanged (Sensor sensor, int accuracy) {
        }

        float [] getCurrentValue() {
            SensorManager.getRotationMatrix(rotationMatrix, null,
                    accelerometerReading, magnetometerReading);

            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            return orientationAngles;
        }
    }


    class LocListen implements LocationListener {
        @Override
        public void onProviderDisabled(String prov) {
            System.out.println("AWAT ProviderDisabled: " + prov);
            if (GPS.this.prefs.continuous_mode)
                GPS.this.startLocator();
            else {
                /* Stop waiting for timeout since this locator is off */
                GPS.this.timeout = System.currentTimeMillis() - 1;
                GPS.this.doTimeout();
            }
        }

        @Override
        public void onProviderEnabled(String prov) {
            System.out.println("AWAT ProviderEnabled: " + prov);
            if (GPS.this.prefs.continuous_mode)
                GPS.this.startLocator();
            else
                GPS.this.doTimeout();
        }

        @Override
        public void onStatusChanged(String prov, int stat,
                                    Bundle xtra) {
            System.out.println("AWAT Status change: "
                    + prov + " -> " + stat);
            if (GPS.this.rpc_if != null)
                GPS.this.rpc_if.onStateChange(prov, stat);

            if (GPS.this.prefs.continuous_mode)
                GPS.this.startLocator();
            else
                GPS.this.doTimeout();
        }

        @Override
        public void onLocationChanged(Location loc) {
            System.out.println("AWAT got loc from "
                    + loc.getProvider() + " (~" + loc.getAccuracy() + "m)");
            GPS.this.location = loc;

            if (!GPS.this.prefs.continuous_mode && !GPS.this.prefs.improve_accuracy) {
                /* Stop waiting for locations. Will be restarted by alarm */
                GPS.this.lm.removeUpdates(GPS.this.ll);
                GPS.this.am.cancel(GPS.this.tointent);

                GPS.this.locationUpdate();

                // Stop sensors
                if (GPS.this.orientation != null) {
                    GPS.this.orientation.stop();
                }
                for (SensorState sensor : GPS.this.sensors.values()) {
                    sensor.stop();
                }
            }
        }
    }
}
