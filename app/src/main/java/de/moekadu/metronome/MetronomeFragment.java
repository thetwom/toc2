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
import androidx.preference.PreferenceManager;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
// import android.util.Log;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

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
    private SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;
    private float speedIncrement = Utilities.speedIncrements[InitialValues.speedIncrementIndex];

    private final Vector<Float> buttonPositions = new Vector<>();

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
        // Log.v("Metronome", "MetronomeFragment:onCreateView");
        // Inflate the layout for this fragment
        final View view = inflater.inflate(R.layout.fragment_metronome, container, false);

        speedText = view.findViewById(R.id.speedtext);

        SpeedPanel speedPanel = view.findViewById(R.id.speedpanel);

        speedIndicator = view.findViewById(R.id.speedindicator2);


        speedPanel.setOnSpeedChangedListener(new SpeedPanel.SpeedChangedListener() {
            @Override
            public void onSpeedChanged(float dSpeed) {
                if (playerServiceBound) {
                    playerService.addValueToSpeed(dSpeed);
                }
            }

            @Override
            public void onAbsoluteSpeedChanged(float newSpeed, long nextKlickTimeInMillis) {
                if (playerServiceBound) {
                    playerService.changeSpeed(newSpeed);
                    playerService.syncKlickWithUptimeMillis(nextKlickTimeInMillis);
                }
            }
        });

        playButton = view.findViewById(R.id.playbutton);

        playButton.setOnButtonClickedListener(new PlayButton.ButtonClickedListener() {

            @Override
            public void onPause() {
                // Log.v("Metronome", "playButton:onPause()");
                playerService.stopPlay();
            }

            @Override
            public void onPlay() {
                // Log.v("Metronome", "playButton:onPause()");
                playerService.startPlay();
            }
        });

        soundChooser = view.findViewById(R.id.soundchooser);

        soundChooser.setButtonClickedListener(new SoundChooser.ButtonClickedListener() {
            @Override
            public void onButtonClicked(MoveableButton button) {
                MainActivity act = (MainActivity) getActivity();
                assert act != null;
                act.loadSoundChooserDialog(button, playerService);
//                SoundChooserDialog soundChooserDialog = new SoundChooserDialog(act, button.getProperties());
//                soundChooserDialog.setNewButtonPropertiesListener(new SoundChooserDialog.NewButtonPropertiesListener() {
//                    @Override
//                    public void onNewButtonProperties(Bundle properties) {
//                        // Log.v("Metronome", "Setting new button properties ");
//                        button.setProperties(properties);
//                        setNewSound(soundChooser.getSounds());
//                    }
//                });
            }
        });

        soundChooser.setSoundChangedListener(new SoundChooser.SoundChangedListener() {
            @Override
            public void onSoundChanged(ArrayList<Bundle> sounds) {
                Log.v("Metronome", "MetronomeFragment:onSoundChanged");
                setNewSound(sounds);
            }
        });

        sharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

                if(key.equals("speedincrement")){
                    int newSpeedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex);
//                    assert newSpeedIncrement != null;
                    speedIncrement = Utilities.speedIncrements[newSpeedIncrementIndex];
                    speedPanel.setSpeedIncrement(speedIncrement);
                }
                else if(key.equals("speedsensitivity")){
                    float newSpeedSensitivity = sharedPreferences.getInt("speedsensitivity", Math.round(Utilities.sensitivity2percentage(InitialValues.speedSensitivity)));
                    speedPanel.setSensitivity(Utilities.percentage2sensitivity(newSpeedSensitivity));
                }
            }
        };
        assert getContext() != null;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        int speedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex);
        speedIncrement = Utilities.speedIncrements[speedIncrementIndex];
        speedPanel.setSpeedIncrement(speedIncrement);

        float speedSensitivity = sharedPreferences.getInt("speedsensitivity", Math.round(Utilities.sensitivity2percentage(InitialValues.speedSensitivity)));
        speedPanel.setSensitivity(Utilities.percentage2sensitivity(speedSensitivity));

        return view;
    }

    @Override
    public void onResume() {
        // Log.v("Metronome", "MetronomeFragment:onResume");
        super.onResume();

        assert getContext() != null;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        // We can bind service only after our fragment is fully inflated since while binding,
        // we call commands which require our view fully set up!
        Runnable run = new Runnable() {
            @Override
            public void run() {
                MainActivity act = (MainActivity) getActivity();
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
        // Log.v("Metronome", "MetronomeFragment:onPause");
        assert getContext() != null;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        if(playerServiceBound)
          unbindPlayerService();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        // Log.v("Metronome", "MetronomeFragment:onDestroyView");
        //if(playerServiceBound)
        // unbindPlayerService();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        // Log.v("Metronome", "MetronomeFragment:onDestroy");
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
        speedText.setText(getString(R.string.bpm, Utilities.getBpmString(state.getPlaybackSpeed(), speedIncrement)));

        if(state.getPosition() != PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN){
            float speed = state.getPlaybackSpeed();
            soundChooser.animateButton((int) state.getPosition(), Utilities.speed2dt(speed));

            speedIndicator.animate((int) state.getPosition(), speed);
        }
    }

    private void updateView(MediaMetadataCompat metadata){
        String soundString = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        // Log.v("Metronome", "MetronomeFragment : updateView : parsing metadata : " + soundString);
        ArrayList<Bundle> sounds = SoundProperties.parseMetaDataString(soundString);
        updateView(sounds);
    }

    private void updateView(List<Bundle> playList){
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
                    // Log.v("Metronome", "PlayerService:onServiceConnected");
                    // We've bound to LocalService, cast the IBinder and get LocalService instance
                    PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
                    playerService = binder.getService();
                    playerServiceBound = true;
                    playerContext = context;
                    playerService.registerMediaControllerCallback(mediaControllerCallback);

                    updateView(playerService.getPlaybackState(), false);
                    updateView(playerService.getSound());
                }

                @Override
                public void onServiceDisconnected(ComponentName arg0) {
                    // Log.v("Metronome", "PlayerService:onServiceDisconnected");
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
        Log.v("Metronome", "MetronomeFragment:setNewSounds");
        if(playerServiceBound) {
            Log.v("Metronome", "MetronomeFragment:setNewSounds: Calling playerService.setSounds");
            playerService.setSounds(sounds);
        }
        updateSpeedIndicatorMarks();
    }

    private void updateSpeedIndicatorMarks() {
         buttonPositions.clear();
        float buttonWidth = soundChooser.getButtonWidth();
        for(int ipos = 0; ipos < soundChooser.numSounds(); ++ipos){
            buttonPositions.add(soundChooser.indexToPosX(ipos)+buttonWidth);
        }
        //buttonPositions.add(soundChooser.indexToPosX(sounds.size()));
        speedIndicator.setMarks(buttonPositions);
    }
}
