package toc2.toc2;


import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


/**
 * A simple {@link Fragment} subclass.
 */
public class SettingsFragment extends PreferenceFragmentCompat { // implements SharedPreferences.OnSharedPreferenceChangeListener {

    private SwitchPreferenceCompat darkThemeSwitch;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        darkThemeSwitch = (SwitchPreferenceCompat) getPreferenceManager().findPreference("darktheme");
        darkThemeSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                NavigationActivity act = (NavigationActivity) getActivity();
                if((Boolean) newValue){
                    act.setNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                }
                else {
                    act.setNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
                return true;
            }
        });

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    //    @Override
//    public void onResume() {
//        //super.onResume();
//
//    }
//
//    @Override
//    public void onPause() {
//        super.onPause();
//        //getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
//    }
//
//    @Override
//    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
//
//    }
}
