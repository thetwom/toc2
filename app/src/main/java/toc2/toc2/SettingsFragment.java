package toc2.toc2;


import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

//import android.widget.EditText;
import android.widget.EditText;
import android.widget.TextView;
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

        MenuItem loadDataItem = menu.findItem(R.id.action_load);
        if(loadDataItem != null)
            loadDataItem.setVisible(false);

        MenuItem saveDataItem = menu.findItem(R.id.action_save);
        if(saveDataItem != null)
            saveDataItem.setVisible(false);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

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
        minimumSpeed.setSummary(getString(R.string.bpm, Integer.parseInt(minimumSpeed.getText())));
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

                        Log.v("Metronome", "Changed minimum speed summary");
//                        minimumSpeed.notifyChanged();
//                        getListView().getAdapter().notifyDataSetChanged();
                        minimumSpeed.setSummary(getString(R.string.bpm, speed));

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
        maximumSpeed.setSummary(getString(R.string.bpm, Integer.parseInt(maximumSpeed.getText())));
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
                        maximumSpeed.setSummary(getString(R.string.bpm, speed));
//                        getListView().getAdapter().notifyDataSetChanged();
//                        getListView().getAdapter().notifyDataSetChanged();
                        return true;
                    }
                    else{
                        Toast.makeText(getActivity(), "Invalid maximum speed: "+speed, Toast.LENGTH_SHORT).show();
                    }
                }
                return false;
            }
        });

        Preference resetSettings = findPreference("setdefault");
        if(resetSettings == null)
            throw new RuntimeException("Set default preference not available");
        resetSettings.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                        .setTitle(R.string.reset_settings_prompt)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                minimumSpeed.setText("20");
                                minimumSpeed.getOnPreferenceChangeListener().onPreferenceChange(minimumSpeed, "20");
                                maximumSpeed.setText("250");
                                maximumSpeed.getOnPreferenceChangeListener().onPreferenceChange(maximumSpeed, "250");
                                appearance.setValue("auto");
                                appearance.getOnPreferenceChangeListener().onPreferenceChange(appearance, "auto");
                            }
                        })
                        .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                builder.show();
                return false;
            }
        });

        Preference aboutPreference = findPreference("about");
        if(aboutPreference == null)
            throw new RuntimeException("About preference not available");
        aboutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                TextView textView = new TextView(getContext());
                textView.setText(R.string.about_message);
                int pad = Utilities.dp_to_px(20);
                textView.setPadding(pad, pad, pad, pad);
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                        .setTitle(R.string.about)
                        .setView(textView);
                builder.show();
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
//        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        NavigationActivity act = (NavigationActivity) getActivity();
        if(act != null) {
            bindService(act.getApplicationContext());
        }
//        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
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
        CharSequence state = listPreference.getEntry();

        if(state == null || state.equals("auto")) {
            return getString(R.string.system_appearance);
        }
        else if(state.equals("dark")){
            return getString(R.string.dark_appearance);
        }
        else if(state.equals("light")){
            return getString(R.string.light_appearance);
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
