package org.gnarf.bigbrother.gps;

import java.util.*;

import android.app.Service;
import android.os.Bundle;
import android.os.IBinder;
import android.content.Context;
import android.content.Intent;

import android.location.*;

public class GPS extends Service
{
    /* Location manager params */
    LocationManager lm;
    LocListen ll;

    /* RPC */
    LocBinder binder;
    public LocIF rpc_if;

    /* Our position data from last read */
    public double latitude;
    public double longitude;
    public float accuracy;


    @Override public void onCreate()
    {
	super.onCreate();
	System.out.println("GPS Service onCreate.");

	/* Set up the position listener */
	ll = new LocListen();
	lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
	lm.requestLocationUpdates(lm.NETWORK_PROVIDER, 60000, 0, ll);
	lm.requestLocationUpdates(lm.GPS_PROVIDER, 600000, 0, ll);

	binder = new LocBinder(this);
    }

    @Override public void onDestroy()
    {
	super.onDestroy();
	System.out.println("GPS Service onDestroy.");
	
	/* Unhook the locator */
	lm.removeUpdates(ll);
    }


    @Override public IBinder onBind(Intent i) {
	return binder;
    }

    @Override public boolean onUnbind(Intent i) {
	rpc_if = null;
	return false;
    }


    /* Location listener class */
    class LocListen implements LocationListener {
	@Override public void onProviderDisabled(String prov)
	{
	    System.out.println("BBG ProviderDisabled: "+prov);
	}

	@Override public void onProviderEnabled(String prov)
	{
	    System.out.println("BBG ProviderEnabled: "+prov);
	}

	@Override public void onStatusChanged(String prov, int stat, Bundle xtra)
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

	    /* Call to UI */
	    if (rpc_if != null) {
		rpc_if.onLocation(loc.getProvider(), latitude, longitude, accuracy);
	    }

	}
    }
}
