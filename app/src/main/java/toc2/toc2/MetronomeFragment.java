package toc2.toc2;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

/**

 */
public class MetronomeFragment extends Fragment {

    private TextView speedText;
    private SpeedPanel speedPanel;

    private int checkedDialogSound = 0;

    public MetronomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_metronome, container, false);

        speedText = view.findViewById(R.id.speedtext);
        speedText.setText(getString(R.string.bpm,getCurrentSpeed()));

        speedPanel = view.findViewById(R.id.speedpanel);

        speedPanel.setOnSpeedChangedListener(new SpeedPanel.SpeedChangedListener(){
            @Override
            public void onSpeedChanged(int speed_change) {
                int currentSpeed = getCurrentSpeed();
                int speed = currentSpeed + speed_change;
                speed = Math.max(speed, NavigationActivity.SPEED_MIN);
                speed = Math.min(speed, NavigationActivity.SPEED_MAX);

                NavigationActivity act = (NavigationActivity) getActivity();
                if(act != null && act.playerFrag != null && speed_change != 0) {
                    act.playerFrag.changeSpeed(speed);
                 }
                //speedText.setText(getString(R.string.bpm,speed));
            }
        });

        speedPanel.setOnButtonClickedListener(new SpeedPanel.ButtonClickedListener() {

            @Override
            public void onPause() {
                NavigationActivity act = (NavigationActivity) getActivity();
                if (act != null) {
                     Log.v("Metronome", "speedPanel:onPause()");
                     act.playerFrag.stopPlayer();
                }
            }

            @Override
            public void onPlay() {
                NavigationActivity act = (NavigationActivity) getActivity();
                if (act != null) {
                     Log.v("Metronome", "speedPanel:onPause()");
                     act.playerFrag.startPlayer(act.getApplicationContext());
                }
            }
        });


        ImageButton soundButton = view.findViewById(R.id.soundbutton);

        soundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final NavigationActivity act = (NavigationActivity) getActivity();

                if(act != null) {
                    //final Sounds sounds = new Sounds();

                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(act);
                    dialogBuilder.setTitle("Choose sound");
                    dialogBuilder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if(act.playerFrag != null) {
                                act.playerFrag.changeSound(checkedDialogSound);
                            }
                        }
                    });

                    dialogBuilder.setNegativeButton("dismiss", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

                    if(act.playerFrag != null)
                        checkedDialogSound = act.playerFrag.getSound();

                    dialogBuilder.setSingleChoiceItems(Sounds.getNames(act), checkedDialogSound, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            checkedDialogSound = which;
                        }
                    });

                    AlertDialog dialog = dialogBuilder.create();
                    dialog.show();
                }
            }
        });

        return view;
    }

    public void updateView(PlaybackStateCompat state){
        if(state.getState() == PlaybackStateCompat.STATE_PLAYING){
            speedPanel.changeStatus(SpeedPanel.STATUS_PLAYING);
        }
        else if(state.getState() == PlaybackStateCompat.STATE_PAUSED){
            speedPanel.changeStatus(SpeedPanel.STATUS_PAUSED);
        }
        speedText.setText(getString(R.string.bpm, Math.round(state.getPlaybackSpeed())));
    }

    private int getCurrentSpeed(){
        NavigationActivity act = (NavigationActivity) getActivity();
        int currentSpeed = NavigationActivity.SPEED_INITIAL;
        if(act != null && act.playerFrag != null) {
            currentSpeed = act.playerFrag.getSpeed();
        }
        return currentSpeed;
    }
}
