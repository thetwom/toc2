package toc2.toc2;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

/**

 */
public class MetronomeFragment extends Fragment {

    public ImageButton playpauseButton;

    public MetronomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_metronome, container, false);

        playpauseButton = view.findViewById(R.id.toggleplay);

        playpauseButton.setOnClickListener(new View.OnClickListener()
        {
             @Override
             public void onClick(View v) {
                 NavigationActivity act = (NavigationActivity) getActivity();
                 if (act != null) {
                     //Toast.makeText(act, "start", Toast.LENGTH_LONG).show();
                     //act.togglePlayer();
                     act.playerFrag.togglePlayer(act.getApplicationContext());
                 }
             }
        });

        //Button startbutton = view.findViewById(R.id.startplay);

        //startbutton.setOnClickListener(new View.OnClickListener()
        //{
        //     @Override
        //     public void onClick(View v)
        //     {
        //         NavigationActivity act = (NavigationActivity) getActivity();
        //        if(act != null) {
        //             //Toast.makeText(act, "start", Toast.LENGTH_LONG).show();
        //             act.togglePlayer();
        //         }
        //     }
        //});

        //Button stopbutton = view.findViewById(R.id.stopplay);

        //stopbutton.setOnClickListener(new View.OnClickListener()
        //{
        //     @Override
        //     public void onClick(View v)
        //     {
        //         NavigationActivity act = (NavigationActivity) getActivity();
        //         if(act != null) {
        //             if (NavigationActivity.playerServiceBound) {
        //                 //Toast.makeText(act, "stop", Toast.LENGTH_LONG).show();
        //                 act.stopPlayer();
        //             }
        //         }
        //     }
        //});

//        NumberPicker numPick = view.findViewById(R.id.numpick);
//        numPick.setMinValue(10);
//        numPick.setMaxValue(400);
//        numPick.setValue(NavigationActivity.speed);
//
//        numPick.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
//            @Override
//            public void onValueChange(NumberPicker picker, int oldVal, int newVal){
//                NavigationActivity act = (NavigationActivity) getActivity();
//                 if(act != null) {
//                     if (NavigationActivity.playerServiceBound) {
//                         act.changeSpeed(newVal);
//                     }
//                 }
//            }
//        });

        final TextView speedView = view.findViewById(R.id.speedview);
        speedView.setText(Integer.toString(NavigationActivity.SPEED_INITIAL));

        final SeekBar speedMeter = view.findViewById(R.id.speedmeter);

        //final VerticalSeekBar speedMeterVert = view.findViewById(R.id.speedmetervert);

//        speedView.addTextChangedListener(new TextWatcher() {
//            @Override
//            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//
//            }
//
//            @Override
//            public void onTextChanged(CharSequence s, int start, int before, int count) {
//               int speed = Integer.parseInt(s.toString());
//               if(speed >= NavigationActivity.SPEED_MIN && speed <= NavigationActivity.SPEED_MAX) {
//                   speedMeter.setProgress(speed - NavigationActivity.SPEED_MIN);
//                   NavigationActivity act = (NavigationActivity) getActivity();
//                   if(act != null) {
//                       if (NavigationActivity.playerServiceBound) {
//                           act.changeSpeed(speed);
//                       }
//                   }
//               }
//           }
//
//            @Override
//            public void afterTextChanged(Editable s) {
//            }
//        });

        speedMeter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

               int speed = seekBar.getProgress() + NavigationActivity.SPEED_MIN;
                speedView.setText(Integer.toString(speed));
                NavigationActivity act = (NavigationActivity) getActivity();

                 if(act != null && act.playerFrag != null) {

                     //if (NavigationActivity.playerServiceBound) {
                     //    Log.v("tocspeed", "New speed: " + Integer.toString(speed));
                         act.playerFrag.changeSpeed(speed);
                     //}
                 }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
//                int speed = seekBar.getProgress() + NavigationActivity.SPEED_MIN;
//                speedView.setText(Integer.toString(speed));
//                NavigationActivity act = (NavigationActivity) getActivity();
//                 if(act != null) {
//                     if (NavigationActivity.playerServiceBound) {
//                         act.changeSpeed(speed);
//                     }
//                 }
            }
        });


        speedMeter.setMax(NavigationActivity.SPEED_MAX - NavigationActivity.SPEED_MIN);
        speedMeter.setProgress(NavigationActivity.SPEED_INITIAL - NavigationActivity.SPEED_MIN);

        Button plusButton = view.findViewById(R.id.speedplus);

        plusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int speed = speedMeter.getProgress() + 1;
                speedMeter.setProgress(speed);
            }
        });

        Button minusButton = view.findViewById(R.id.speedminus);

        minusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int speed = speedMeter.getProgress() - 1;
                speedMeter.setProgress(speed);
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
                playpauseButton.setImageResource(R.drawable.ic_play);
                break;
            case PlayerService.PLAYER_STARTED:
                playpauseButton.setImageResource(R.drawable.ic_pause);
                break;
            default:
                break;
        }
    }
}
