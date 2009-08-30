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
	    PreferenceManager.getDefaultSharedPreferences(ctx);

	target_url = prefs.getString("target_url", null);

	tmp = prefs.getString("update_interval","15");
	update_interval = (new Integer(tmp)).intValue();
	update_interval *= 60 * 1000;

	Boolean tb = prefs.getBoolean("provider", true);
	if (tb)
	    provider = 1;
	else
	    provider = 0;

	tmp = prefs.getString("gps_timeout","10");
	gps_timeout = (new Integer(tmp)).intValue();
	gps_timeout *= 1000;

    }
}

