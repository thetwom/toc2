package toc2.toc2;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.Toast;


/**

 */
public class MetronomeFragment extends Fragment {

    public MetronomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_metronome, container, false);

        Button startbutton = view.findViewById(R.id.startplay);

        startbutton.setOnClickListener(new View.OnClickListener()
        {
             @Override
             public void onClick(View v)
             {
                 NavigationActivity act = (NavigationActivity) getActivity();
                 if(act != null) {
                     //Toast.makeText(act, "start", Toast.LENGTH_LONG).show();
                     act.startPlayer();
                 }
             }
        });

        Button stopbutton = view.findViewById(R.id.stopplay);

        stopbutton.setOnClickListener(new View.OnClickListener()
        {
             @Override
             public void onClick(View v)
             {
                 NavigationActivity act = (NavigationActivity) getActivity();
                 if(act != null) {
                     if (act.playerServiceBound) {
                         //Toast.makeText(act, "stop", Toast.LENGTH_LONG).show();
                         act.stopPlayer();
                     }
                 }
             }
        });

        NumberPicker numPick = view.findViewById(R.id.numpick);
        numPick.setMinValue(10);
        numPick.setMaxValue(400);
        numPick.setValue(NavigationActivity.speed);

        numPick.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal){
                NavigationActivity act = (NavigationActivity) getActivity();
                 if(act != null) {
                     if (act.playerServiceBound) {
                         act.changeSpeed(newVal);
                     }
                 }
            }
        });

        return view;
    }
}
