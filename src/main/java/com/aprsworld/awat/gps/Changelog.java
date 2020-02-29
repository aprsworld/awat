package com.aprsworld.awat.gps;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;


public class Changelog extends Activity {
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* Create the UI */
        setContentView(R.layout.changelog);

        /* Populate Build Date */
        TextView tv;
        tv = findViewById(R.id.changelog_build_versioncode);
        tv.setText(String.format(Locale.US, "%d", BuildConfig.VERSION_CODE));
        tv = findViewById(R.id.changelog_build_version);
        tv.setText(BuildConfig.VERSION_NAME);
        tv = findViewById(R.id.changelog_build_date);
        tv.setText((new SimpleDateFormat("MM/dd/YYYY HH:mm:ss.SS", Locale.US)).format(BuildConfig.BUILD_TIME));
        tv = findViewById(R.id.changelog_build_type);
        tv.setText(BuildConfig.BUILD_TYPE);
        tv = findViewById(R.id.changelog_build_git_hash);
        tv.setText(BuildConfig.BUILD_GIT_HASH);
        tv = findViewById(R.id.changelog_build_git_clean);
        tv.setText(BuildConfig.BUILD_GIT_CLEAN ? "True" : "False");
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
