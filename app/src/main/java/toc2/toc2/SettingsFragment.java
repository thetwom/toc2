package toc2.toc2;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;


/**
 * A simple {@link Fragment} subclass.
 */
class SettingsFragment extends PreferenceFragmentCompat { // implements SharedPreferences.OnSharedPreferenceChangeListener {

    private boolean playerServiceBound = false;
    private ServiceConnection playerConnection = null;
    private PlayerService playerService;
    private Context playerContext = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
//        super.onPrepareOptionsMenu(menu);
        MenuItem settingsItem = menu.findItem(R.id.action_properties);
        if(settingsItem != null)
            settingsItem.setVisible(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final ListPreference appearance = findPreference("appearance");

        if(appearance == null)
            throw new RuntimeException("No appearance preference");

        appearance.setSummary(getAppearanceSummary());
        appearance.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                NavigationActivity act = (NavigationActivity) getActivity();
                appearance.setSummary(getAppearanceSummary());
                if(act != null)
                    act.recreate();
                return true;
            }
        });

        final EditTextPreference minimumSpeed = findPreference("minimumspeed");
        if(minimumSpeed == null)
            throw new RuntimeException("No minimum speed preference");
        minimumSpeed.setSummary(minimumSpeed.getText() + " bpm");
        minimumSpeed.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
        });

        minimumSpeed.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(playerServiceBound) {
                    int speed = Integer.parseInt((String) newValue);
                    if(playerService.setMinimumSpeed(speed)){
                        minimumSpeed.setSummary(speed + " bpm");
                        return true;
                    }
                    else{
                        Toast.makeText(getActivity(), "Invalid minimum speed: "+speed, Toast.LENGTH_SHORT).show();
                    }
                }
                return false;
            }
        });

        final EditTextPreference maximumSpeed = findPreference("maximumspeed");
        if(maximumSpeed == null)
            throw new RuntimeException("No maximum speed preference");
        maximumSpeed.setSummary(maximumSpeed.getText() + " bpm");
        maximumSpeed.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
        });

        maximumSpeed.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(playerServiceBound) {
                    int speed = Integer.parseInt((String) newValue);
                    if(playerService.setMaximumSpeed(speed)){
                        maximumSpeed.setSummary(speed + " bpm");
                        return true;
                    }
                    else{
                        Toast.makeText(getActivity(), "Invalid maximum speed: "+speed, Toast.LENGTH_SHORT).show();
                    }
                }
                return false;
            }
        });

        CheckBoxPreference resetSettings = findPreference("setdefault");
        if(resetSettings == null)
            throw new RuntimeException("Set default preference not available");
        resetSettings.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                minimumSpeed.setText("20");
                minimumSpeed.getOnPreferenceChangeListener().onPreferenceChange(minimumSpeed, "20");
                maximumSpeed.setText("250");
                maximumSpeed.getOnPreferenceChangeListener().onPreferenceChange(maximumSpeed, "250");
                appearance.setValue("auto");
                appearance.getOnPreferenceChangeListener().onPreferenceChange(appearance, "auto");

                return false;
            }
        });

        return super.onCreateView(inflater, container, savedInstanceState);
    }


    @Override
    public void onPause() {
        if(playerServiceBound)
          unbindPlayerService();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        NavigationActivity act = (NavigationActivity) getActivity();
        if(act != null) {
            bindService(act.getApplicationContext());
        }
    }



    private void bindService(final Context context) {

        if(!playerServiceBound) {
            playerConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {

                    if (context != null) {
                        // We've bound to LocalService, cast the IBinder and get LocalService instance
                        PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
                        playerService = binder.getService();
                        playerServiceBound = true;
                        playerContext = context;
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName arg0) {
                    playerServiceBound = false;
                    playerContext = null;
                }
            };

            Intent serviceIntent = new Intent(context, PlayerService.class);
            context.bindService(serviceIntent, playerConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void unbindPlayerService() {
        playerServiceBound = false;
        playerContext.unbindService(playerConnection);
    }

    private String getAppearanceSummary() {
        final ListPreference listPreference = findPreference("appearance");
        assert listPreference != null;
        String state = listPreference.getValue();
        if(state.equals("auto"))
            return "Appearance follows system settings";
        else if(state.equals("dark")){
            return "Dark appearance";
        }
        else if(state.equals("light")){
            return "Light appearance";
        }
        throw new RuntimeException("No summary for given appearance value");
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
