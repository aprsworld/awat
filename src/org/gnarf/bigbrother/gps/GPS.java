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

import java.io.*;
import java.net.*;
import java.util.*;

import org.gnarf.bigbrother.gps.*;

public class GPS extends Service
{
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

    /* Notification */
    NotificationManager notman;
    Notification notif;
    PendingIntent notintent;

    /* RPC */
    LocBinder binder;
    public LocIF rpc_if;
    URL target_url;

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

	setupLocationListener();

	setupNotif();

	setupAlarms();

	/* Get prefs */
	this.prefs = new Preferences(this);
	this.prefs.load();
	reconfigure();

	/* Create binder */
	this.binder = new LocBinder(this);
    }

    @Override public void onDestroy()
    {
	super.onDestroy();
	System.out.println("GPS Service onDestroy.");
	
	/* Remove alarms and unhook locator */
	this.am.cancel(this.amintent);
	this.am.cancel(this.tointent);
	this.lm.removeUpdates(this.ll);
	unregisterReceiver(this.recvr);
	unregisterReceiver(this.recvTimeout);

	/* Remove notification */
	this.notman.cancelAll();
    }


    @Override public IBinder onBind(Intent i) 
    {
	return this.binder;
    }

    @Override public boolean onUnbind(Intent i) 
    {
	this.rpc_if = null;
	return false;
    }

    public void triggerUpdate() 
    {
	this.lm.removeUpdates(this.ll);
	
	try {
	    if (this.prefs.provider == 1)
		this.lm.requestLocationUpdates(this.lm.GPS_PROVIDER, 0, 0, 
					       this.ll);
	    else
		this.lm.requestLocationUpdates(this.lm.NETWORK_PROVIDER, 0, 0,
					       this.ll);
	}
	catch (IllegalArgumentException e) {
	    System.out.println("BigBrotherGPS: "+e.toString());
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

    private void setupLocationListener()
    {
	/* Set up the position listener */
	this.ll = new LocListen();
	this.lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
    }

    private void setupNotif()
    {
	/* Set up a persistent notification */
	this.notman = (NotificationManager)
	    getSystemService(Context.NOTIFICATION_SERVICE);
	this.notif = new Notification(R.drawable.notif_icon,
				      "BigBrother GPS Waiting for location",
				      System.currentTimeMillis());
	this.notif.flags = notif.FLAG_ONGOING_EVENT;
	this.notintent = 
	    PendingIntent.getActivity(this, 0, 
				      new Intent(this, BigBrotherGPS.class),0);
	this.notif.setLatestEventInfo(this, getString(R.string.app_name),
				      "Waiting for initial location", 
				      notintent);
	this.notman.notify(0, notif);
    }

    private void setupAlarms()
    {
	/* Prepare alarm manager */
	this.am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);

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

    public void doTimeout()
    {
	if (System.currentTimeMillis() > this.timeout) {
	    System.out.println("BigBrotherGPS: Doing timeout");
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
	
	/* Update the request times */
	this.lm.removeUpdates(ll);

	/* Reset update alarms */
	this.am.setRepeating(this.am.RTC_WAKEUP,
			     System.currentTimeMillis() + 1000,
			     this.prefs.update_interval, this.amintent);

	/* Set URL */
	this.target_url = null;
	try {
	    this.target_url = new URL(this.prefs.target_url);
	}
	catch (MalformedURLException e) {
	    System.out.println("BigBrotherGPS: "+e.toString());
	    this.target_url = null;
	}
    }

    /* Send a request to the URL and post some data */
    protected void postLocation()
    {
	/* No url, don't do anything */
	if (this.target_url == null)
	    return;

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
	con.setDoInput(false);

	/* Build request data */
	String req = "latitude="+this.latitude;
	req += "&longitude="+this.longitude;
	req += "&accuracy="+this.accuracy;
	con.setRequestProperty("Content-Length", ""+req.length());

	/* Connect and write */
	try {
	    con.connect();
	    DataOutputStream wr = 
		new DataOutputStream(con.getOutputStream());
	    wr.writeBytes(req);
	    wr.flush();
	    wr.close();
	} 
	catch (IOException e) {
	    System.out.println("BigBrotherGPS: "+e.toString());
	    if (this.rpc_if != null)
		this.rpc_if.onError(e.toString());
	    return;
	}
	con.disconnect();

	System.out.println("BigBrotherGPS sent HTTP poke");
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
	}
    }

    class LocTimeout extends BroadcastReceiver
    {
	@Override public void onReceive(Context ctx, Intent i)
	{
	    System.out.println("BigBrotherGPS: Timeout!");
	    GPS.this.doTimeout();
	}
    }

    class LocListen implements LocationListener 
    {
	@Override public void onProviderDisabled(String prov)
	{
	    System.out.println("BigBrotherGPS ProviderDisabled: "+prov);
	    GPS.this.doTimeout();	    
	}

	@Override public void onProviderEnabled(String prov)
	{
	    System.out.println("BigBrotherGPS ProviderEnabled: "+prov);
	    GPS.this.doTimeout();	    
	}

	@Override public void onStatusChanged(String prov, int stat, 
					      Bundle xtra)
	{
	    System.out.println("BigBrotherGPS Status change: "
			       +prov+" -> "+stat);
	    if (GPS.this.rpc_if != null)
		GPS.this.rpc_if.onStateChange(prov, stat);

	    GPS.this.doTimeout();
	}

	@Override public void onLocationChanged(Location loc)
	{
	    System.out.println("BigBrotherGPS got loc from "
			       +loc.getProvider());
	    GPS.this.latitude = loc.getLatitude();
	    GPS.this.longitude = loc.getLongitude();
	    GPS.this.accuracy = loc.getAccuracy();

	    /* Stop waiting for locations. Will be restarted by alarm */
	    GPS.this.lm.removeUpdates(GPS.this.ll);
	    GPS.this.am.cancel(GPS.this.tointent);

	    /* Change notification */
	    String txt = GPS.this.latitude+", "
		+GPS.this.longitude+", "
		+(int)GPS.this.accuracy+"m";
	    GPS.this.notif.when = System.currentTimeMillis();
	    GPS.this.notif.setLatestEventInfo(GPS.this, 
					      getString(R.string.app_name),
					      txt, GPS.this.notintent);
	    GPS.this.notman.notify(0, notif);

	    /* Call to UI */
	    if (GPS.this.rpc_if != null) {
		GPS.this.rpc_if.onLocation(loc.getProvider(), 
					   GPS.this.latitude, 
					   GPS.this.longitude,
					   GPS.this.accuracy);
	    }

	    /* Post to server */
	    GPS.this.postLocation();
	}
    }
}
