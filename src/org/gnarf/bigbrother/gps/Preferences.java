package org.gnarf.bigbrother.gps;

import android.preference.PreferenceManager;
import android.content.Context;
import android.content.SharedPreferences;

public class Preferences
{
    Context ctx;

    /* The preference values */
    public String target_url;
    public int update_interval;
    public int provider;
    public int gps_timeout;

    Preferences(Context ctx)
    {
	this.ctx = ctx;
    }

    public void load()
    {
	String tmp;

	SharedPreferences prefs = 
	    PreferenceManager.getDefaultSharedPreferences(this.ctx);

	this.target_url = prefs.getString("target_url", null);

	tmp = prefs.getString("update_interval","15");
	this.update_interval = (new Integer(tmp)).intValue();
	this.update_interval *= 60 * 1000;

	Boolean tb = prefs.getBoolean("provider", true);
	if (tb)
	    this.provider = 1;
	else
	    this.provider = 0;
	
	tmp = prefs.getString("gps_timeout","10");
	this.gps_timeout = (new Integer(tmp)).intValue();
	this.gps_timeout *= 1000;

    }
}

