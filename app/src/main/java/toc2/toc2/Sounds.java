package toc2.toc2;

import android.content.Context;
import android.util.Log;

final class Sounds {

    private final static class SoundInfo {

        SoundInfo(int soundID, int nameID, int iconID){
            this.soundID = soundID;
            this.nameID = nameID;
            this.iconID = iconID;
        }
        final int soundID;
        final int nameID;
        final int iconID;
    }

    private final static SoundInfo[] sounds = {
            //new SoundInfo(R.raw.hhp_dry_a, R.string.hihat, R.drawable.ic_hihat),
            new SoundInfo(R.raw.hihat, R.string.hihat, R.drawable.ic_hihat_note),
            //new SoundInfo(R.raw.sn_jazz_c, R.string.snare, R.drawable.ic_snare),
            new SoundInfo(R.raw.snare, R.string.snare, R.drawable.ic_c_note),
            new SoundInfo(R.raw.sticks, R.string.sticks, R.drawable.ic_c_note_rim),
            new SoundInfo(R.raw.claves, R.string.claves, R.drawable.ic_gp_note),
            new SoundInfo(R.raw.woodblock_high, R.string.woodblock, R.drawable.ic_ep_note),
            new SoundInfo(R.raw.hihat, R.string.mute, R.drawable.ic_quarter_pause),
    };


    static CharSequence[] getNames(Context context){
        Log.v("Metronome", "Sound.getNames : number of sounds: " + sounds.length);
        CharSequence[] names = new CharSequence[sounds.length];
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
    static int getSoundID(int id)
    {
        return sounds[id].soundID;
    }
    static int getIconID(int id) {
        return sounds[id].iconID;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isMute(int id) {
        //noinspection RedundantIfStatement
        if(sounds[id].iconID == R.drawable.ic_quarter_pause)
            return true;
        return false;
    }
}
