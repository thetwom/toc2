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
            new SoundInfo(R.raw.base44, R.raw.base48, R.string.base, R.drawable.ic_a_note),
            new SoundInfo(R.raw.snare44, R.raw.snare48, R.string.snare, R.drawable.ic_c_note),
            new SoundInfo(R.raw.sticks44, R.raw.sticks48, R.string.sticks, R.drawable.ic_c_note_rim),
            new SoundInfo(R.raw.woodblock_high44, R.raw.woodblock_high48, R.string.woodblock, R.drawable.ic_ep_note),
            new SoundInfo(R.raw.claves44, R.raw.claves48, R.string.claves, R.drawable.ic_gp_note),
            //new SoundInfo(R.raw.hhp_dry_a, R.string.hihat, R.drawable.ic_hihat),
            new SoundInfo(R.raw.hihat44, R.raw.hihat48, R.string.hihat, R.drawable.ic_hihat_note),
            //new SoundInfo(R.raw.sn_jazz_c, R.string.snare, R.drawable.ic_snare),
            new SoundInfo(R.raw.hihat44, R.raw.hihat48, R.string.mute, R.drawable.ic_quarter_pause),
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
