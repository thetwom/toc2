package toc2.toc2;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class NavigationActivity extends AppCompatActivity implements MetronomeFragment.OnFragmentInteractionListener {
        //implements NavigationView.OnNavigationItemSelectedListener {

    private static FragmentManager fragManager;

    private MetronomeFragment metrFrag;
    public static final String metrFragTag = "metrFrag";

    private PlayerFragment playerFrag;
    private static final String playerFragTag = "playerFrag";

    private SettingsFragment settingsFrag;
    private static final String settingsFragTag = "settingsFrag";

    private PropertiesFragment propertiesFrag;
    public static final String propertiesFragTag = "propertiesFrag";
    //private TestFragment testFrag;
    //private static final String testFragTag = "testFrag";

    final public static int SPEED_INITIAL = 120;
    // final public static int SOUND_INITIAL = R.raw.hhp_dry_a;

    final public static int SPEED_MIN= 20;
    final public static int SPEED_MAX = 220;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        int nightMode= preferences.getInt("nightmode", AppCompatDelegate.MODE_NIGHT_NO);

        AppCompatDelegate.setDefaultNightMode(nightMode);
//        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_navigation);
        //Toolbar toolbar = findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        //DrawerLayout drawer = findViewById(R.id.drawer_layout);
        //ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
        //        this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        //drawer.addDrawerListener(toggle);
        //toggle.syncState();

        //NavigationView navigationView = findViewById(R.id.nav_view);
        //navigationView.setNavigationItemSelectedListener(this);

        fragManager = getSupportFragmentManager();

        playerFrag = (PlayerFragment) fragManager.findFragmentByTag(playerFragTag);
        if(playerFrag == null) {
            playerFrag = new PlayerFragment();
            fragManager.beginTransaction().add(playerFrag, playerFragTag).commit();
        }

        metrFrag = (MetronomeFragment) fragManager.findFragmentByTag(metrFragTag);
        if(metrFrag == null) {
            metrFrag = new MetronomeFragment();
        }

//        metrFrag.setOnFragmentInteractionListener(new MetronomeFragment.OnFragmentInteractionListener() {
//            @Override
//            public void onSettingsClicked() {
//                loadFragment(propertiesFragTag);
//            }
//        });


        settingsFrag = (SettingsFragment) fragManager.findFragmentByTag(settingsFragTag);
        if(settingsFrag == null) {
            settingsFrag = new SettingsFragment();
        }

        propertiesFrag = (PropertiesFragment) fragManager.findFragmentByTag(propertiesFragTag);
        if(propertiesFrag == null) {
            propertiesFrag = new PropertiesFragment();
        }

        if(fragManager.getFragments().size() == 0)
            fragManager.beginTransaction().replace(R.id.mainframe, metrFrag, metrFragTag).commit();
        //testFrag = (TestFragment) fragManager.findFragmentByTag(testFragTag);
        //if(testFrag == null){
        //    testFrag = new TestFragment();
        //    fragManager.beginTransaction().replace(R.id.mainframe, testFrag, testFragTag).commit();
        //}
        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        //getSupportActionBar().setDisplayShowHomeEnabled(true);
        setDisplayHomeButton();
        getSupportFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                setDisplayHomeButton();
            }
        });
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if(fragment instanceof MetronomeFragment){
            MetronomeFragment frag = (MetronomeFragment) fragment;
            frag.setOnFragmentInteractionListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        getSupportActionBar().setDisplayHomeAsUpEnabled(showDisplayHomeButton);
    }

    public void loadFragment(String tag)
    {
        if(tag.equals(propertiesFragTag)){
            fragManager.beginTransaction()
                    .replace(R.id.mainframe, propertiesFrag, propertiesFragTag)
                    .addToBackStack("blub")
                    .commit();
        }
        else if(tag.equals(metrFragTag)){
            fragManager.beginTransaction()
                    .replace(R.id.mainframe, metrFrag, metrFragTag)
                    .commit();
        }
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
                    //.replace(R.id.mainframe, propertiesFrag, propertiesFragTag)
                    .addToBackStack("blub")
                    .commit();
    //        return true;

        }

        return super.onOptionsItemSelected(item);
    }

    public void setNightMode(int mode) {
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("nightmode", mode);
        editor.apply();
        recreate();
    }

    @Override
    public void onSettingsClicked() {
        loadFragment(propertiesFragTag);
    }

    //@SuppressWarnings("StatementWithEmptyBody")
    //@Override
    //public boolean onNavigationItemSelected(@NonNull MenuItem item) {
    //    // Handle navigation view item clicks here.
    //    int id = item.getItemId();


    //    if (id == R.id.metronome) {
    //        FragmentTransaction ft = fragManager.beginTransaction();
    //        ft.replace(R.id.mainframe, metrFrag, metrFragTag);
    //        ft.commit();
    //    } else if (id == R.id.testfrag) {
    //    //    FragmentTransaction ft = fragManager.beginTransaction();
    //    //    ft.replace(R.id.mainframe, testFrag, testFragTag);
    //    //    ft.commit();
    //    } else if (id == R.id.nav_share) {

    //    } else if (id == R.id.nav_send) {

    //    }

    //    DrawerLayout drawer = findViewById(R.id.drawer_layout);
    //    drawer.closeDrawer(GravityCompat.START);
    //    return true;
    //}

}
