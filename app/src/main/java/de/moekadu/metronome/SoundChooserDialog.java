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

import android.os.Bundle;
// import android.util.Log;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

public class SoundChooserDialog extends Fragment implements View.OnClickListener {

    private MoveableButton activeButton = null;
    private PlayerService playerService = null;

    private SoundChooserCircle soundChooserCircle = null;

    private VolumeControl volumeControl = null;

    interface OnBackgroundClickedListener{
        void onClick();
    }


    private OnBackgroundClickedListener onBackgroundClickedListener = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view =inflater.inflate(R.layout.sound_chooser_dialog, container, false);

        // Log.v("Metronome", "SoundChooserDialog.onCreateView");
        TextView backgroundView = view.findViewById(R.id.soundchooserbackground);
        backgroundView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        MaterialButton okButton = view.findViewById(R.id.okbutton);
        okButton.setOnClickListener(this);

        soundChooserCircle = view.findViewById(R.id.soundchoosercircle);

        soundChooserCircle.setOnSoundIDChangedListener(new SoundChooserCircle.OnSoundIDChangedListener() {
            @Override
            public void onSoundIDChanged(int soundID) {
                if(activeButton != null) {
                    AudioMixer.PlayListItem properties = activeButton.getProperties();
                    properties.setTrackIndex(soundID);
                    activeButton.setProperties(properties, false);
                    soundChooserCircle.setActiveSoundID(soundID);
                }
                if(playerService != null && playerService.getState() != PlaybackStateCompat.STATE_PLAYING) {
                    playerService.playSpecificSound(soundID, volumeControl.getVolume());
                }
            }
        });

        volumeControl = view.findViewById(R.id.volumeControl);

        volumeControl.setOnVolumeChangedListener(new VolumeControl.OnVolumeChangedListener() {
            @Override
            public void onVolumeChanged(float volume) {
                if(activeButton != null) {
                    AudioMixer.PlayListItem properties = activeButton.getProperties();
                    properties.setVolume(volume);
                    activeButton.setProperties(properties, false);
                }
                if(playerService != null && playerService.getState() != PlaybackStateCompat.STATE_PLAYING) {
                    playerService.playSpecificSound(soundChooserCircle.getCurrentSoundID(), volume);
                }
            }
        });
        setStatus(activeButton, playerService);
        return view;
    }

    @Override
    public void onClick(View v) {
        if(onBackgroundClickedListener != null)
            onBackgroundClickedListener.onClick();
    }

    void setOnBackgroundClickedListener(OnBackgroundClickedListener listener){
        onBackgroundClickedListener = listener;
    }

    void setStatus(MoveableButton button, PlayerService playerService) {
        this.playerService = playerService;
        activeButton = button;

        if(activeButton == null)
            return;

        if(soundChooserCircle != null)
            soundChooserCircle.setActiveSoundID(button.getProperties().getTrackIndex());

        if(volumeControl != null)
            volumeControl.setState(button.getProperties().getVolume());
    }
}
