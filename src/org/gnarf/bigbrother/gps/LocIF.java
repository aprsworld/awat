package org.gnarf.bigbrother.gps;

public interface LocIF
{
    public void onStateChange(String prov, int state);
    public void onLocation(String prov, 
			   double longitude, double latitude, float accuracy);
    public void onError(String err);
}
