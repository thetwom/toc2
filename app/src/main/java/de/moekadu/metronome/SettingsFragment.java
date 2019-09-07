/*
 * Copyright 2019 Michael Moessner
 *
 * This file is part of Metronome.
 *
 * Metronome is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Metronome is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Metronome.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.moekadu.metronome;

import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import android.text.InputType;
// import android.util.Log;
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
public class SettingsFragment extends PreferenceFragmentCompat {

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
                MainActivity act = (MainActivity) getActivity();
                appearance.setSummary(getAppearanceSummary());
                if(act != null)
                    act.recreate();
                return true;
            }
        });

        final SeekBarPreference speedIncrement = findPreference("speedincrement");
        assert speedIncrement != null;
        speedIncrement.setUpdatesContinuously(true);
        speedIncrement.setSeekBarIncrement(1);
        speedIncrement.setMin(0);
        speedIncrement.setMax(Utilities.speedIncrements.length-1);
        if(Utilities.speedIncrements.length > speedIncrement.getValue()) {
            float speedIncrementValue = Utilities.speedIncrements[speedIncrement.getValue()];
            speedIncrement.setSummary(getString(R.string.bpm, Utilities.getBpmString(speedIncrementValue, speedIncrementValue)));
        }
        speedIncrement.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int incrementIndex = (int) newValue;
                if(incrementIndex < Utilities.speedIncrements.length) {
                    float speedIncrementValue = Utilities.speedIncrements[incrementIndex];
                    speedIncrement.setSummary(getString(R.string.bpm, Utilities.getBpmString(speedIncrementValue, speedIncrementValue)));
                }
                return true;
            }
        });
        float speedIncrementValue = Utilities.speedIncrements[speedIncrement.getValue()];

        final SeekBarPreference speedSensitivity = findPreference("speedsensitivity");
        assert speedSensitivity != null;
        speedSensitivity.setUpdatesContinuously(true);
        speedSensitivity.setSummary(getString(R.string.speed_sensitivity_summary, Utilities.percentage2sensitivity(speedSensitivity.getValue())));
        speedSensitivity.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int percentage = (int) newValue;
                float sensitivity = Utilities.percentage2sensitivity(percentage);
                speedSensitivity.setSummary(getString(R.string.speed_sensitivity_summary, sensitivity));
                return true;
            }
        });

        final EditTextPreference minimumSpeed = findPreference("minimumspeed");
        assert minimumSpeed != null;
        if(minimumSpeed.getText() == null)
            minimumSpeed.setText(Float.toString(InitialValues.minimumSpeed));

        minimumSpeed.setSummary(getString(R.string.bpm, Utilities.getBpmString(Float.parseFloat(minimumSpeed.getText()), speedIncrementValue)));
        minimumSpeed.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
        });

        final EditTextPreference maximumSpeed = findPreference("maximumspeed");
        assert maximumSpeed != null;
        if(maximumSpeed.getText() == null)
            maximumSpeed.setText(Float.toString(InitialValues.maximumSpeed));
//        maximumSpeed.setDefaultValue(Float.toString(InitialValues.maximumSpeed));

        maximumSpeed.setSummary(getString(R.string.bpm, Utilities.getBpmString(Float.parseFloat(maximumSpeed.getText()), speedIncrementValue)));
        maximumSpeed.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
        });

        minimumSpeed.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
//                if(playerServiceBound) {
                    float speed = Float.parseFloat((String) newValue);
                    float maxSpeed = Float.parseFloat(maximumSpeed.getText());
                    if(speed < maxSpeed){
                        minimumSpeed.setSummary(getString(R.string.bpm, Utilities.getBpmString(speed)));
                        return true;
                    }
                    else{
                        Toast.makeText(getActivity(), "Invalid minimum speed: "+speed, Toast.LENGTH_SHORT).show();
                    }
//                }
                return false;
            }
        });

        maximumSpeed.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
//                if(playerServiceBound) {
                    float speed = Float.parseFloat((String) newValue);
                    // if(playerService.setMaximumSpeed(speed)){
                    float minSpeed = Float.parseFloat(minimumSpeed.getText());
                    if(speed > minSpeed){
                        maximumSpeed.setSummary(getString(R.string.bpm, Utilities.getBpmString(speed)));
                        return true;
                    }
                    else{
                        Toast.makeText(getActivity(), "Invalid maximum speed: "+speed, Toast.LENGTH_SHORT).show();
                    }
//                }
                return false;
            }
        });

        Preference resetSettings = findPreference("setdefault");
        if(resetSettings == null)
            throw new RuntimeException("Set default preference not available");
        resetSettings.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                assert getContext() != null;
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                        .setTitle(R.string.reset_settings_prompt)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                minimumSpeed.setText(Float.toString(InitialValues.minimumSpeed));
                                minimumSpeed.getOnPreferenceChangeListener().onPreferenceChange(minimumSpeed, Float.toString(InitialValues.minimumSpeed));
                                maximumSpeed.setText(Float.toString(InitialValues.maximumSpeed));
                                maximumSpeed.getOnPreferenceChangeListener().onPreferenceChange(maximumSpeed, Float.toString(InitialValues.maximumSpeed));
                                speedIncrement.setValue(3);
                                speedIncrement.getOnPreferenceChangeListener().onPreferenceChange(speedIncrement, 3);

                                speedSensitivity.setValue(30);
                                speedSensitivity.getOnPreferenceChangeListener().onPreferenceChange(speedSensitivity, 30);

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
                textView.setText(getString(R.string.about_message, getString(R.string.version)));
                int pad = Math.round(Utilities.dp_to_px(20));
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


    private String getAppearanceSummary() {
        final ListPreference listPreference = findPreference("appearance");
        assert listPreference != null;
        CharSequence state = listPreference.getEntry();

        if (state == null || state.equals("auto")) {
            return getString(R.string.system_appearance);
        } else if (state.equals("dark")) {
            return getString(R.string.dark_appearance);
        } else if (state.equals("light")) {
            return getString(R.string.light_appearance);
        }
        throw new RuntimeException("No summary for given appearance value");
    }
}
