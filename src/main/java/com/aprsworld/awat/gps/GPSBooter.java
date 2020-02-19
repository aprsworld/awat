package com.aprsworld.awat.gps;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;

public class GPSBooter extends BroadcastReceiver {
    public static final String TAG = "GPSBooter";

    @Override
    public void onReceive(Context ctx, Intent i) {
        Preferences prefs = new Preferences(ctx);
        prefs.load();

        if (i.getAction() != Intent.ACTION_BOOT_COMPLETED) {
            return;
        }

        if (prefs.start_on_boot) {
            Intent svc_i = new Intent(ctx, GPS.class);
            ComponentName svc = ctx.startService(svc_i);
            if (svc == null)
                System.out.println("AWAT: Failed starting service");
            else
                System.out.println("AWAT: Started service");

        } else {
            System.out.println("AWAT: Not starting on boot");
        }
    }

}
