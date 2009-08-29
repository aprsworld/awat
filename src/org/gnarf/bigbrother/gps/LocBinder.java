package org.gnarf.bigbrother.gps;

import android.os.Binder;

import org.gnarf.bigbrother.gps.*;

/* This class is a RPC binder for the GPS class. It is instantiated in
 * the GPS class and returned in onBind() */
public class LocBinder extends Binder
{
    private GPS gps;

    LocBinder(GPS gps)
    {
	this.gps = gps;
    }


    public void setCallback(LocIF the_if)
    {
	gps.rpc_if = the_if;
    }

    public double getLatitude()
    {
	return gps.latitude;
    }

    public double getLongitude()
    {
	return gps.longitude;
    }

    public float getAccuracy()
    {
	return gps.accuracy;
    }

    public void updatePrefs()
    {
	gps.loadPrefs();
    }
}
