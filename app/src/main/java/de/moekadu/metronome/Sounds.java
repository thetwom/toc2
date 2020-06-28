/*
 * Copyright 2019 Michael Moessner
 *
 * This file is part of Metronome.
 *
 * Metronome is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Metronome is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Metronome.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.moekadu.metronome;

import android.content.Context;
// import android.util.Log;

final class Sounds {

    private final static class SoundInfo {

        SoundInfo(int soundID44, int soundID48, int nameID, int iconID){
            this.soundID44 = soundID44;
            this.soundID48 = soundID48;
            this.nameID = nameID;
            this.iconID = iconID;
        }
        final int soundID44;
        final int soundID48;
        final int nameID;
        final int iconID;
    }

    private final static SoundInfo[] sounds = {
            new SoundInfo(R.raw.base44_wav, R.raw.base48_wav, R.string.base, R.drawable.ic_a_note),
            new SoundInfo(R.raw.snare44_wav, R.raw.snare48_wav, R.string.snare, R.drawable.ic_c_note),
            new SoundInfo(R.raw.sticks44_wav, R.raw.sticks48_wav, R.string.sticks, R.drawable.ic_c_note_rim),
            new SoundInfo(R.raw.woodblock_high44_wav, R.raw.woodblock_high48_wav, R.string.woodblock, R.drawable.ic_ep_note),
            new SoundInfo(R.raw.claves44_wav, R.raw.claves48_wav, R.string.claves, R.drawable.ic_gp_note),
            //new SoundInfo(R.raw.hhp_dry_a, R.string.hihat, R.drawable.ic_hihat),
            new SoundInfo(R.raw.hihat44_wav, R.raw.hihat48_wav, R.string.hihat, R.drawable.ic_hihat_note),
            //new SoundInfo(R.raw.sn_jazz_c, R.string.snare, R.drawable.ic_snare),
            new SoundInfo(R.raw.mute44_wav, R.raw.mute48_wav, R.string.mute, R.drawable.ic_quarter_pause),
    };


    static CharSequence[] getNames(Context context){
        // Log.v("Metronome", "Sound.getNames : number of sounds: " + sounds.length);
        CharSequence[] names = new CharSequence[sounds.length];
        int i = 0;
        for(SoundInfo si : sounds) {
            String name = context.getResources().getString(si.nameID);
            // Log.v("Metronome", "Sounds.get_names : " + name);
            names[i] = name;
            i++;
        }
        return names;
    }

    static int defaultSound(){
        return 3;
    }
    static int getNumSoundID() {
        return sounds.length;
    }
    static int getSoundID(int id, int sampleRate)
    {
        if(sampleRate == 44100)
            return sounds[id].soundID44;
        else
            return sounds[id].soundID48;
    }

    static int[] getSoundIDs(int sampleRate) {
        int[] soundIDs = new int[sounds.length];

        for(int i = 0; i < sounds.length; ++i)   {
            soundIDs[i] = getSoundID(i, sampleRate);
        }
        return  soundIDs;
    }
    static int getIconID(int id) {
        return sounds[id].iconID;
    }
}
