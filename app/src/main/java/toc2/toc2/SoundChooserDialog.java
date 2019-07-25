package toc2.toc2;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

class SoundChooserDialog extends Fragment implements View.OnClickListener {

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
        final View view =inflater.inflate(R.layout.sound_chooser_dialog_new, container, false);

        Log.v("Metronome", "SoundChooserDialog.onCreateView");
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
                    Bundle bundle = new Bundle();
                    bundle.putInt("soundid", soundID);
                    activeButton.setProperties(bundle, false);
                    soundChooserCircle.setActiveSoundID(soundID);
                }
                if(playerService != null) {
                    playerService.playSpecificSound(soundID, volumeControl.getVolume());
                }
            }
        });

        volumeControl = view.findViewById(R.id.volumeControl);

        volumeControl.setOnVolumeChangedListener(new VolumeControl.OnVolumeChangedListener() {
            @Override
            public void onVolumeChanged(float volume) {
                if(activeButton != null) {
                    Bundle bundle = new Bundle();
                    bundle.putFloat("volume", volume);
                    activeButton.setProperties(bundle, false);
                }
                if(playerService != null) {
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
            soundChooserCircle.setActiveSoundID(button.getProperties().getInt("soundid", 0));

        if(volumeControl != null)
            volumeControl.setState(button.getProperties().getFloat("volume"));

    }
}
