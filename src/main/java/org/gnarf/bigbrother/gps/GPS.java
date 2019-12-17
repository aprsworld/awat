package org.gnarf.bigbrother.gps;

import java.util.*;

import android.os.StrictMode;
import android.app.Service;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Notification;

import android.os.Bundle;
import android.os.IBinder;
import android.os.BatteryManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import android.location.*;
import android.os.SystemClock;
import android.telephony.TelephonyManager;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

import org.gnarf.bigbrother.gps.*;

// DAR
import android.os.*;
import android.telephony.*;
// !DAR

public class GPS extends Service {
    /* Location manager params */
    LocationManager lm;
    LocListen ll;
    AlarmManager am;
    PendingIntent amintent;
    LocAlarm recvr;
    PendingIntent tointent;
    LocTimeout recvTimeout;
    boolean twiceTimeout;
    long timeout;

    /* Date formatting */
    SimpleDateFormat dateformatter;

    /* Notification */
    NotificationManager notman;
    Notification notif;
    PendingIntent notintent;

    /* RPC */
    LocBinder binder;
    public LocIF rpc_if;
    URL target_url;

    /* Our position data from last read */
    Location location;

    /* Battery info */
    BatteryState bat_rcvr;
    int bat_level;
    boolean charger;


    /* DAR Other info */
    float bat_temp;
    long uptime;
    long freespace;

    int signal;
    TelephonyManager tManager;
    SignalState signal_rcvr;
    // !DAR

    /* Prefs */
    Preferences prefs;

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

        /* Get prefs */
        this.prefs = new Preferences(this);
        this.prefs.load();
        reconfigure();

