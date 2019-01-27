package toc2.toc2;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**

 */
public class MetronomeFragment extends Fragment {

    private TextView speedText;
    private SpeedPanel speedPanel;

    public MetronomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_metronome, container, false);

        speedText = view.findViewById(R.id.speedtext);
        speedText.setText(Integer.toString(getCurrentSpeed()) + " bpm");

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
                speedText.setText(Integer.toString(speed) + " bpm");
            }
        });

        speedPanel.setOnButtonClickedListener(new SpeedPanel.ButtonClickedListener() {
            @Override
            public void onButtonClicked() {
                NavigationActivity act = (NavigationActivity) getActivity();
                 if (act != null) {
                     //Toast.makeText(act, "start", Toast.LENGTH_LONG).show();
                     //act.togglePlayer();
                     act.playerFrag.togglePlayer(act.getApplicationContext());
                 }
                //Toast.makeText(getContext(),"Play button clicked", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    public void updateView(PlayerService playerService){
        int playerStatus = PlayerService.PLAYER_STOPPED;

        if(playerService != null) {
            playerStatus = playerService.getPlayerStatus();
        }

        switch(playerStatus) {
            case PlayerService.PLAYER_STOPPED:
                speedPanel.changeStatus(SpeedPanel.STATUS_PAUSED);
                break;
            case PlayerService.PLAYER_STARTED:
                speedPanel.changeStatus(SpeedPanel.STATUS_STARTED);
                break;
           default:
                break;
        }
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
