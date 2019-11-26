package org.gnarf.bigbrother.gps;

import android.location.*;

public interface LocIF
{
    public void onStateChange(String prov, int state);
    public void onLocation(String prov, Location loc,
			   int bat_level, boolean charger, float temp, long uptime, long freespace);
    public void onError(String err);
}
