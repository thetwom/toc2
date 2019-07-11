package toc2.toc2;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

class SoundChooserDialogNew extends Fragment implements View.OnClickListener {

    private MoveableButton activeButton = null;
    private PlayerService playerService = null;

    private SoundChooserCircle soundChooserCircle = null;

    private VolumeControl volumeControl = null;
//    private int normalButtonColor = Color.BLACK;
//    private int highlightButtonColor = Color.GRAY;

    interface OnBackgroundClickedListener{
        void onClick();
    }


    private OnBackgroundClickedListener onBackgroundClickedListener = null;

//    @Override
//    public void onInflate(@NonNull Context context, @NonNull AttributeSet attrs, @Nullable Bundle savedInstanceState) {
//        super.onInflate(context, attrs, savedInstanceState);
//        readAttributes(attrs);
//    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view =inflater.inflate(R.layout.sound_chooser_dialog_new, container, false);

        Log.v("Metronome", "SoundChooserDialogNew.onCreateView");
//        TextView backgroundView = view.findViewById(R.id.soundchooserbackground);
//        backgroundView.setOnClickListener(this);

        MaterialButton okButton = view.findViewById(R.id.okbutton);
        okButton.setOnClickListener(this);

        soundChooserCircle = view.findViewById(R.id.soundchoosercircle);

        soundChooserCircle.setOnSoundIDChangedListener(new SoundChooserCircle.OnSoundIDChangedListener() {
            @Override
            public void onSoundIDChanged(int soundID) {
                if(activeButton != null) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("soundid", soundID);
                    activeButton.setProperties(bundle);
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
                    activeButton.setProperties(bundle);
                }
                if(playerService != null) {
                    playerService.playSpecificSound(soundChooserCircle.getCurrentSoundID(), volume);
                }
            }
        });
        setStatus(activeButton, playerService);
        return view;
    }

//    @Override
//    public void onResume() {
//        super.onResume();
//
//        getView().post(new Runnable() {
//            @Override
//            public void run() {
//                init();
//            }
//        });
//    }

//    private void init() {
//        FragmentActivity act = getActivity();
//        View view = getView();
//
//        final int buttonSize = dp_to_px(100);
//        float cx = view.getWidth() / 2.0f;
//        float cy = view.getHeight() / 2.0f;
//
//        MoveableButton button = new MoveableButton(act, normalButtonColor, highlightButtonColor);
//
//        Bundle properties;
//        properties = new Bundle();
//        properties.putFloat("volume", 1.0f);
//        properties.putInt("soundid", 0);
//        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
//        button.setProperties(properties);
//        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(buttonSize, buttonSize);
//        button.setLayoutParams(params);
//        button.setElevation(24);
//        ConstraintLayout layout = view.findViewById(R.id.soundchooserndialogewlayout);
//
//        layout.addView(button);
//
//        button.setTranslationX(cx-buttonSize/2.0f);
//        button.setTranslationY(cy-buttonSize/2.0f);
//    }


//    private void readAttributes(AttributeSet attrs){
//        if(attrs == null)
//            return;
//
//        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.SoundChooserDialogNew);
//        highlightButtonColor = ta.getColor(R.styleable.SoundChooserDialogNew_highlightColor, Color.BLUE);
//        normalButtonColor = ta.getColor(R.styleable.SoundChooserDialogNew_normalColor, Color.BLACK);
//
//        ta.recycle();
//    }



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

    private int dp_to_px(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }
}
