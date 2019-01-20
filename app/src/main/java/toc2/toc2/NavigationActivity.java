package toc2.toc2;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;

public class NavigationActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static FragmentManager fragManager;

    private static MetronomeFragment metrFrag;
    private static final String metrFragTag = "metrFrag";

    public static PlayerFragment playerFrag;
    private static final String playerFragTag = "playerFrag";

    private static SoundGeneratorFragment soundGenFrag;
    private static final String soundGenFragTag = "soundGenFrag";

    //private static PlayerService playerService;
    //public static boolean playerServiceBound = false;

    final public static int SPEED_INITIAL = 120;
    public static int speed = SPEED_INITIAL;
    public static final String speedTag = "speed";

    final public static int SPEED_MIN= 20;
    final public static int SPEED_MAX = 220;

    private static ImageButton playpauseButton;

    static final public class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent){
            playerFrag.togglePlayerIfAvailable();
            //if(playerServiceBound)
            //    playerService.togglePlay();
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    //private final ServiceConnection playerConnection = new ServiceConnection() {
    //    @Override
    //    public void onServiceConnected(ComponentName className, IBinder service) {
    //        // We've bound to LocalService, cast the IBinder and get LocalService instance
    //        PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
    //        playerService = binder.getService();
    //        playerServiceBound = true;
    //        playerService.changeSpeed(speed);
    //        final int sound = R.raw.hhp_dry_a;
    //        playerService.changeSound(sound);
    //        startPlayer();
    //        //playerService.startPlay();
    //    }

    //    @Override
    //    public void onServiceDisconnected(ComponentName arg0) {
    //        playerServiceBound = false;
    //    }
    //};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        //FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        //fab.setOnClickListener(new View.OnClickListener() {
        //    @Override
        //    public void onClick(View view) {
        //        Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
        //                .setAction("Action", null).show();
        //    }
        //});

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        fragManager = getSupportFragmentManager();

        playerFrag = (PlayerFragment) fragManager.findFragmentByTag(playerFragTag);

        if(playerFrag == null) {
            playerFrag = new PlayerFragment();
            getSupportFragmentManager().beginTransaction().add(playerFrag, playerFragTag).commit();
        }

        metrFrag = (MetronomeFragment) fragManager.findFragmentByTag(metrFragTag);
        if(metrFrag == null){
            metrFrag = new MetronomeFragment();
            fragManager.beginTransaction().replace(R.id.mainframe, metrFrag, metrFragTag).commit();
        }

        //if (savedInstanceState == null) {
        //    metrFrag = new MetronomeFragment();
        //    fragManager.beginTransaction().replace(R.id.mainframe, metrFrag, metrFragTag).commit();
        //    //soundGenFrag = new SoundGeneratorFragment();
        //}
        //else {
        //    //speed = savedInstanceState.getInt(speedTag);

        //   metrFrag = (MetronomeFragment) fragManager.findFragmentByTag(metrFragTag);
        //}

        //playpauseButton = findViewById(R.id.toggleplay);
    }

    @Override
    protected void onResume() {
        super.onResume();
        playerFrag.setMetronomeFragment(metrFrag);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(speedTag, speed);
    }

    @Override
    protected void onDestroy() {
        //if(playerServiceBound) {
        //    unbindService(playerConnection);
        //    playerServiceBound = false;
        //}
        //this.unregisterReceiver(klickUnbindRecv);
        super.onDestroy();
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();


        if (id == R.id.metronome) {
            FragmentTransaction ft = fragManager.beginTransaction();
            ft.replace(R.id.mainframe, metrFrag, metrFragTag);
            ft.commit();
        } else if (id == R.id.soundgen) {
            //FragmentTransaction ft = fragManager.beginTransaction();
            //ft.replace(R.id.mainframe, soundGenFrag, soundGenFragTag);
            //ft.commit();
        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    //private void bindToPlayerService(){
    //    if(!playerServiceBound) {
    //        Intent serviceIntent = new Intent(this, PlayerService.class);
    //        this.bindService(serviceIntent, playerConnection, Context.BIND_AUTO_CREATE);
    //    }
    //}

    //public void startPlayer() {

    //    if(!playerServiceBound){
    //        bindToPlayerService(); // this also starts player
    //    }
    //    else{
    //        startPlayerIfBound();
    //    }
    //}

    //static public void startPlayer() {
    //    if (playerFrag.playerServiceBound()) {
    //        playerService.startPlay();
    //        metrFrag.playpauseButton.setImageResource(R.drawable.ic_pause);
    //    }
    //}

    //static public void stopPlayer() {
    //    if (playerFrag.playerServiceBound()) {
    //        playerService.stopPlay();
    //        metrFrag.playpauseButton.setImageResource(R.drawable.ic_play);
    //    }
    //}

    //static public void togglePlayerIfAvailable() {
    //    if(playerFrag.playerServiceBound()) {
    //        if(playerService.getPlayerStatus() == playerService.PLAYER_STARTED) {
    //            stopPlayer();
    //        }
    //        else if(playerService.getPlayerStatus() == playerService.PLAYER_STOPPED){
    //            startPlayer();
    //        }
    //    }
    //}

    //public void togglePlayer() {

    //    if(!playerFrag.playerServiceBound()) {
    //        playerService = playerFrag.bindAndStartPlayer(this);
    //        //bindToPlayerService(); // this also starts player
    //    }
    //    else {
    //        if(playerService.getPlayerStatus() == playerService.PLAYER_STARTED) {
    //            stopPlayer();
    //        }
    //        else if(playerService.getPlayerStatus() == playerService.PLAYER_STOPPED){
    //            startPlayer();
    //        }
    //    }
    //}

    //public void changeSpeed(int val) {
    //    speed = val;
    //    if (playerFrag.playerServiceBound()) {
    //        playerService.changeSpeed(val);
    //    }
    //}
}
