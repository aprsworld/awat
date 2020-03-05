package com.aprsworld.awat.gps;

import android.preference.PreferenceManager;
import android.content.Context;
import android.content.SharedPreferences;

class Preferences {
    private final Context ctx;

    /* The preference values */
    public String target_url;
    public String secret;
    public int update_interval;
    public int provider;
    public int gps_timeout;
    public int coordinate_format;
    public boolean start_on_boot;
    public boolean improve_accuracy;
    public boolean continous_mode;
    public boolean http_resp_in_notif_bar;
    public boolean send_provider;
    public boolean send_altitude;
    public boolean send_bearing;
    public boolean send_speed;
    public boolean send_time;
    public boolean send_batt_status;
    public boolean send_devid;
    public boolean send_subscrid;
    public boolean send_temp;
    public boolean send_uptime;
    public boolean send_freespace;
    public boolean send_signal;
    public boolean send_extras;
    public boolean send_systime;
    public boolean send_screen;
    public boolean send_build_versioncode;
    public boolean send_build_version;
    public boolean send_build_date;
    public boolean send_build_type;
    public boolean send_build_git_hash;
    public boolean send_build_git_clean;


    Preferences(Context ctx) {
        this.ctx = ctx;
    }

    public void load() {
        String tmp;

        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this.ctx);

        this.target_url = prefs.getString("target_url", "");

        tmp = prefs.getString("update_interval", "10");
        this.update_interval = Integer.parseInt(tmp) * 60 * 1000;

        boolean tb = prefs.getBoolean("provider", true);
        if (tb)
            this.provider = 1;
        else
            this.provider = 0;

        this.improve_accuracy = prefs.getBoolean("improve_accuracy", true);
        this.continous_mode = prefs.getBoolean("continous_mode", false);

        tmp = prefs.getString("gps_timeout", "30");
        this.gps_timeout = Integer.parseInt(tmp);
        this.gps_timeout *= 1000;

        if (this.gps_timeout + 1000 > this.update_interval) {
            this.continous_mode = true;
        }

        this.start_on_boot = prefs.getBoolean("start_on_boot", true);
        this.http_resp_in_notif_bar = prefs.getBoolean("http_resp_in_notif_bar", false);

        secret = prefs.getString("secret", null);
        if (secret == null || secret.length() < 1)
            secret = null;

        tmp = prefs.getString("coordinate_format", "1");
        this.coordinate_format = Integer.parseInt(tmp);

        this.send_provider = prefs.getBoolean("send_provider", true);
        this.send_altitude = prefs.getBoolean("send_altitude", true);
        this.send_bearing = prefs.getBoolean("send_bearing", true);
        this.send_speed = prefs.getBoolean("send_speed", true);
        this.send_time = prefs.getBoolean("send_time", true);
        this.send_batt_status = prefs.getBoolean("send_batt_status", true);
        this.send_devid = prefs.getBoolean("send_devid", true);
        this.send_subscrid = prefs.getBoolean("send_subscrid", true);
        this.send_temp = prefs.getBoolean("send_temp", true);
        this.send_uptime = prefs.getBoolean("send_uptime", true);
        this.send_freespace = prefs.getBoolean("send_freespace", true);
        this.send_signal = prefs.getBoolean("send_signal", true);
        this.send_extras = prefs.getBoolean("send_extras", true);
        this.send_systime = prefs.getBoolean("send_systime", true);
        this.send_screen = prefs.getBoolean("send_screen", true);
        this.send_build_date = prefs.getBoolean("send_build_date", true);
        this.send_build_version = prefs.getBoolean("send_build_version", true);
        this.send_build_versioncode = prefs.getBoolean("send_build_versioncode", true);
        this.send_build_type = prefs.getBoolean("send_build_type", true);
        this.send_build_git_hash = prefs.getBoolean("send_build_git_hash", true);
        this.send_build_git_clean = prefs.getBoolean("send_build_git_clean", true);
    }
}

