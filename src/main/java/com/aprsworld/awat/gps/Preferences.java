package com.aprsworld.awat.gps;

import android.preference.PreferenceManager;
import android.content.Context;
import android.content.SharedPreferences;

class Preferences {
    private final Context ctx;
    SharedPreferences prefs;

    /* The preference values */
    String target_url;
    String secret;
    int update_interval;
    int provider;
    int gps_timeout;
    int coordinate_format;
    boolean start_on_boot;
    boolean improve_accuracy;
    boolean continuous_mode;
    boolean http_resp_in_notif_bar;
    boolean send_provider;
    boolean send_altitude;
    boolean send_bearing;
    boolean send_speed;
    boolean send_time;
    boolean send_orientation;
    boolean send_batt_status;
    boolean send_devid;
    boolean send_subscrid;
    boolean send_batt_temp;
    boolean send_uptime;
    boolean send_freespace;
    boolean send_signal;
    boolean send_extras;
    boolean send_systime;
    boolean send_screen;
    boolean send_build_versioncode;
    boolean send_build_version;
    boolean send_build_date;
    boolean send_build_type;
    boolean send_build_git_hash;
    boolean send_build_git_clean;

    Preferences(Context ctx) {
        this.ctx = ctx;
    }

    void load() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this.ctx);

        this.target_url = prefs.getString("target_url", "");
        this.update_interval = Integer.parseInt(prefs.getString("update_interval", "10")) * 60 * 1000;

        if (prefs.getBoolean("provider", true))
            this.provider = 1;
        else
            this.provider = 0;
        this.improve_accuracy = prefs.getBoolean("improve_accuracy", true);
        this.continuous_mode = prefs.getBoolean("continuous_mode", false);
        this.gps_timeout = Integer.parseInt(prefs.getString("gps_timeout", "30")) * 1000;
        if (this.gps_timeout + 1000 > this.update_interval) {
            this.continuous_mode = true;
        }

        this.start_on_boot = prefs.getBoolean("start_on_boot", true);
        this.http_resp_in_notif_bar = prefs.getBoolean("http_resp_in_notif_bar", false);

        secret = prefs.getString("secret", null);
        if (secret == null || secret.length() < 1)
            secret = null;

        this.coordinate_format = Integer.parseInt(prefs.getString("coordinate_format", "1"));

        this.send_provider = prefs.getBoolean("send_provider", true);
        this.send_altitude = prefs.getBoolean("send_altitude", true);
        this.send_bearing = prefs.getBoolean("send_bearing", true);
        this.send_speed = prefs.getBoolean("send_speed", true);
        this.send_time = prefs.getBoolean("send_time", true);
        this.send_batt_status = prefs.getBoolean("send_batt_status", true);
        this.send_devid = prefs.getBoolean("send_devid", true);
        this.send_subscrid = prefs.getBoolean("send_subscrid", true);
        this.send_batt_temp = prefs.getBoolean("send_batt_temp", true);
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
        this.send_orientation = prefs.getBoolean("send_orientation", true);
    }
}

