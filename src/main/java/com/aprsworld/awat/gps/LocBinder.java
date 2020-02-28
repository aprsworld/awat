package com.aprsworld.awat.gps;

import android.location.*;

import android.os.Binder;

import com.aprsworld.awat.gps.*;

/* This class is a RPC binder for the GPS class. It is instantiated in
 * the GPS class and returned in onBind() */
class LocBinder extends Binder {
    private final GPS gps;

    LocBinder(GPS gps) {
        this.gps = gps;
    }


    public void setCallback(LocIF the_if) {
        this.gps.rpc_if = the_if;
    }

    public Location getLocation() {
        return this.gps.location;
    }

    public int getBattery() {
        return this.gps.bat_level;
    }

    public boolean getCharger() {
        return this.gps.charger;
    }

    public float getTemp() {
        return this.gps.bat_temp;
    }

    public long getUptime() {
        return this.gps.uptime;
    }

    public long getFreespace() {
        return this.gps.freespace;
    }


    public void updatePrefs() {
        System.out.println("Doing updatePrefs");
        this.gps.reloadPrefs();
    }

    public void triggerUpdate() {
        this.gps.triggerUpdate();
    }
}
