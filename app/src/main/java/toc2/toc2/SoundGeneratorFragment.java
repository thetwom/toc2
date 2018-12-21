package toc2.toc2;


import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;


/**

 */
public class SoundGeneratorFragment extends Fragment {

    private AudioTrack track;
    private int nativeRate;

    public SoundGeneratorFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        nativeRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        // Inflate the layout for this fragment

        View view = inflater.inflate(R.layout.fragment_sound_generator, container, false);
        Button button = (Button) view.findViewById(R.id.sgplay);

        button.setOnClickListener(new View.OnClickListener()
        {
             @Override
             public void onClick(View v)
             {
                testPlay();
             }
        });
        return view;
    }

    public void testPlay() {
        if(track == null) {
            byte sine[] = createSine(440.0, 1.0, nativeRate);
            byte gaussder[] = createGaussDeriv(0.001, nativeRate);
            //track = getAudioTrack(sine.length);
            track = getAudioTrack(gaussder.length);
            //track.write(sine,0, sine.length);
            track.write(gaussder,0, gaussder.length);
        }
        else{
            track.stop();
            track.reloadStaticData();
        }
        //Toast.makeText(getActivity(), Integer.toString(track.getSampleRate()),Toast.LENGTH_LONG).show();
        track.play();
    }

    public AudioTrack getAudioTrack(int bufferSize) {

            // getMinBufferSize???
        AudioFormat.Builder formatBuilder = new AudioFormat.Builder();
        formatBuilder.setEncoding(AudioFormat.ENCODING_PCM_16BIT);
        formatBuilder.setSampleRate(nativeRate);
        formatBuilder.setChannelMask(AudioFormat.CHANNEL_OUT_MONO);

        AudioAttributes.Builder attribBuilder = new AudioAttributes.Builder();
        attribBuilder.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
        attribBuilder.setUsage(AudioAttributes.USAGE_MEDIA);

        AudioTrack.Builder trackBuilder = new AudioTrack.Builder();
        trackBuilder.setAudioAttributes(attribBuilder.build());
        trackBuilder.setAudioFormat(formatBuilder.build());
        trackBuilder.setTransferMode(AudioTrack.MODE_STATIC);
        trackBuilder.setBufferSizeInBytes(bufferSize);

        return trackBuilder.build();
    }

    public byte[] createSine(double freq, double duration, int sampleRate) {
        int numSamples = (int) Math.round(sampleRate * duration);
        double soundDataRaw[] = new double[numSamples];

        for(int i = 0; i < soundDataRaw.length; i++) {
            soundDataRaw[i] = Math.sin(2.0 * Math.PI * freq * (double) i / sampleRate);
        }

        int maxsample = (int) (sampleRate / freq * 0.75);

        byte soundData[] = new byte[2*numSamples];

        int idx = 0;
        for (final double sd : soundDataRaw) {
            // scale to maximum amplitude
            final short val = (short) (sd * 32767);
            // in 16 bit wav PCM, first byte is the low order byte
            soundData[idx++] = (byte) (val & 0x00ff);
            soundData[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        return soundData;
    }

    public byte[] createGaussDeriv(double duration, int sampleRate) {
        double expfac = 50.0 * Math.log(2.0);
        double maxval = duration / Math.sqrt(2.0*expfac) * Math.exp(-0.5);
        double shift = duration / 2.0;
        double dt = 1.0 / sampleRate;

        int numSamples = (int) Math.round(sampleRate * duration);
        double soundDataRaw[] = new double[numSamples];

        for(int i = 0; i < soundDataRaw.length; i++) {
            double tm = i * dt;
            soundDataRaw[i] = (shift-tm) / maxval * Math.exp(-expfac / Math.pow(duration,2.0) * Math.pow(tm-shift,2.0));
        }

        int pos = (int) Math.round(numSamples * (1.0-0.38));
        Toast.makeText(getActivity(), Double.toString(soundDataRaw[pos]),Toast.LENGTH_LONG).show();

        byte soundData[] = new byte[2*numSamples];

        int idx = 0;
        for (final double sd : soundDataRaw) {
            // scale to maximum amplitude
            final short val = (short) (sd * 32767);
            // in 16 bit wav PCM, first byte is the low order byte
            soundData[idx++] = (byte) (val & 0x00ff);
            soundData[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        return soundData;
    }
}
