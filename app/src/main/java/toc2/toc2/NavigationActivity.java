package toc2.toc2;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Objects;

public class NavigationActivity extends AppCompatActivity { // implements MetronomeFragment.OnFragmentInteractionListener {
        //implements NavigationView.OnNavigationItemSelectedListener {

    private static FragmentManager fragManager;

    private static final String metrFragTag = "metrFrag";

    private static final String playerFragTag = "playerFrag";

    private SettingsFragment settingsFrag;
    private static final String settingsFragTag = "settingsFrag";

    private SoundChooserDialogNew soundChooserDialogNew;
    private static final String soundChooserDialogNewTag = "soundChooserDialog";

    final public static int SPEED_INITIAL = 120;
    // final public static int SOUND_INITIAL = R.raw.hhp_dry_a;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this /* Activity context */);

        boolean manualThemeYesNo = sharedPreferences.getBoolean("manualtheme", true);
        boolean nightModeYesNo = sharedPreferences.getBoolean("darktheme", false);
        int nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if(manualThemeYesNo) {
            if (nightModeYesNo)
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
            else
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);

        setContentView(R.layout.activity_navigation);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        fragManager = getSupportFragmentManager();

        PlayerFragment playerFrag = (PlayerFragment) fragManager.findFragmentByTag(playerFragTag);
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

        soundChooserDialogNew = (SoundChooserDialogNew) fragManager.findFragmentByTag(soundChooserDialogNewTag);
        if(soundChooserDialogNew == null) {
            soundChooserDialogNew = new SoundChooserDialogNew();

            fragManager.beginTransaction()
                    .add(R.id.dialogframe, soundChooserDialogNew, soundChooserDialogNewTag)
                    .detach(soundChooserDialogNew)
                    .commit();
        }
        soundChooserDialogNew.setOnBackgroundClickedListener(new SoundChooserDialogNew.OnBackgroundClickedListener() {
            @Override
            public void onClick() {
                unloadSoundChooserDialog();
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

//    @Override
//    public void onAttachFragment(Fragment fragment) {
//        if(fragment instanceof MetronomeFragment){
//            MetronomeFragment frag = (MetronomeFragment) fragment;
//            frag.setOnFragmentInteractionListener(this);
//        }
//    }

    @Override
    protected void onPause() {
        unloadSoundChooserDialog();
        super.onPause();
    }

    //    @Override
//    public void onBackPressed() {
//        DrawerLayout drawer = findViewById(R.id.drawer_layout);
//        if (drawer.isDrawerOpen(GravityCompat.START)) {
//            drawer.closeDrawer(GravityCompat.START);
//        } else {
//            super.onBackPressed();
//        }
//    }


    @Override
    protected void onDestroy() {
        Log.v("Metronome", "NavigationActivity:onDestroy");
//        if(isFinishing()){
//            Log.v("Metronome", "NavigationActivity:onDestroy:isFinishing");
//            if(metrFrag != null) {
//                fragManager.beginTransaction().remove(metrFrag).commit();
//                metrFrag = null;
//            }
//            if(playerFrag != null) {
//                fragManager.beginTransaction().remove(playerFrag).commit();
//                playerFrag = null;
//            }
//        }
        super.onDestroy();
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
        getMenuInflater().inflate(R.menu.navigation, menu);
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
//        else if(id == R.id.test_setting) {
//            fragManager.beginTransaction()
//                    .attach(soundChooserDialogNew)
//                    //.replace(R.id.dialogframe, soundChooserDialogNew, soundChooserDialogNewTag)
//                    .commit();
//        }

        return super.onOptionsItemSelected(item);
    }

    public void loadSoundChooserDialog(MoveableButton button, PlayerService playerService){

        soundChooserDialogNew.setStatus(button, playerService);
        fragManager.beginTransaction()
                .attach(soundChooserDialogNew)
                .commit();
    }

    private void unloadSoundChooserDialog(){
        fragManager.beginTransaction()
                .detach(soundChooserDialogNew)
                .commit();
    }

}
