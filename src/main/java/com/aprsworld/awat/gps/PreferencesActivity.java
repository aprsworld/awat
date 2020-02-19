package com.aprsworld.awat.gps;

import android.preference.PreferenceActivity;
import android.os.Bundle;
import android.preference.PreferenceFragment;

/* The preference displaying and poking around activity */
public class PreferencesActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferencesFragment()).commit();
    }

    public static class PreferencesFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }
    }
}
