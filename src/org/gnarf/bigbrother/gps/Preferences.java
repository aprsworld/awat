package org.gnarf.bigbrother.gps;

import android.preference.PreferenceManager;
import android.content.Context;
import android.content.SharedPreferences;

public class Preferences
{
    Context ctx;

    /* The preference values */
    public String target_url;
    public String secret;
    public int update_interval;
    public int provider;
    public int gps_timeout;
    public boolean start_on_boot;
    public boolean show_in_notif_bar;
    public boolean send_batt_status;

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

	float tmpf;
	tmp = prefs.getString("update_interval","15");
	tmpf = (new Float(tmp)).floatValue();
	tmpf *= 60 * 1000;
	this.update_interval = (int)tmpf;

	Boolean tb = prefs.getBoolean("provider", true);
	if (tb)
	    this.provider = 1;
	else
	    this.provider = 0;
	
	tmp = prefs.getString("gps_timeout","10");
	this.gps_timeout = (new Integer(tmp)).intValue();
	this.gps_timeout *= 1000;

	this.start_on_boot = prefs.getBoolean("start_on_boot", false);
	this.show_in_notif_bar = prefs.getBoolean("show_in_notif_bar", true);

	secret = prefs.getString("secret", null);
	if (secret == null || secret.length() < 1)
	    secret = null;

	this.send_batt_status = prefs.getBoolean("send_batt_status", false);
    }
}

