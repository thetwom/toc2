package toc2.toc2;

import android.content.Context;
import android.util.Log;

final class Sounds {

    private final static class SoundInfo {

        SoundInfo(int soundID, int nameID){
            this.soundID = soundID;
            this.nameID = nameID;
        }
        final int soundID;
        final int nameID;
    }

    private final static SoundInfo sounds[] = {
            new SoundInfo(R.raw.hhp_dry_a, R.string.hihat),
            new SoundInfo(R.raw.sn_jazz_c, R.string.snare),
            new SoundInfo(R.raw.stick, R.string.sticks),
            new SoundInfo(R.raw.claves, R.string.claves),
            new SoundInfo(R.raw.woodblock, R.string.woodblock),
    };


    static CharSequence[] getNames(Context context){
        Log.v("Metronome", "Sound.getNames : number of sounds: " + Integer.toString(sounds.length));
        CharSequence names[] = new CharSequence[sounds.length];
        int i = 0;
        for(SoundInfo si : sounds) {
            String name = context.getResources().getString(si.nameID);
            Log.v("Metronome", "Sounds.get_names : " + name);
            names[i] = name;
            i++;
        }
        return names;
    }

    static int getNumSoundID() {
        return sounds.length;
    }
    static int getSoundID(int id){
        return sounds[id].soundID;
    }
}
