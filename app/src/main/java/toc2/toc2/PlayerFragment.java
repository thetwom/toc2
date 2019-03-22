package toc2.toc2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
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
    private Context appContext = null;
    private Context playerContext = null;

    private ServiceConnection playerConnection = null;

    int speed = NavigationActivity.SPEED_INITIAL;
    int sound[] = {0};

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
            String soundString = preferences.getString("sound", "0");
            sound = MetaDataHelper.parseMetaDataString(soundString);
        }
        bindService(context.getApplicationContext());
    }

    private void bindService(final Context context) {

        if(!playerServiceBound) {
            playerConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    Log.v("Metronome", "PlayerService:onServiceConnected");

                    if (context != null) {
                        // We've bound to LocalService, cast the IBinder and get LocalService instance
                        PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
                        playerService = binder.getService();
                        playerServiceBound = true;
                        playerContext = context;
                        playerService.changeSpeed(speed);
                        playerService.changeSound(sound);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName arg0) {
                    Log.v("Metronome", "PlayerService:onServiceDisconnected");
                    playerServiceBound = false;
                    playerContext = null;
                }
            };

            Intent serviceIntent = new Intent(context, PlayerService.class);
            context.bindService(serviceIntent, playerConnection, Context.BIND_AUTO_CREATE);
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
        super.onResume();
    }

    @Override
    public void onPause() {
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
            //editor.putInt("speed", speed);
            if(playerServiceBound){
                speed = Math.round(playerService.getPlaybackState().getPlaybackSpeed());
                sound = playerService.getSound();
            }

            editor.putInt("speed", speed);
            editor.putString("sound", MetaDataHelper.createMetaDataString(sound));
            editor.apply();
        }

        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.v("Metronome", "PlayerFragment:onDestroy");
        if(playerServiceBound) {
            playerContext.unbindService(playerConnection);
            playerServiceBound = false;
        }

        super.onDestroy();
    }
}
