package toc2.toc2;

import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

public class NavigationActivity extends AppCompatActivity {
        //implements NavigationView.OnNavigationItemSelectedListener {

    private static FragmentManager fragManager;

    private MetronomeFragment metrFrag;
    private static final String metrFragTag = "metrFrag";

    public PlayerFragment playerFrag;
    private static final String playerFragTag = "playerFrag";

    //private TestFragment testFrag;
    //private static final String testFragTag = "testFrag";

    final public static int SPEED_INITIAL = 120;
    // final public static int SOUND_INITIAL = R.raw.hhp_dry_a;

    final public static int SPEED_MIN= 20;
    final public static int SPEED_MAX = 220;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        fragManager.beginTransaction().replace(R.id.mainframe, metrFrag, metrFragTag).commit();


        //testFrag = (TestFragment) fragManager.findFragmentByTag(testFragTag);
        //if(testFrag == null){
        //    testFrag = new TestFragment();
        //    fragManager.beginTransaction().replace(R.id.mainframe, testFrag, testFragTag).commit();
        //}
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    //@Override
    //public void onBackPressed() {
    //    DrawerLayout drawer = findViewById(R.id.drawer_layout);
    //    if (drawer.isDrawerOpen(GravityCompat.START)) {
    //        drawer.closeDrawer(GravityCompat.START);
    //    } else {
    //        super.onBackPressed();
    //    }
    //}

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
    //    int id = item.getItemId();

    //    //noinspection SimplifiableIfStatement
    //    if (id == R.id.action_settings) {
    //        return true;
    //    }

        return super.onOptionsItemSelected(item);
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
