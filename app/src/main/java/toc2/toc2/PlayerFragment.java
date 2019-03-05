package toc2.toc2;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A simple {@link Fragment} subclass.
 */
public class PlayerFragment extends Fragment {

    private PlayerService playerService;
    private boolean playerServiceBound = false;
    private Context appContext;

    private int speed = NavigationActivity.SPEED_INITIAL;
    //private int sound = NavigationActivity.SOUND_INITIAL;
    private int sound = 0; //NavigationActivity.SOUND_INITIAL;

    private ServiceConnection playerConnection = null;

    private MetronomeFragment metrFrag = null;

    MediaControllerCompat.Callback mediaControllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            updateMetronomeFragment(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
        }
    };
    //private NotificationReceiver receiver;

    //final public class NotificationReceiver extends BroadcastReceiver {
    //    @Override
    //    public void onReceive(Context context, Intent intent){
    //        togglePlayerIfAvailable();
    //    }
    //}

    /** Defines callbacks for service binding, passed to bindService() */
    //private final ServiceConnection playerConnection = new ServiceConnection() {
    //    @Override
    //    public void onServiceConnected(ComponentName className, IBinder service) {

            //NavigationActivity act = (NavigationActivity) getActivity();
    //        if(appContext != null) {
    //            // We've bound to LocalService, cast the IBinder and get LocalService instance
    //            PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
    //            playerService = binder.getService();
    //            playerServiceBound = true;
    //            playerService.changeSpeed(speed);
    //            final int sound = R.raw.hhp_dry_a;
    //            playerService.changeSound(sound);
    //            startPlayer();
    //        }
    //    }

    //    @Override
    //    public void onServiceDisconnected(ComponentName arg0) {
    //        playerServiceBound = false;
    //    }
    //};

    public PlayerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.v("Metronome", "PlayerFragment:onCreate");
        setRetainInstance(true);

        FragmentActivity context = getActivity();
        if(context != null){
            Log.v("Metronome", "PlayerFragment:onCreate : loading preferences");
            SharedPreferences preferences = context.getPreferences(Context.MODE_PRIVATE);
            speed = preferences.getInt("speed", NavigationActivity.SPEED_INITIAL);
            sound = preferences.getInt("sound", 0); //NavigationActivity.SOUND_INITIAL);
            //speed = preferences.getInt("speed", 99);
        }

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        //return super.onCreateView(inflater, container, savedInstanceState);
        return null;
    }

    @Override
    public void onResume() {
        Log.v("Metronome", "PlayerFragment:onResume");
        //FragmentActivity act = getActivity();

        //if (act != null) {
        //    IntentFilter filter = new IntentFilter(PlayerService.PLAYER_NOTIFICATION_TOGGLE);
        //    receiver = new NotificationReceiver();
        //    act.registerReceiver(receiver, filter);
        //}
        super.onResume();
    }

    @Override
    public void onPause() {
        //FragmentActivity act = getActivity();
        //if (act != null)
        //    getActivity().unregisterReceiver(receiver);
        super.onPause();
    }

    @Override
    public void onStop() {
        FragmentActivity context = getActivity();
        if(context != null)
        {
            Log.v("Metronome", "PlayerFragment:onStop : saving preferences");
            SharedPreferences preferences = context.getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("speed", speed);
            editor.putInt("sound", sound);
            editor.apply();
        }

        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.v("Metronome", "PlayerFragment:onDestroy");
        if(playerServiceBound) {
            appContext.unbindService(playerConnection);
            playerServiceBound = false;
        }


        super.onDestroy();
    }

    //public boolean playerServiceBound(){
    //    return playerServiceBound;
    //}

    //private PlayerService bindAndStartPlayer(Context context){
    public PlayerService startPlayer(Context context){
        //Log.v("Metronome", "PlayerFragment:bindAndStartPlayer");
        Log.v("Metronome", "PlayerFragment:startPlayer");
        appContext = context;

        if(!playerServiceBound) {

            playerConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    Log.v("Metronome", "PlayerService:onServiceConnected");
                    //NavigationActivity act = (NavigationActivity) getActivity();
                    if (appContext != null) {
                        // We've bound to LocalService, cast the IBinder and get LocalService instance
                        PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
                        playerService = binder.getService();
                        playerServiceBound = true;
                        playerService.changeSpeed(speed);
                        //final int sound = R.raw.hhp_dry_a;
                        playerService.changeSound(sound);

                        playerService.registerMediaControllerCallback(mediaControllerCallback);

                        Log.v("Metronome", "PlayerFragment:startPlayer : sending play-broadcast");
                        Intent playIntent = new Intent(PlayerService.BROADCAST_PLAYERACTION);
                        playIntent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PLAY);
                        //Intent playIntent = new Intent("blub");
                        appContext.sendBroadcast(playIntent);
                        //startPlayer();

                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName arg0) {
                    Log.v("Metronome", "PlayerService:onServiceDisconnected");
                    playerService.unregisterMediaControllerCallback(mediaControllerCallback);
                    playerServiceBound = false;
                }
            };

            Intent serviceIntent = new Intent(appContext, PlayerService.class);
            appContext.bindService(serviceIntent, playerConnection, Context.BIND_AUTO_CREATE);
        }
        else {
            Intent playIntent = new Intent(PlayerService.BROADCAST_PLAYERACTION);
            playIntent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PLAY);
            appContext.sendBroadcast(playIntent);
        }
        return playerService;
    }

    //private void startPlayer() {
    //    if (playerServiceBound) {
    //        //playerService.startPlay();
    //        Intent playIntent = new Intent(PlayerService.BROADCAST_PLAYERACTION);
    //                    playIntent.setAction(PlayerService.ACTION_PLAY);
    //                    appContext.sendBroadcast(playIntent);
    //        //updateMetronomeFragment();
    //        //metrFrag.playpauseButton.setImageResource(R.drawable.ic_pause);
    //    }
    //}

    public void stopPlayer() {
        if (playerServiceBound) {
            //playerService.stopPlay();
            Intent intent = new Intent(PlayerService.BROADCAST_PLAYERACTION);
            intent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PAUSE);
            //intent.setAction(PlayerService.ACTION_PAUSE);
            appContext.sendBroadcast(intent);
            //updateMetronomeFragment();
            //metrFrag.playpauseButton.setImageResource(R.drawable.ic_play);
        }
    }

    private void updateMetronomeFragment(PlaybackStateCompat state) {
        Log.v("Metronome", "PlayerFragment:updateMetronomeFragment");
        if(metrFrag != null){
            metrFrag.updateView(state);
        }
    }

    //private void togglePlayerIfAvailable() {
    //    if(playerServiceBound) {
    //        if(playerService.getPlayerStatus() == PlayerService.PLAYER_STARTED) {
    //            stopPlayer();
    //        }
    //        else if(playerService.getPlayerStatus() == PlayerService.PLAYER_STOPPED){
    //            startPlayer();
    //        }
    //    }
    //}

    //public void togglePlayer(Context context) {

    //    if(!playerServiceBound) {
    //        playerService = bindAndStartPlayer(context);
    //        //bindToPlayerService(); // this also starts player
    //    }
    //    else {
    //        //if(playerService.getPlayerStatus() == PlayerService.PLAYER_STARTED) {
    //            //stopPlayer();
    //            //Intent playIntent = new Intent(PlayerService.BROADCAST_PLAYERACTION);
    //            //playIntent.setAction(PlayerService.ACTION_PAUSE);
    //            //context.sendBroadcast(playIntent);
    //        //}
    //        //else if(playerService.getPlayerStatus() == PlayerService.PLAYER_STOPPED){
    //            //startPlayer();
    //            //Intent playIntent = new Intent(PlayerService.BROADCAST_PLAYERACTION);
    //            //playIntent.setAction(PlayerService.ACTION_PLAY);
    //            //context.sendBroadcast(playIntent);
    //        //}
    //    }
    //}

    public void changeSpeed(int val) {
        speed = val;
        if (playerServiceBound) {
            playerService.changeSpeed(val);
        }
    }

    public int getSpeed(){
        return speed;
    }

    public void setMetronomeFragment(MetronomeFragment metronomeFragment){
        metrFrag = metronomeFragment;
        if(playerServiceBound)
            updateMetronomeFragment(playerService.getPlaybackState());
        //updateMetronomeFragment();
    }

    public void changeSound(int soundid) {
        sound = soundid;
        if (playerServiceBound) {
            playerService.changeSound(sound);
        }
    }

    public int getSound() {
        return sound;
    }
    //@Override
    //public View onCreateView(LayoutInflater inflater, ViewGroup container,
    //                         Bundle savedInstanceState) {
    //    TextView textView = new TextView(getActivity());
    //    textView.setText(R.string.hello_blank_fragment);
    //    return textView;
    //}

}
