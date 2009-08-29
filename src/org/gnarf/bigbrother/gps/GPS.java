package org.gnarf.bigbrother.gps;

import java.util.*;

import android.app.Service;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Notification;

import android.os.Bundle;
import android.os.IBinder;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import android.location.*;

import org.gnarf.bigbrother.gps.*;

public class GPS extends Service
{
    /* Location manager params */
    LocationManager lm;
    LocListen ll;
    AlarmManager am;
    PendingIntent amintent;

    /* Notification */
    NotificationManager notman;
    Notification notif;
    PendingIntent notintent;

    /* RPC */
    LocBinder binder;
    public LocIF rpc_if;

    /* Our position data from last read */
    public double latitude;
    public double longitude;
    public float accuracy;

    /* Prefs */
    Preferences prefs;

    @Override public void onCreate()
    {
	super.onCreate();
	System.out.println("GPS Service onCreate.");

	/* Set up the position listener */
	ll = new LocListen();
	lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

	/* Set up a persistent notification */
	notman = (NotificationManager)
	    getSystemService(Context.NOTIFICATION_SERVICE);
	notif = new Notification(R.drawable.notif_icon,
				 "BigBrother GPS Waiting for location",
				 System.currentTimeMillis());
	notif.flags = notif.FLAG_ONGOING_EVENT;
	notintent = 
	    PendingIntent.getActivity(this, 0, 
				      new Intent(this, BigBrotherGPS.class),0);
	notif.setLatestEventInfo(this, getString(R.string.app_name),
				 "Waiting for initial location", notintent);
	notman.notify(0, notif);
	
	/* Prepare alarm manager */
	registerReceiver(new LocAlarm(), 
			 new IntentFilter(LocAlarm.class.toString()),
			 null, null);
	am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);	
	Intent i = new Intent(LocAlarm.class.toString());
	amintent = PendingIntent.getBroadcast(this, 0, i, 0);

	/* Get prefs */
	prefs = new Preferences(this);
	loadPrefs();

	binder = new LocBinder(this);
    }

    @Override public void onDestroy()
    {
	super.onDestroy();
	System.out.println("GPS Service onDestroy.");
	
	/* Remove alarms and unhook locator */
	am.cancel(amintent);
	lm.removeUpdates(ll);

	/* Remove notification */
	notman.cancelAll();
    }


    @Override public IBinder onBind(Intent i) {
	return binder;
    }

    @Override public boolean onUnbind(Intent i) {
	rpc_if = null;
	return false;
    }

    public void loadPrefs()
    {
	prefs.load();

	/* Update the request times */
	lm.removeUpdates(ll);

	/* Reset alarms */
	am.setRepeating(am.RTC_WAKEUP,
			System.currentTimeMillis() + 1000,
			prefs.update_interval, amintent);
    }

    /**************************************************************************
     * Helper classes for timed locator interaction
     *************************************************************************/
    class LocAlarm extends BroadcastReceiver
    {
	@Override public void onReceive(Context ctx, Intent i)
	{
	    System.out.println("GPS: Alarm!");

	    Preferences prefs = GPS.this.prefs;
	    LocationManager lm = GPS.this.lm;
	    LocListen ll = GPS.this.ll;

	    lm.removeUpdates(ll);
	    
	    if (prefs.provider == 1)
		lm.requestLocationUpdates(lm.GPS_PROVIDER, 0, 0, ll);
	    else
		lm.requestLocationUpdates(lm.NETWORK_PROVIDER, 0, 0, ll);
	}
    }

    class LocListen implements LocationListener 
    {
	@Override public void onProviderDisabled(String prov)
	{
	    System.out.println("BBG ProviderDisabled: "+prov);
	}

	@Override public void onProviderEnabled(String prov)
	{
	    System.out.println("BBG ProviderEnabled: "+prov);
	}

	@Override public void onStatusChanged(String prov, int stat, 
					      Bundle xtra)
	{
	    System.out.println("BBG Status change: "+prov+" -> "+stat);
	    if (rpc_if != null)
		rpc_if.onStateChange(prov, stat);
	}

	@Override public void onLocationChanged(Location loc)
	{
	    System.out.println("BBG got loc from "+loc.getProvider());
	    latitude = loc.getLatitude();
	    longitude = loc.getLongitude();
	    accuracy = loc.getAccuracy();

	    /* Stop waiting for locations. Will be restarted by alarm */
	    GPS.this.lm.removeUpdates(ll);

	    /* Change notification */
	    String txt = latitude+", "+longitude+", "+accuracy+"m";
	    GPS.this.notif.when = System.currentTimeMillis();
	    GPS.this.notif.setLatestEventInfo(GPS.this, 
					      getString(R.string.app_name),
					      txt, GPS.this.notintent);
	    notman.notify(0, notif);

	    /* Call to UI */
	    if (rpc_if != null) {
		rpc_if.onLocation(loc.getProvider(), latitude, longitude,
				  accuracy);
	    }

	}
    }
}
