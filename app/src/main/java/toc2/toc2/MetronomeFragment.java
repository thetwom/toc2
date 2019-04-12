package toc2.toc2;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.animation.DynamicAnimation;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

/**

 */
public class MetronomeFragment extends Fragment {

    private TextView speedText;
    private SpeedPanel speedPanel;
    private ImageButton soundButton;
    private SoundChooser soundChooser;
    private boolean playerServiceBound = false;
    private ServiceConnection playerConnection = null;
    private int checkedDialogSound = 0;
    private PlayerService playerService;
    private Context playerContext;

    private int fragHeight;
    private int fragWidth;

    float soundButtonX, soundButtonY;

    private GestureDetector mTapDetector;

    private class GestureTap extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {

            NavigationActivity act = (NavigationActivity) getActivity();
            if (act == null) {
                return true;
            }
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(act);
            dialogBuilder.setTitle("Choose sound");

            dialogBuilder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (playerServiceBound) {
                        int sounds[] = {checkedDialogSound};
                        playerService.changeSound(sounds);
                        //playerService.changeSound(checkedDialogSound);
                    }
                }
            });

            dialogBuilder.setNegativeButton("dismiss", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            checkedDialogSound = 0;
            if (playerServiceBound) {
                checkedDialogSound = playerService.getSound()[0];
            }

            dialogBuilder.setSingleChoiceItems(Sounds.getNames(act), checkedDialogSound, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    checkedDialogSound = which;
                }
            });

            AlertDialog dialog = dialogBuilder.create();
            dialog.show();
            return false;
        }
    }

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
        //speedText.setText(getString(R.string.bpm,getCurrentSpeed()));

        mTapDetector = new GestureDetector(getActivity(), new GestureTap());

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


        soundButton = view.findViewById(R.id.soundbutton);

        soundButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int action = event.getActionMasked();

                mTapDetector.onTouchEvent(event);

                switch(action){
                    case MotionEvent.ACTION_DOWN:
                        soundButtonX = v.getX() - event.getRawX();
                        soundButtonY = v.getY() - event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        v.animate()
                                .x(event.getRawX() + soundButtonX)
                                .y(event.getRawY() + soundButtonY)
                                .setDuration(0)
                                .start();
                        break;
                    case MotionEvent.ACTION_UP:
                        final SpringForce springForceX = new SpringForce(fragWidth - soundButton.getWidth())
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                                .setStiffness(SpringForce.STIFFNESS_MEDIUM);
                        final SpringForce springForceY = new SpringForce(fragHeight - soundButton.getHeight())
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                                .setStiffness(SpringForce.STIFFNESS_MEDIUM);
                        final SpringAnimation springAnimationX= new SpringAnimation(soundButton, DynamicAnimation.X).setSpring(springForceX);
                        final SpringAnimation springAnimationY= new SpringAnimation(soundButton, DynamicAnimation.Y).setSpring(springForceY);
                        springAnimationX.start();
                        springAnimationY.start();
                        break;
                }

                return false;
            }
        });

        soundChooser = view.findViewById(R.id.soundchooser);

        soundChooser.setButtonClickedListener(new SoundChooser.ButtonClickedListener() {
            @Override
            public void onButtonClicked(MoveableButton button) {
                NavigationActivity act = (NavigationActivity) getActivity();
                SoundChooserDialog soundChooserDialog = new SoundChooserDialog(act);
                soundChooserDialog.setNewButtonPropertiesListener(new SoundChooserDialog.NewButtonPropertiesListener() {
                    @Override
                    public void onNewButtonProperties(Bundle properties) {
                        Log.v("Metronome", "Setting new button properties");
                    }
                });
            }
        });

        NavigationActivity act = (NavigationActivity) getActivity();
        if(act != null) {
            bindService(act.getApplicationContext());
        }

        view.post(new Runnable() {
            @Override
            public void run() {
                fragHeight = view.getHeight();
                fragWidth =  view.getWidth();
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        Log.v("Metronome", "MetronomeFragment:onDestroyView");
        if(playerServiceBound) {
            playerService.unregisterMediaControllerCallback(mediaControllerCallback);
            playerServiceBound = false;
            playerContext.unbindService(playerConnection);
        }
        super.onDestroyView();
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

    public void updateView(MediaMetadataCompat metadata){
        String soundString = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        int [] soundIDs = MetaDataHelper.parseMetaDataString(soundString);
        soundButton.setImageResource(Sounds.getIconID(soundIDs[0]));
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

}
