package com.aprsworld.awat.gps;

import android.location.*;

public interface LocIF {
    void onStateChange(String prov, int state);

    void onLocation(String prov, Location loc,
                    int bat_level, boolean charger, float temp, long uptime, long freespace);

    void onError(String err);
}
