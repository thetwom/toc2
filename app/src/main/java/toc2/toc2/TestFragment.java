package toc2.toc2;


import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;


/**
 * A simple {@link Fragment} subclass.
 */
public class TestFragment extends Fragment {

    private TextView speedText;
    private SpeedPanel speedPanel;
    private int speed = NavigationActivity.SPEED_INITIAL;

    public TestFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_test, container, false);

        speedText = view.findViewById(R.id.speedtext);
        speedText.setText(Integer.toString(speed) + "bpm");

        speedPanel = view.findViewById(R.id.speedpanel);

        speedPanel.setOnSpeedChangedListener(new SpeedPanel.SpeedChangedListener(){
            @Override
            public void onSpeedChanged(int speed_change) {
                NavigationActivity act = (NavigationActivity) getActivity();
                int current_speed = NavigationActivity.SPEED_INITIAL;
                if(act != null && act.playerFrag != null) {
                    current_speed = act.playerFrag.getSpeed();
                }
                int speed = current_speed + speed_change;
                speed = Math.max(speed, NavigationActivity.SPEED_MIN);
                speed = Math.min(speed, NavigationActivity.SPEED_MAX);

                if(act != null && act.playerFrag != null && speed_change != 0) {
                    act.playerFrag.changeSpeed(speed);
                 }
                speedText.setText(Integer.toString(speed) + "bpm");
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

}
