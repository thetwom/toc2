package toc2.toc2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

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
    private PlayButton playButton;
    private SpeedIndicator speedIndicator;
    private SoundChooser soundChooser;
    private boolean playerServiceBound = false;
    private ServiceConnection playerConnection = null;
    private PlayerService playerService;
    private Context playerContext;


    private final MediaControllerCompat.Callback mediaControllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            updateView(state, true);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            updateView(metadata);
        }
    };

//    public interface OnFragmentInteractionListener {
//        void onSettingsClicked();
//    }
//
//    private OnFragmentInteractionListener onFragmentInteractionListener = null;


    public MetronomeFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

//    @Override
//    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//        super.onCreateOptionsMenu(menu, inflater);
//        inflater.inflate(R.menu.metronome_menu, menu);
//    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.v("Metronome", "MetronomeFragment:onCreateView");
        // Inflate the layout for this fragment
        final View view = inflater.inflate(R.layout.fragment_metronome, container, false);

        speedText = view.findViewById(R.id.speedtext);

        SpeedPanel speedPanel = view.findViewById(R.id.speedpanel);

        speedIndicator = view.findViewById(R.id.speedindicator);

        speedPanel.setOnSpeedChangedListener(speedChange -> {
            if (playerServiceBound) {
                playerService.addValueToSpeed(speedChange);
            }
        });

        playButton = view.findViewById(R.id.playbutton);

        playButton.setOnButtonClickedListener(new PlayButton.ButtonClickedListener() {

            @Override
            public void onPause() {
                Log.v("Metronome", "playButton:onPause()");
                playerService.stopPlay();
            }

            @Override
            public void onPlay() {
                Log.v("Metronome", "playButton:onPause()");
                playerService.startPlay();
            }
        });

        soundChooser = view.findViewById(R.id.soundchooser);

        soundChooser.setButtonClickedListener(button -> {
            NavigationActivity act = (NavigationActivity) getActivity();
            assert act != null;
            act.loadSoundChooserDialog(button, playerService);
//                SoundChooserDialog soundChooserDialog = new SoundChooserDialog(act, button.getProperties());
//                soundChooserDialog.setNewButtonPropertiesListener(new SoundChooserDialog.NewButtonPropertiesListener() {
//                    @Override
//                    public void onNewButtonProperties(Bundle properties) {
//                        Log.v("Metronome", "Setting new button properties ");
//                        button.setProperties(properties);
//                        setNewSound(soundChooser.getSounds());
//                    }
//                });
        });

        soundChooser.setSoundChangedListener(sounds -> setNewSound(sounds));

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

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        int id = item.getItemId();
//        if(id == R.id.action_settings){
//            if(onFragmentInteractionListener != null) {
//                onFragmentInteractionListener.onSettingsClicked();
//                return true;
//            }
//        }
//        return super.onOptionsItemSelected(item);
//    }

    private void unbindPlayerService() {
        playerService.unregisterMediaControllerCallback(mediaControllerCallback);
        playerServiceBound = false;
        playerContext.unbindService(playerConnection);
    }

    private void updateView(PlaybackStateCompat state, boolean animate){
        if(state.getState() == PlaybackStateCompat.STATE_PLAYING){
            playButton.changeStatus(PlayButton.STATUS_PLAYING, animate);
        }
        else if(state.getState() == PlaybackStateCompat.STATE_PAUSED){
            speedIndicator.stopPlay();
            playButton.changeStatus(PlayButton.STATUS_PAUSED, animate);
        }
        speedText.setText(getString(R.string.bpm, Math.round(state.getPlaybackSpeed())));
        speedIndicator.setSpeed(state.getPlaybackSpeed());
        if(state.getPosition() != PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN){
            soundChooser.animateButton((int) state.getPosition());
            speedIndicator.animatePosition();
        }
    }

    private void updateView(MediaMetadataCompat metadata){
        String soundString = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        Log.v("Metronome", "MetronomeFragment : updateView : parsing metadata : " + soundString);
        ArrayList<Bundle> sounds = SoundProperties.parseMetaDataString(soundString);
        updateView(sounds);
    }

    private void updateView(ArrayList<Bundle> playList){
        soundChooser.setSounds(playList);
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
                    playerService.setMaximumSpeed(getMaximumSpeed());
                    playerService.setMinimumSpeed(getMinimumSpeed());
                    playerService.registerMediaControllerCallback(mediaControllerCallback);
                    updateView(playerService.getPlaybackState(), false);
                    updateView(playerService.getSound());
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
//
//    public void setOnFragmentInteractionListener(OnFragmentInteractionListener onFragmentInteractionListener){
//        this.onFragmentInteractionListener = onFragmentInteractionListener;
//    }


    private int getMaximumSpeed() {
        final int maximumSpeedDefault = 250;

        FragmentActivity act = getActivity();
        if(act == null)
            throw new RuntimeException("No activity available");

        SharedPreferences sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(act);

        String speedString = sharedPreferences.getString("maximumspeed", Integer.toString(maximumSpeedDefault));
        if(speedString == null)
            return maximumSpeedDefault;

        try {
            int speed = Integer.parseInt(speedString);
            if (speed < 1)
                return maximumSpeedDefault;
            else
                return speed;
        }
        catch(java.lang.NumberFormatException ex) {
            return maximumSpeedDefault;
        }
    }

    private int getMinimumSpeed() {
        final int minimumSpeedDefault = 20;

        FragmentActivity act = getActivity();
        if(act == null)
            throw new RuntimeException("No activity available");

        SharedPreferences sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(act);

        String speedString = sharedPreferences.getString("minimumspeed", Integer.toString(minimumSpeedDefault));
        if(speedString == null)
            return minimumSpeedDefault;

        try {
            int speed = Integer.parseInt(speedString);
            if (speed < 1)
                return minimumSpeedDefault;
            else
                return speed;
        }
        catch(java.lang.NumberFormatException ex) {
            return minimumSpeedDefault;
        }
    }
}
