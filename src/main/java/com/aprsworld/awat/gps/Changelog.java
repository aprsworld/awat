package com.aprsworld.awat.gps;

import android.app.Activity;
import android.os.Bundle;


public class Changelog extends Activity {
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* Create the UI */
        setContentView(R.layout.changelog);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
