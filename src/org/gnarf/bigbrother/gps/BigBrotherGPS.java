package org.gnarf.bigbrother.gps;

import java.util.*;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;

import android.view.Menu;
import android.view.MenuItem;

import android.view.View;
import android.widget.TextView;
import android.widget.Button;

import android.location.*;

import org.gnarf.android.Helper;
import org.gnarf.bigbrother.gps.*;

public class BigBrotherGPS extends Activity
{
    Bundle state;
    
    /* References to our service */
    Intent srvint;
    ComponentName srv;
    Con servicecon;
    LocBinder binder;

    /* Our UI components */
    private TextView lat, lon, acc;


    /** Called when the activity is first created. */
    @Override public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

	System.out.println("onCreate called");
	this.state = savedInstanceState;

	startTheGPS();

	startUI();
    }


    @Override public void onDestroy()
    {
	super.onDestroy();
	System.out.println("onDestroy called");

	/* Disconnect from service */
	unbindService(this.servicecon);
    }    

    
    @Override public boolean onCreateOptionsMenu(Menu menu)
    {
	menu.add(0, 0, 0, "Update location now");
	menu.add(0, 100, 100, "Settings")
	    .setIcon(android.R.drawable.ic_menu_preferences);
	return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item)
    {
	switch(item.getItemId()) {
	case 0:
	    if (this.binder != null)
		this.binder.triggerUpdate();
	    break;
	case 100:
	    Intent prefs = new Intent(this, PreferencesActivity.class);
	    startActivityForResult(prefs, 0);
	    break;
	}

	return true;
    }

    @Override public void onActivityResult(int req, int res, Intent i)
    {
	/* Notify the service that preferences might have changed */
	if (this.binder != null)
	    this.binder.updatePrefs();
    }

    private void startTheGPS()
    {
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

    private void startUI()
    {
	/* Create the UI */
        setContentView(R.layout.main);

	/* Lookup our text fields */
	this.lat = (TextView)findViewById(R.id.main_latitude);
	this.lon = (TextView)findViewById(R.id.main_longitude);
	this.acc = (TextView)findViewById(R.id.main_accuracy);

	/* Hook the button */
        Button btn = (Button)findViewById(R.id.main_stop);
	if (btn != null)
	    btn.setOnClickListener(new View.OnClickListener() {
		    public void onClick(View v) {
			stopService(BigBrotherGPS.this.srvint);
			BigBrotherGPS.this.finish();
		    }
		});
    }



    /************************************************************************ 
     * Helper classes for communication with the service
     ************************************************************************/

    /* Wrapper so we get connected to the service on bind */
    class Con implements ServiceConnection
    {
	@Override public void onServiceConnected(ComponentName name, 
						 IBinder service)
	{
	    LocBinder lb = (LocBinder)service;
	    CallBackIF cb = new CallBackIF();

	    /* Read last position from locator */
	    cb.onLocation("init", lb.getLatitude(), lb.getLongitude(),
			  lb.getAccuracy());
	    

	    /* Bind for updates */
	    BigBrotherGPS.this.binder = lb;
	    lb.setCallback(new CallBackIF());
	}

	@Override public void onServiceDisconnected(ComponentName name) {};
    }
    


    /* The actual connection interface class */
    class CallBackIF implements LocIF
    {
	@Override public void onError(String err)
	{
	    Helper.ok_dialog(BigBrotherGPS.this, "Error", err);
	}
	
	@Override public void onStateChange(String prov, int state) {}

	@Override public void onLocation(String prov, double latitude, 
					 double longitude, float accuracy)
	{
	    BigBrotherGPS.this.lat.setText((new Double(latitude)).toString());
	    BigBrotherGPS.this.lon.setText((new Double(longitude)).toString());
	    BigBrotherGPS.this.acc.setText((new Integer((int)accuracy)).toString());
	}
    }
}
