package toc2.toc2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

/**

 */
public class MetronomeFragment extends Fragment {

    private TextView speedText;
    private SpeedPanel speedPanel;
    private SoundChooser soundChooser;
    private boolean playerServiceBound = false;
    private ServiceConnection playerConnection = null;
    private PlayerService playerService;
    private Context playerContext;


    private final MediaControllerCompat.Callback mediaControllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            updateView(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            updateView(metadata);
        }
    };

    public MetronomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.v("Metronome", "MetronomeFragment:onCreateView");
        // Inflate the layout for this fragment
        final View view = inflater.inflate(R.layout.fragment_metronome, container, false);

        speedText = view.findViewById(R.id.speedtext);

        speedPanel = view.findViewById(R.id.speedpanel);

        speedPanel.setOnSpeedChangedListener(new SpeedPanel.SpeedChangedListener(){
            @Override
            public void onSpeedChanged(int speedChange) {
                //int currentSpeed = getCurrentSpeed();
                //int speed = currentSpeed + speed_change;
                //speed = Math.max(speed, NavigationActivity.SPEED_MIN);
                //speed = Math.min(speed, NavigationActivity.SPEED_MAX);
                if (playerServiceBound) {
                    playerService.addValueToSpeed(speedChange);
                }
                //PlayerService.sendChangeSpeedIntent(act, speed);
                //speedText.setText(getString(R.string.bpm,speed));
            }
        });

        speedPanel.setOnButtonClickedListener(new SpeedPanel.ButtonClickedListener() {

            @Override
            public void onPause() {
                Log.v("Metronome", "speedPanel:onPause()");
                playerService.stopPlay();
            }

            @Override
            public void onPlay() {
                Log.v("Metronome", "speedPanel:onPause()");
                playerService.startPlay();
            }
        });

        soundChooser = view.findViewById(R.id.soundchooser);

        soundChooser.setButtonClickedListener(new SoundChooser.ButtonClickedListener() {
            @Override
            public void onButtonClicked(final MoveableButton button) {
                NavigationActivity act = (NavigationActivity) getActivity();
                SoundChooserDialog soundChooserDialog = new SoundChooserDialog(act, button.getProperties());
                soundChooserDialog.setNewButtonPropertiesListener(new SoundChooserDialog.NewButtonPropertiesListener() {
                    @Override
                    public void onNewButtonProperties(Bundle properties) {
                        Log.v("Metronome", "Setting new button properties ");
                        button.setProperties(properties);
                        setNewSound(soundChooser.getSounds());
                    }
                });
            }
        });

        soundChooser.setSoundChangedListener(new SoundChooser.SoundChangedListener() {
            @Override
            public void onSoundChanged(ArrayList<Bundle> sounds) {
                setNewSound(sounds);
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        Log.v("Metronome", "MetronomeFragment:onResume");
        super.onResume();

        // We can bind service only after our fragment is fully inflated since while binding,
        // we call commands which require our view fully set up!
        Runnable run = new Runnable() {
            @Override
            public void run() {
                NavigationActivity act = (NavigationActivity) getActivity();
                if(act != null) {
                    bindService(act.getApplicationContext());
                }
            }
        };

        if(getView() != null) {
            getView().post(run);
        }
    }

    @Override
    public void onPause() {
        Log.v("Metronome", "MetronomeFragment:onPause");
        if(playerServiceBound)
          unbindPlayerService();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        Log.v("Metronome", "MetronomeFragment:onDestroyView");
        //if(playerServiceBound)
        // unbindPlayerService();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.v("Metronome", "MetronomeFragment:onDestroy");
        // if(playerServiceBound)
        //  unbindPlayerService();
        super.onDestroy();
    }

    private void unbindPlayerService() {
        playerService.unregisterMediaControllerCallback(mediaControllerCallback);
        playerServiceBound = false;
        playerContext.unbindService(playerConnection);
    }

    public void updateView(PlaybackStateCompat state){
        if(state.getState() == PlaybackStateCompat.STATE_PLAYING){
            speedPanel.changeStatus(SpeedPanel.STATUS_PLAYING);
        }
        else if(state.getState() == PlaybackStateCompat.STATE_PAUSED){
            speedPanel.changeStatus(SpeedPanel.STATUS_PAUSED);
        }
        speedText.setText(getString(R.string.bpm, Math.round(state.getPlaybackSpeed())));
        if(state.getPosition() != PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN){
            soundChooser.animateButton((int) state.getPosition());
        }
    }

    public void updateView(MediaMetadataCompat metadata){
        String soundString = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        ArrayList<Bundle> sounds = SoundProperties.parseMetaDataString(soundString);
        soundChooser.setSounds(sounds);
    }

    private void bindService(final Context context) {
        if(context == null)
        {
            // we should throw some error here
            return;
        }

        if(!playerServiceBound) {
            playerConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    Log.v("Metronome", "PlayerService:onServiceConnected");
                    // We've bound to LocalService, cast the IBinder and get LocalService instance
                    PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
                    playerService = binder.getService();
                    playerServiceBound = true;
                    playerContext = context;
                    playerService.registerMediaControllerCallback(mediaControllerCallback);
                    updateView(playerService.getPlaybackState());
                    if(playerService.getMetaData() != null)
                        updateView(playerService.getMetaData());
                }

                @Override
                public void onServiceDisconnected(ComponentName arg0) {
                    Log.v("Metronome", "PlayerService:onServiceDisconnected");
                    playerService.unregisterMediaControllerCallback(mediaControllerCallback);
                    playerServiceBound = false;
                    playerContext = null;
                }
            };

            Intent serviceIntent = new Intent(context, PlayerService.class);
            context.bindService(serviceIntent, playerConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void setNewSound(ArrayList<Bundle> sounds) {
        if(playerServiceBound) {
            playerService.setSounds(sounds);
        }
    }

}
