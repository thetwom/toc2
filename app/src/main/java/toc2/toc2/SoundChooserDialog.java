package toc2.toc2;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;

import java.util.ArrayList;

public class SoundChooserDialog {

    public interface NewButtonPropertiesListener {
        void onNewButtonProperties(Bundle properties);
    }

    private NewButtonPropertiesListener newButtonPropertiesListener = null;

    private SeekBar volumeBar = null;
    private HorizontalScrollView availableSoundView = null;
    private final ArrayList<ImageButton> buttons = new ArrayList<>();

    private final Bundle newProperties = new Bundle();

    public SoundChooserDialog(final Activity activity, Bundle oldProperties) {
        if (activity == null)
            return;

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity);
        dialogBuilder.setTitle("Sound properties");

        dialogBuilder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //if(newButtonPropertiesListener != null && newProperties != null){
                if (newButtonPropertiesListener != null) {
                    newButtonPropertiesListener.onNewButtonProperties(newProperties);
                }
            }
        });

        dialogBuilder.setNegativeButton("dismiss", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        ViewGroup viewGroup = activity.findViewById(android.R.id.content);
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.sound_chooser_dialog, viewGroup, false);

        dialogBuilder.setView(dialogView);
        AlertDialog dialog = dialogBuilder.create();
        dialog.show();

        volumeBar = dialogView.findViewById(R.id.volumeBar);
        volumeBar.setProgress(Math.round(100.0f * oldProperties.getFloat("volume", 1.0f)));

        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                newProperties.putFloat("volume", progress / 100.0f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        newProperties.putInt("soundid", oldProperties.getInt("soundid"));

        availableSoundView = dialogView.findViewById(R.id.availableSounds);
        dialogView.post(new Runnable() {
            @Override
            public void run() {
                initSoundView(activity);
            }
        });


    }

    private void initSoundView(final Activity activity) {
        int buttonSize = availableSoundView.getHeight();

        LinearLayoutCompat layout = new LinearLayoutCompat(activity);
        layout.setOrientation(LinearLayoutCompat.HORIZONTAL);

        Log.v("Metronome", "Buttonsize: " + buttonSize);
        for(int i = 0; i < Sounds.getNumSoundID(); ++i) {
            final ImageButton soundButton = new ImageButton(activity);

            soundButton.setImageResource(Sounds.getIconID(i));
            soundButton.setScaleType(ImageView.ScaleType.FIT_XY);
            soundButton.setBackgroundColor(ContextCompat.getColor(activity, R.color.colorMyBackground));

            int pad = dp_to_px(5);
            soundButton.setPadding(pad, pad, pad, pad);

            LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(buttonSize, buttonSize);
            int margin = dp_to_px(2);
            params.setMargins(margin, margin, margin, margin);

            soundButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    highlightButton(soundButton, activity);
                }
            });


            layout.addView(soundButton, params);

            buttons.add(soundButton);
        }
        availableSoundView.addView(layout);

        ImageButton initialHighlighedButton = buttons.get(newProperties.getInt("soundid"));
        highlightButton(initialHighlighedButton, activity);
    }

    private void highlightButton(ImageButton button, Activity activity){
        int i = 0;
        for(ImageButton b : buttons) {
            if(b == button) {
                b.setBackgroundColor(ContextCompat.getColor(activity, R.color.colorAccent));
                newProperties.putInt("soundid", i);
            }
            else {
                b.setBackgroundColor(ContextCompat.getColor(activity, R.color.colorMyBackground));
            }
            ++i;
        }
    }

    public void setNewButtonPropertiesListener(NewButtonPropertiesListener newButtonPropertiesListener) {
        this.newButtonPropertiesListener = newButtonPropertiesListener;
    }

    private int dp_to_px(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }
}
