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
import androidx.fragment.app.FragmentActivity;
// import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A simple {@link Fragment} subclass.
 */
public class PlayerFragment extends Fragment {

    private PlayerService playerService;
    private boolean playerServiceBound = false;
    private Context playerContext = null;

    private ServiceConnection playerConnection = null;

    private float speed = InitialValues.speed;
    private AudioMixer.PlayListItem[] playList;

    public PlayerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Log.v("Metronome", "PlayerFragment:onCreate");
        setRetainInstance(true);

        FragmentActivity context = getActivity();

        if(context != null) {
            // Log.v("Metronome", "PlayerFragment:onCreate : loading preferences");
            SharedPreferences preferences = context.getPreferences(Context.MODE_PRIVATE);
            speed = preferences.getFloat("speed", InitialValues.speed);
            String soundString = preferences.getString("sound", Integer.toString(Sounds.defaultSound()));
            playList = SoundProperties.Companion.parseMetaDataString(soundString);
            if(playList.length == 0){
                initializePlayList();
            }

            bindService(context.getApplicationContext());
        }
    }

    private void bindService(final Context context) {

        if(!playerServiceBound) {
            playerConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    // Log.v("Metronome", "PlayerService:onServiceConnected");

                    if (context != null) {
                        // We've bound to LocalService, cast the IBinder and get LocalService instance
                        PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
                        playerService = binder.getService();
                        playerServiceBound = true;
                        playerContext = context;
                        playerService.changeSpeed(speed);
                        playerService.setSounds(playList);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName arg0) {
                    // Log.v("Metronome", "PlayerService:onServiceDisconnected");
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
        // Log.v("Metronome", "PlayerFragment:onResume");
        super.onResume();
    }

    @Override
    public void onStop() {
//        Log.v("Metronome", "PlayerFragment:onStop()");
        FragmentActivity context = getActivity();
        if(context != null)
        {
            // Log.v("Metronome", "PlayerFragment:onStop : saving preferences");
            SharedPreferences preferences = context.getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            //editor.putInt("speed", speed);
            if(playerServiceBound){
                speed = playerService.getPlaybackState().getPlaybackSpeed();
                playList = playerService.getSound();
            }

            editor.putFloat("speed", speed);
            String metaDataString = SoundProperties.Companion.createMetaDataString(playList);
            // Log.v("Metronome", "PlayerFragment:onStop : saving meta data: " + metaDataString);
            editor.putString("sound", metaDataString);
            editor.apply();
        }

        super.onStop();
    }

    @Override
    public void onDestroy() {
        // Log.v("Metronome", "PlayerFragment:onDestroy");
        if(playerServiceBound) {
            playerContext.unbindService(playerConnection);
            playerServiceBound = false;
        }

        super.onDestroy();
    }

    private void initializePlayList() {
        playList = new AudioMixer.PlayListItem[1];
        AudioMixer.PlayListItem s = new AudioMixer.PlayListItem(Sounds.defaultSound(), 1.0f, -1f, null);
        playList[0] = s;
    }

    PlayerService getPlayerService() {
        return playerService;
    }
}
