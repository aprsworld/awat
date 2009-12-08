package org.gnarf.bigbrother.gps;

public interface LocIF
{
    public void onStateChange(String prov, int state);
    public void onLocation(String prov, 
			   double longitude, double latitude, float accuracy,
			   int bat_level, boolean charger);
    public void onError(String err);
}
