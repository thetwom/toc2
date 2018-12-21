package toc2.toc2;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

public class NavigationActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static FragmentManager fragManager;
    private static MetronomeFragment metrFrag;
    private static SoundGeneratorFragment soundGenFrag;

    public static PlayerService playerService;
    public static boolean playerServiceBound = false;

    final public static int INITIAL_SPEED = 120;
    public static int speed = INITIAL_SPEED;
    //private Bundle sound = SoundFactory.getGaussDerivativePackage(0.001);
    private Bundle sound = SoundFactory.getSkewedAndSinePackage(800.0, 0.15);

    static public class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent){
            if(playerServiceBound)
                playerService.togglePlay();
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection playerConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
            playerService = binder.getService();
            playerServiceBound = true;
            playerService.changeSpeed(speed);
            playerService.changeSound(sound);
            playerService.startPlay();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            playerServiceBound = false;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        //fab.setOnClickListener(new View.OnClickListener() {
        //    @Override
        //    public void onClick(View view) {
        //        Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
        //                .setAction("Action", null).show();
        //    }
        //});

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        if (metrFrag == null)
            metrFrag = new MetronomeFragment();
        if (soundGenFrag == null)
            soundGenFrag = new SoundGeneratorFragment();

        fragManager = getSupportFragmentManager();

        FragmentTransaction ft = fragManager.beginTransaction();
        ft.add(R.id.mainframe, metrFrag);
        ft.commit();
    }

    @Override
    protected void onDestroy() {
        if(playerServiceBound) {
            unbindService(playerConnection);
            playerServiceBound = false;
        }
        //this.unregisterReceiver(klickUnbindRecv);
        super.onDestroy();
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
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
            ft.replace(R.id.mainframe, metrFrag);
            ft.commit();
        } else if (id == R.id.soundgen) {
            FragmentTransaction ft = fragManager.beginTransaction();
            ft.replace(R.id.mainframe, soundGenFrag);
            ft.commit();
        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void bindToPlayerService(){
        if(!playerServiceBound) {
            Intent serviceIntent = new Intent(this, PlayerService.class);
            this.bindService(serviceIntent, playerConnection, Context.BIND_AUTO_CREATE);
        }
    }

    public void startPlayer() {

        if(!playerServiceBound){
            bindToPlayerService(); // this also starts player
        }
        else{
            playerService.startPlay();
        }
    }

    public void stopPlayer() {

        if (playerServiceBound) {
            playerService.stopPlay();
        }
    }

    public void changeSpeed(int val) {
        speed = val;
        if (playerServiceBound) {
            playerService.changeSpeed(val);
        }
    }
}
