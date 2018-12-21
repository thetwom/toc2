package toc2.toc2;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import static java.lang.Math.pow;

public class SoundFactory {

    private static final int SINE = 1;
    private static final int GAUSSDER = 2;
    private static final int SKEWEDANDSINE = 3;

    private static final int nativeSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);

    public static AudioTrack createSound(Bundle bundle){
        AudioTrack sound = null;
        int soundType = bundle.getInt("type");
        switch (soundType) {
            case SINE:{
                sound = createSine(bundle);
                break;
            }
            case GAUSSDER: {
                sound = createGaussDerivative(bundle);
                break;
            }
            case SKEWEDANDSINE: {
                sound = createSkewedAndSine(bundle);
                break;
            }
            default: {
                break;
            }
        }
        return sound;

    }

    public static Bundle getSinePackage(double frequency, double duration){
        Bundle bundle = new Bundle();
        bundle.putInt("type", SINE);
        bundle.putDouble("frequency", frequency);
        bundle.putDouble("duration", duration);
        return bundle;
    }

    public static Bundle getGaussDerivativePackage(double duration){
        Bundle bundle = new Bundle();
        bundle.putInt("type", GAUSSDER);
        bundle.putDouble("duration", duration);
        return bundle;
    }

    public static Bundle getSkewedAndSinePackage(double frequency, double duration){
        Bundle bundle = new Bundle();
        bundle.putInt("type", SKEWEDANDSINE);
        bundle.putDouble("duration", duration);
        bundle.putDouble("frequency", frequency);
        return bundle;
    }

    private static AudioTrack createSine(Bundle bundle){
        double freq = bundle.getDouble("frequency");
        double duration = bundle.getDouble("duration");

        int numSamples = (int) Math.round(nativeSampleRate * duration);
        double soundDataRaw[] = new double[numSamples];
        for(int i = 0; i < soundDataRaw.length; i++) {
            soundDataRaw[i] = Math.sin(2.0 * Math.PI * freq * (double) i / nativeSampleRate);
        }

        return generateSound(soundDataRaw);
    }

    private static AudioTrack createGaussDerivative(Bundle bundle){
        double duration = bundle.getDouble("duration");

        double expfac = 50.0 * Math.log(2.0);
        double maxval = duration / Math.sqrt(2.0*expfac) * Math.exp(-0.5);
        double shift = duration / 2.0;
        double dt = 1.0 / nativeSampleRate;

        int numSamples = (int) Math.round(nativeSampleRate * duration);
        double soundDataRaw[] = new double[numSamples];

        for(int i = 0; i < soundDataRaw.length; i++) {
            double tm = i * dt;
            soundDataRaw[i] = (shift-tm) / maxval * Math.exp(-expfac / pow(duration,2.0) * pow(tm-shift,2.0));
        }

        return generateSound(soundDataRaw);
    }

    private static AudioTrack createSkewedAndSine(Bundle bundle){
        double duration = bundle.getDouble("duration");
        double sinfreq = bundle.getDouble("frequency");
        double dt = 1.0 / nativeSampleRate;
        //double stretch = 10.0 / duration;
        double stretch = 1.6 / duration;
        double mu = 3.0;

        int numSamples = (int) Math.round(nativeSampleRate * duration);
        double soundDataRaw[] = new double[numSamples];

        double maxval = 0.0;
        for(int i = 0; i < soundDataRaw.length; i++) {
            double tm = i * dt;
            double x = tm * stretch;
            //double val = Math.pow(stretch*x,2./3.)
            //        * Math.exp(-(Math.pow(stretch*x-mu,2)/(mu*mu*stretch*x)))
            //        * Math.sin(2.0*Math.PI*sinfreq*tm);
            double val = Math.sin(2.0*Math.PI*sinfreq*tm) + 0.1* Math.sin(2.0*Math.PI*sinfreq*tm*3);
            val *= Math.exp(-Math.pow(stretch*x,4));
            if(i < soundDataRaw.length/8.0){
                val *= -Math.cos(Math.PI * tm / (soundDataRaw.length*dt/8.0)) + 1;
            }
            soundDataRaw[i] = val;
            maxval = Math.max(Math.abs(val), maxval);
        }
        for(int i = 0; i < soundDataRaw.length; i++) {
            soundDataRaw[i] /= maxval;
            //if (soundDataRaw[i] > 1.0)
            //    soundDataRaw[i] = 1.0;
            //if (soundDataRaw[i] < -1.0)
            //    soundDataRaw[i] = -1.0;
        }
        return generateSound(soundDataRaw);
    }

    private static AudioTrack generateSound(double soundDataRaw[]){

        byte soundData[] = new byte[2*soundDataRaw.length];

        int idx = 0;
        for (final double sd : soundDataRaw) {
            // scale to maximum amplitude
            final short val = (short) (sd * 32767);
            // in 16 bit wav PCM, first byte is the low order byte
            soundData[idx++] = (byte) (val & 0x00ff);
            soundData[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        AudioFormat.Builder formatBuilder = new AudioFormat.Builder();
        formatBuilder.setEncoding(AudioFormat.ENCODING_PCM_16BIT);
        formatBuilder.setSampleRate(nativeSampleRate);
        formatBuilder.setChannelMask(AudioFormat.CHANNEL_OUT_MONO);

        AudioAttributes.Builder attribBuilder = new AudioAttributes.Builder();
        attribBuilder.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
        attribBuilder.setUsage(AudioAttributes.USAGE_MEDIA);

        AudioTrack.Builder trackBuilder = new AudioTrack.Builder();
        trackBuilder.setAudioAttributes(attribBuilder.build());
        trackBuilder.setAudioFormat(formatBuilder.build());
        trackBuilder.setTransferMode(AudioTrack.MODE_STATIC);
        trackBuilder.setBufferSizeInBytes(soundData.length);

        Log.v("TOCCCC","Num. sount data " + Integer.toString(soundDataRaw.length));
        AudioTrack track = trackBuilder.build();
        track.write(soundData, 0, soundData.length);
        return track;
    }
}
