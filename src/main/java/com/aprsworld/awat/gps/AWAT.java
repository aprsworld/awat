package com.aprsworld.awat.gps;

import java.util.*;

import java.text.SimpleDateFormat;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import android.view.View;
import android.widget.TextView;
import android.widget.Button;

import android.location.*;

import com.aprsworld.android.Helper;

public class AWAT extends Activity {
    @SuppressWarnings("unused")
    Bundle state;

    /* Prefs */
    Preferences prefs;

    /* References to our service */
    Intent srvint;
    ComponentName srv;
    Con servicecon;
    LocBinder binder;

    /* Our UI components */
    private TextView time;
    private TextView prov, lat, lon, alt, acc;
    private TextView brg, spd;
    private TextView batlev, chrgr;
    private TextView log;
    // DAR
    private TextView temp;
    private TextView uptime;
    private TextView freespace;
    // !DAR

    SimpleDateFormat dateformatter;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        System.out.println("onCreate called");
        this.state = savedInstanceState;

        /* Get prefs */
        this.prefs = new Preferences(this);
        this.prefs.load();

        startTheGPS();

        startUI();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        System.out.println("onDestroy called");

        /* Disconnect from service */
        unbindService(this.servicecon);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, "Update location now");
        menu.add(0, 1, 1, "Changelog");
        menu.add(0, 100, 100, "Settings")
                .setIcon(android.R.drawable.ic_menu_preferences);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                if (this.binder != null)
                    this.binder.triggerUpdate();
                break;
            case 1:
                Intent changelog = new Intent(this, Changelog.class);
                startActivity(changelog);
                break;
            case 100:
                Intent prefA = new Intent(this, PreferencesActivity.class);
                startActivityForResult(prefA, 0);
                break;
        }

        return true;
    }

    @Override
    public void onActivityResult(int req, int res, Intent i) {
        /* Get prefs */
        this.prefs.load();

        /* Notify the service that preferences might have changed */
        if (this.binder != null)
            this.binder.updatePrefs();
    }

    private void startTheGPS() {
        /* Start GPS service */
        this.srvint = new Intent(this, GPS.class);
        this.srv = startService(this.srvint);
        this.servicecon = new Con();
        boolean bind = bindService(this.srvint, this.servicecon, 0);
        if (this.srv == null && bind) {
            Helper.ok_dialog(this, "Service", "Failed starting GPS service");
            finish();
        }
    }

    private void startUI() {
        /* Create the UI */
        setContentView(R.layout.main);

        /* Date/time formatting */
        this.dateformatter = new SimpleDateFormat("HH:mm:ss.SS", Locale.US);

        /* Lookup our text fields */
        this.time = findViewById(R.id.main_time);

        this.prov = findViewById(R.id.main_provider);
        this.lat = findViewById(R.id.main_latitude);
        this.lon = findViewById(R.id.main_longitude);
        this.alt = findViewById(R.id.main_altitude);
        this.acc = findViewById(R.id.main_accuracy);

        this.brg = findViewById(R.id.main_bearing);
        this.spd = findViewById(R.id.main_speed);

        this.batlev = findViewById(R.id.main_bat_level);
        this.chrgr = findViewById(R.id.main_bat_charger);

        this.log = findViewById(R.id.main_log);

        // DAR
        this.temp = findViewById(R.id.main_temperature);
        this.uptime = findViewById(R.id.main_uptime);
        this.freespace = findViewById(R.id.main_freespace);
        // !DAR

        /* Hook the button */
        Button btn;

        /* end button */
        btn = findViewById(R.id.main_stop);
        if (btn != null)
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    stopService(AWAT.this.srvint);
                    AWAT.this.finish();
                }
            });

        /* update button */
        btn = findViewById(R.id.main_triggerupdate);
        if (btn != null)
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (AWAT.this.binder != null)
                        AWAT.this.binder.triggerUpdate();
                }
            });
    }

    void logError(String err) {
        /* Add error message at top. */
        this.log.setText(err + "\n" + this.log.getText());

        /* Keep only last 10 */
        String oldlog = this.log.getText().toString();
        String[] log = oldlog.split("\n");
        if (log.length > 10) {
            StringBuffer newlog = new StringBuffer();
            for (int i = 0; i < 10; i++)
                newlog.append(log[i]).append("\n");
            this.log.setText(newlog);
        }

    }


    /************************************************************************ 
     * Helper classes for communication with the service
     ************************************************************************/

    /* Wrapper so we get connected to the service on bind */
    class Con implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            LocBinder lb = (LocBinder) service;
            CallBackIF cb = new CallBackIF();

            /* Read last position from locator */
            cb.onLocation("init", lb.getLocation(),
                    lb.getBattery(), lb.getCharger(), lb.getTemp(), lb.getUptime(), lb.getFreespace());


            /* Bind for updates */
            AWAT.this.binder = lb;
            lb.setCallback(new CallBackIF());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }


    /* The actual connection interface class */
    @SuppressWarnings("AccessStaticViaInstance")
    class CallBackIF implements LocIF {
        @Override
        public void onError(String err) {
            AWAT.this.logError(err);
        }

        @Override
        public void onStateChange(String prov, int state) {
            Log.d("AWAT","OnStateChange('" + prov + "'," + state + ",)");
        }

        @Override
        public void onLocation(String prov, Location loc,
                               int bat_level, boolean charger, float temp, long uptime, long freespace) {
            if (loc == null)
                return;

            double latitude = loc.getLatitude();
            double longitude = loc.getLongitude();
            int accuracy = (int) loc.getAccuracy();
            double altitude = loc.getAltitude();
            double bearing = (double) loc.getBearing();
            double speed = (double) loc.getSpeed();

            Date date = new Date(loc.getTime());
            String df = AWAT.this.dateformatter.format(date);

            /* Set format */
            int cf;
            switch (AWAT.this.prefs.coordinate_format) {
                case 2:
                    cf = loc.FORMAT_MINUTES;
                    break;
                case 3:
                    cf = loc.FORMAT_SECONDS;
                    break;
                default:
                    cf = loc.FORMAT_DEGREES;
                    break;
            }

            AWAT.this.time.setText(df);
            //AWAT.this.prov.setText(loc.getProvider());
            AWAT.this.prov.setText(prov);
            AWAT.this.lat.setText(loc.convert(latitude, cf));
            AWAT.this.lon.setText(loc.convert(longitude, cf));
            AWAT.this.alt.setText(String.format(Locale.US, "%.2f", altitude));
            AWAT.this.acc.setText(String.format(Locale.US, "%d", accuracy));
            AWAT.this.brg.setText(String.format(Locale.US, "%.2f", bearing));
            AWAT.this.spd.setText(String.format(Locale.US, "%.2f", speed));
            AWAT.this.batlev.setText(String.format(Locale.US, "%d", bat_level));
            if (charger)
                AWAT.this.chrgr.setText("(charging)");
            else
                AWAT.this.chrgr.setText("");
            AWAT.this.temp.setText(String.format(Locale.US, "%.1f", temp));
            AWAT.this.uptime.setText(String.format(Locale.US, "%d", uptime));
            AWAT.this.freespace.setText(String.format(Locale.US, "%d", freespace));
        }
    }
}