        /* Create formatter */
        dateformatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'");
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
    }

    public void triggerUpdate() {
        if (this.prefs.continous_mode) {
            /* If we are in continous mode just send last location */
            locationUpdate();
        } else {
            /* Polling mode */

            this.lm.removeUpdates(this.ll);
            this.location = null;

            try {
                startLocator();
            } catch (IllegalArgumentException e) {
                System.out.println("BigBrotherGPS: " + e.toString());
                return;
            }

            /* Start timeout alarm */
            this.timeout = System.currentTimeMillis();
            this.timeout += this.prefs.gps_timeout;
            this.timeout += 100; /* delay a bit to avoid a race */
            this.am.setRepeating(this.am.RTC_WAKEUP, this.timeout,
                    this.prefs.gps_timeout, this.tointent);
            if (this.prefs.provider == 1)
                this.twiceTimeout = true;
            else
                this.twiceTimeout = false;
        }
    }

    private void setupLocationListener() {
        /* Set up the position listener */
        this.ll = new LocListen();
        this.lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    private void setupNotif() {
        if (this.prefs.show_in_notif_bar) {
            /* Show state in notif bar */

            if (this.notman != null) {
                /* Already set up */
                return;
            }

            /* Set up a persistent notification */
            this.notman = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            this.notif = new Notification(R.drawable.notif_icon,
                    "BigBrother GPS Waiting for location",
                    System.currentTimeMillis());
            this.notif.flags = notif.FLAG_ONGOING_EVENT;
            this.notintent =
                    PendingIntent.getActivity(this, 0,
                            new Intent(this, BigBrotherGPS.class), 0);
            this.notif.setLatestEventInfo(this, getString(R.string.app_name),
                    "Waiting for initial location",
                    notintent);
            this.notman.notify(1, notif);
            if (this.prefs.continous_mode || this.prefs.improve_accuracy) {
                this.startForeground(1, this.notif);
            }
        } else {
            if (this.notman != null) {
                /* Remove notification */
                this.stopForeground(true);
                this.notman.cancelAll();
                this.notman = null;
            }
        }
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

    private void setupSignal()
    {
        this.tManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        this.signal_rcvr = new SignalState();
        this.tManager.listen(this.signal_rcvr, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    public void doTimeout()
    {
		if (System.currentTimeMillis() > this.timeout) {
			System.out.println("BigBrotherGPS: Doing timeout");
			if (this.prefs.improve_accuracy) {
				locationUpdate();
				this.lm.removeUpdates(this.ll);
				this.am.cancel(this.tointent);
				return;
			}
			if (this.twiceTimeout) {
				System.out.println("BigBrotherGPS: Switching locator");
				this.twiceTimeout = false;
				this.timeout =
					System.currentTimeMillis() + this.prefs.gps_timeout;
				try {
					this.lm.requestLocationUpdates(this.lm.NETWORK_PROVIDER,
												   0, 0, this.ll);
				}
				catch (IllegalArgumentException e) {
					System.out.println("BigBrotherGPS(timeout): "
									   +e.toString());
					return;
				}
			} else {
				/* Timeout reached */
				this.lm.removeUpdates(this.ll);
				this.am.cancel(this.tointent);
			}
		}
    }


    public void reloadPrefs()
    {
		this.prefs.load();
		reconfigure();
    }

    private void reconfigure()
    {
		System.out.println("BigBrotherGPS doing reconfig");
		/* Update the request times */
		this.lm.removeUpdates(ll);

		/* Reset update alarms */
		long current = System.currentTimeMillis();
		long timeout = this.prefs.update_interval;
		long next = current + timeout - current % timeout;
		if (Build.VERSION.SDK_INT >= 19) {
			this.am.setExact(this.am.RTC_WAKEUP, next, this.amintent);
		} else {
			this.am.set(this.am.RTC_WAKEUP, next, this.amintent);
		}

		/* Fix notifs */
		setupNotif();

		/* Set URL */
		this.target_url = null;
		try {
			this.target_url = new URL(this.prefs.target_url);
		}
		catch (MalformedURLException e) {
			System.out.println("BigBrotherGPS: "+e.toString());
			this.target_url = null;
		}

		/* For continous mode, start updating */
		if (this.prefs.continous_mode) {
			try {
				startLocator();
			}
			catch (IllegalArgumentException e) {
				System.out.println("BigBrotherGPS: "
								   +"Can't start locator in continous mode: "
								   +e.toString());
				return;
			}
		}
    }

    /* Send a request to the URL and post some data */
    protected void postLocation()
    {
		boolean do_notif = false;

		/* No url, don't do anything */
		if (this.target_url == null)
			return;

		System.out.println("BigBrotherGPS sending HTTP poke");

		/* Prepare connection and request */
		HttpURLConnection con;
		try {
			con = (HttpURLConnection)this.target_url.openConnection();
		}
		catch (IOException e) {
			System.out.println("BigBrotherGPS: "+e.toString());
			if (this.rpc_if != null)
				this.rpc_if.onError(e.toString());
			return;
		}

		try {
			con.setRequestMethod("POST");
		}
		catch (ProtocolException e) {
			System.out.println("BigBrotherGPS: "+e.toString());
			if (this.rpc_if != null)
				this.rpc_if.onError(e.toString());
			return;
		}

		con.setUseCaches(false);
		con.setDoOutput(true);
		con.setDoInput(true);

		/* If HTTP response is to be used in notif bar */
		if (this.prefs.show_in_notif_bar &&
			this.prefs.http_resp_in_notif_bar) {
			this.setupNotif();
			do_notif = true;
		}

		/* Build request data */
		StringBuffer req = new StringBuffer();
		req.append("update=1");

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
					try {
						req.append("&gnss_");
						req.append(URLEncoder.encode(key, "utf-8"));
						req.append("=");
						req.append(URLEncoder.encode(extras.get(key).toString(), "utf-8"));
					} catch (UnsupportedEncodingException e) {
					}
				}
			}
		}

		if (this.prefs.send_systime) {
			req.append("&systime=");
			req.append(this.dateformatter.format(new Date()));
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
			TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
			req.append("&deviceid=");
			req.append(tm.getDeviceId());
		}

		/* Add subscriber id */
		if (this.prefs.send_subscrid) {
			TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
			req.append("&subscriberid=");
			req.append(tm.getSubscriberId());
		}

		// DAR
		/* Add temperature */
		if (this.prefs.send_temp) {
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
		// !DAR

		con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		con.setRequestProperty("Content-Length", ""+req.length());

		/* Connect and write */
		StringBuffer response = new StringBuffer();
		try {
			con.connect();

			DataOutputStream wr;
			wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(req.toString());
			wr.flush();
			wr.close();

			DataInputStream rd;
			rd = new DataInputStream(con.getInputStream());
			if (do_notif) {
				response.append(rd.readLine());
			}
			rd.close();
		}
		catch (IOException e) {
			System.out.println("BigBrotherGPS: "+e.toString());
			if (this.rpc_if != null)
				this.rpc_if.onError(e.toString());
			return;
		}
		con.disconnect();

		System.out.println("BigBrotherGPS sent HTTP poke");

		/* Set notification if we have it */
		if (this.notif != null && do_notif) {
			this.notif.when = System.currentTimeMillis();
			this.notif.setLatestEventInfo(this,
										  getString(R.string.app_name),
										  response.toString(),
										  this.notintent);
			this.notman.notify(1, this.notif);
		}

		this.location = null;
    }

    private void locationUpdate()
    {
		/* Location update triggered */
		Location loc = this.location;
		//if (loc == null)
		//	return;

		/* Update extra info */
		this.uptime = SystemClock.uptimeMillis();
		StatFs stats =  new StatFs((Environment.getDataDirectory().getAbsolutePath()));
		if (Build.VERSION.SDK_INT >= 18) {
            this.freespace = stats.getAvailableBlocksLong() * stats.getBlockSizeLong() / 1024 / 1024;
        } else {
            this.freespace = stats.getAvailableBlocks() * stats.getBlockSize() / 1024 / 1024;
        }

		/* Change notification */
		if (loc != null && this.prefs.show_in_notif_bar &&
			!this.prefs.http_resp_in_notif_bar) {
			this.setupNotif();
			String txt = loc.getLatitude()+", "
				+loc.getLongitude()+", "
				+(int)loc.getAccuracy()+"m";
			this.notif.when = System.currentTimeMillis();
			this.notif.setLatestEventInfo(GPS.this,
										  getString(R.string.app_name),
										  txt, GPS.this.notintent);
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
    class LocAlarm extends BroadcastReceiver
    {
		@Override public void onReceive(Context ctx, Intent i)
		{
			System.out.println("BigBrotherGPS: Alarm!");
			GPS.this.triggerUpdate();

			// setup next alarm
			long current = System.currentTimeMillis();
			long timeout = GPS.this.prefs.update_interval;
			long next = current + timeout - current % timeout;
			if (Build.VERSION.SDK_INT >= 19) {
				GPS.this.am.setExact(GPS.this.am.RTC_WAKEUP, next, GPS.this.amintent);
			} else {
				GPS.this.am.set(GPS.this.am.RTC_WAKEUP, next, GPS.this.amintent);
			}
		}
    }

    class LocTimeout extends BroadcastReceiver
    {
		@Override public void onReceive(Context ctx, Intent i)
		{
			if (GPS.this.prefs.continous_mode) {
				System.out.println("BigBrotherGPS: Ignored timeout");
			} else {
				System.out.println("BigBrotherGPS: Received Timeout!");
				GPS.this.doTimeout();
			}
		}
    }

    class BatteryState extends BroadcastReceiver
    {
		@Override public void onReceive(Context ctx, Intent i)
		{
			float level;
			level = i.getIntExtra("level", 0);
			level /= i.getIntExtra("scale", 100);
			GPS.this.bat_level = (int)(level*100);
			GPS.this.charger = i.getIntExtra("plugged",1)!=0;
			System.out.printf("BigBrotherGPS: Battery state change: %d%% %b\n",
							  GPS.this.bat_level, GPS.this.charger);

			// DAR
			GPS.this.bat_temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -999)/10.0f;
			System.out.printf("BigBrotherGPS: Battery temp: %f%%\n", GPS.this.bat_temp);
			// !DAR
		}
    }

    class SignalState extends PhoneStateListener {
        @Override public void onSignalStrengthsChanged(SignalStrength strength) {
            super.onSignalStrengthsChanged(strength);
            GPS.this.signal = strength.getGsmSignalStrength();
        }
    }


    class LocListen implements LocationListener
    {
		@Override public void onProviderDisabled(String prov)
		{
			System.out.println("BigBrotherGPS ProviderDisabled: "+prov);
			if (GPS.this.prefs.continous_mode)
				GPS.this.startLocator();
			else {
				/* Stop waiting for timeout since this locator is off */
				GPS.this.timeout = System.currentTimeMillis()-1;
				GPS.this.doTimeout();
			}
		}

		@Override public void onProviderEnabled(String prov)
		{
			System.out.println("BigBrotherGPS ProviderEnabled: "+prov);
			if (GPS.this.prefs.continous_mode)
				GPS.this.startLocator();
			else
				GPS.this.doTimeout();
		}

		@Override public void onStatusChanged(String prov, int stat,
											  Bundle xtra)
		{
			System.out.println("BigBrotherGPS Status change: "
							   +prov+" -> "+stat);
			if (GPS.this.rpc_if != null)
				GPS.this.rpc_if.onStateChange(prov, stat);

			if (GPS.this.prefs.continous_mode)
				GPS.this.startLocator();
			else
				GPS.this.doTimeout();
		}

		@Override public void onLocationChanged(Location loc)
		{
			System.out.println("BigBrotherGPS got loc from "
							   +loc.getProvider());
			GPS.this.location = loc;

			if (!GPS.this.prefs.continous_mode && !GPS.this.prefs.improve_accuracy) {
				/* Stop waiting for locations. Will be restarted by alarm */
				GPS.this.lm.removeUpdates(GPS.this.ll);
				GPS.this.am.cancel(GPS.this.tointent);

				GPS.this.locationUpdate();
			}
		}
    }
}
