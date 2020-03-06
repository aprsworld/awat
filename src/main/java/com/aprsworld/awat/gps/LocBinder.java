package com.aprsworld.awat.gps;

import android.location.*;
import android.os.Binder;

/* This class is a RPC binder for the GPS class. It is instantiated in
 * the GPS class and returned in onBind() */
class LocBinder extends Binder {
    private final GPS gps;

    LocBinder(GPS gps) {
        this.gps = gps;
    }


    void setCallback(LocIF the_if) {
        this.gps.rpc_if = the_if;
    }

    public Location getLocation() {
        return this.gps.location;
    }

    int getBattery() {
        return this.gps.bat_level;
    }

    boolean getCharger() {
        return this.gps.charger;
    }

    float getTemp() {
        return this.gps.bat_temp;
    }

    long getUptime() {
        return this.gps.uptime;
    }

    long getFreespace() {
        return this.gps.freespace;
    }


    void updatePrefs() {
        System.out.println("Doing updatePrefs");
        this.gps.reloadPrefs();
    }

    void triggerUpdate() {
        this.gps.update_now = true;
        this.gps.triggerUpdate();
    }
}
