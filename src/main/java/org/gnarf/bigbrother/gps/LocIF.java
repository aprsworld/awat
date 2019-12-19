package org.gnarf.bigbrother.gps;

import android.location.*;

public interface LocIF
{
    @SuppressWarnings({"EmptyMethod", "unused"})
    void onStateChange(String prov, int state);
    void onLocation(String prov, Location loc,
			   int bat_level, boolean charger, float temp, long uptime, long freespace);
    void onError(String err);
}
