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
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import android.support.v4.media.MediaMetadataCompat;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    // TODO: Individual toolbar for each fragment
    // TODO: new app icon
    // TODO: add settings options for speed changing sensitivity
    // TODO: allow smaller steps than 1

    private static FragmentManager fragManager;

    private static final String metrFragTag = "metrFrag";

    private PlayerFragment playerFrag = null;
    private static final String playerFragTag = "playerFrag";

    private SettingsFragment settingsFrag;
    private static final String settingsFragTag = "settingsFrag";

    private SoundChooserDialog soundChooserDialog;
    private static final String soundChooserDialogTag = "soundChooserDialog";

    private SaveDataFragment saveDataFragment;
    private static final String saveDataFragTag = "saveDataFagment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this /* Activity context */);

        String appearance = sharedPreferences.getString("appearance", getString(R.string.system_appearance_short));
        assert appearance != null;
        int nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

        if(appearance.equals(getString(R.string.dark_appearance_short))){
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        }
        else if(appearance.equals(getString(R.string.light_appearance_short))){
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        }

        AppCompatDelegate.setDefaultNightMode(nightMode);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        fragManager = getSupportFragmentManager();

        playerFrag = (PlayerFragment) fragManager.findFragmentByTag(playerFragTag);
        if(playerFrag == null) {
            playerFrag = new PlayerFragment();
            fragManager.beginTransaction().add(playerFrag, playerFragTag).commit();
        }

        MetronomeFragment metrFrag = (MetronomeFragment) fragManager.findFragmentByTag(metrFragTag);
        if(metrFrag == null) {
            metrFrag = new MetronomeFragment();
        }

        settingsFrag = (SettingsFragment) fragManager.findFragmentByTag(settingsFragTag);
        if(settingsFrag == null) {
            settingsFrag = new SettingsFragment();
        }

        soundChooserDialog = (SoundChooserDialog) fragManager.findFragmentByTag(soundChooserDialogTag);
        if(soundChooserDialog == null) {
            soundChooserDialog = new SoundChooserDialog();

            fragManager.beginTransaction()
                    .add(R.id.dialogframe, soundChooserDialog, soundChooserDialogTag)
                    .detach(soundChooserDialog)
                    .commit();
        }
        soundChooserDialog.setOnBackgroundClickedListener(new SoundChooserDialog.OnBackgroundClickedListener() {
            @Override
            public void onClick() {
                unloadSoundChooserDialog();
            }
        });

        saveDataFragment = (SaveDataFragment) fragManager.findFragmentByTag(saveDataFragTag);
        if(saveDataFragment == null) {
            saveDataFragment = new SaveDataFragment();
        }
        saveDataFragment.setOnItemClickedListener(new SavedItemDatabase.OnItemClickedListener() {
            @Override
            public void onItemClicked(SavedItemDatabase.SavedItem item, int position) {
                loadSettings(item);
                fragManager.popBackStack();
            }
        });

        if(fragManager.getFragments().size() == 0)
            fragManager.beginTransaction().replace(R.id.mainframe, metrFrag, metrFragTag).commit();

        setDisplayHomeButton();
        getSupportFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                setDisplayHomeButton();
            }
        });
    }


    @Override
    protected void onPause() {
        unloadSoundChooserDialog();
        super.onPause();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getSupportFragmentManager().popBackStack();
        return true;
    }

    private void setDisplayHomeButton() {
        boolean showDisplayHomeButton = getSupportFragmentManager().getBackStackEntryCount() > 0;
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(showDisplayHomeButton);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    //    // Handle action bar item clicks here. The action bar will
    //    // automatically handle clicks on the Home/Up button, so long
    //    // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
    //    //noinspection SimplifiableIfStatement
        if (id == R.id.action_properties) {
            fragManager.beginTransaction()
                    .replace(R.id.mainframe, settingsFrag, settingsFragTag)
                    .addToBackStack("blub")
                    .commit();
        }
        else if (id == R.id.action_load) {
            fragManager.beginTransaction()
                    .replace(R.id.mainframe, saveDataFragment, saveDataFragTag)
                    .addToBackStack("blub")
                    .commit();
        }
        else if (id == R.id.action_save) {
            saveCurrentSettings();
        }
//        else if(id == R.id.test_setting) {
//            fragManager.beginTransaction()
//                    .attach(soundChooserDialog)
//                    //.replace(R.id.dialogframe, soundChooserDialog, soundChooserDialogNewTag)
//                    .commit();
//        }

        return super.onOptionsItemSelected(item);
    }

    public void loadSoundChooserDialog(MoveableButton button, PlayerService playerService){

        soundChooserDialog.setStatus(button, playerService);
        fragManager.beginTransaction()
                .attach(soundChooserDialog)
                .commit();
    }

    private void unloadSoundChooserDialog(){
        fragManager.beginTransaction()
                .detach(soundChooserDialog)
                .commit();
    }

    private void saveCurrentSettings() {

        final EditText editText = new EditText(this);
//        editText.setPadding(dp_to_px(8), dp_to_px(8), dp_to_px(8), dp_to_px(8));
        editText.setHint(R.string.save_name);
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        dialogBuilder
                .setTitle(R.string.save_settings_dialog_title)
                .setView(editText)
                .setPositiveButton(R.string.save,
                new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SavedItemDatabase.SavedItem item = new SavedItemDatabase.SavedItem();
                    item.title = editText.getText().toString();
                    DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
                    DateFormat timeFormat = new SimpleDateFormat("hh:mm");
                    Date date = Calendar.getInstance().getTime();
                    item.date = dateFormat.format(date);
                    item.time = timeFormat.format(date);

                    item.bpm = playerFrag.getPlayerService().getSpeed();
                    item.playList = playerFrag.getPlayerService().getMetaData().getString(MediaMetadataCompat.METADATA_KEY_TITLE);

                    if(item.title.length() > 200) {
                        item.title = item.title.substring(0, 200);
                        Toast.makeText(MainActivity.this, getString(R.string.max_allowed_characters, 200), Toast.LENGTH_SHORT).show();
                    }
                    saveDataFragment.saveItem(MainActivity.this, item);
                    Toast.makeText(MainActivity.this, getString(R.string.saved_item_message, item.title), Toast.LENGTH_SHORT).show();
                }
        }).setNegativeButton(R.string.dismiss, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        dialogBuilder.show();
    }

    private void loadSettings(SavedItemDatabase.SavedItem item) {
        playerFrag.getPlayerService().changeSpeed(item.bpm);
        playerFrag.getPlayerService().setSounds(SoundProperties.parseMetaDataString(item.playList));
        Toast.makeText(this, getString(R.string.loaded_message, item.title), Toast.LENGTH_SHORT).show();
    }
}
