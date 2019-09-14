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

import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

class SoundProperties {

    static public ArrayList<Bundle> parseMetaDataString(String data){
        String[] elements = data.split("\\s");
        ArrayList<Bundle> sounds = new ArrayList<>();

        for(int i = 0; i < elements.length / 2; i++){
            int soundid = Integer.parseInt(elements[2*i]);
            float volume = Float.parseFloat(elements[2*i+1]);
            Bundle s = new Bundle();
            s.putInt("soundid", soundid);
            s.putFloat("volume", volume);
            sounds.add(s);
        }
        return sounds;
    }

    static public String createMetaDataString(List<Bundle> sounds){
        StringBuilder mdata = new StringBuilder();
        for(Bundle b: sounds){
            int soundid = b.getInt("soundid", 0);
            float volume = b.getFloat("volume", 1.0f);
            mdata.append(soundid).append(" ").append(volume).append(" ");
        }
        return mdata.toString();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static public boolean equal(Bundle sound1, Bundle sound2) {
        if(Math.abs(sound1.getFloat("volume", 1.0f) - sound2.getFloat("volume", 1.0f)) > 1e-3)
            return false;
        //noinspection RedundantIfStatement
        if(sound1.getInt("soundid", 0) != sound2.getInt("soundid",0))
            return false;
        return true;
    }

    static public Bundle deepCopy(Bundle sound) {
        Bundle copy = new Bundle();
        copy.putInt("soundid", sound.getInt("soundid"));
        copy.putFloat("volume", sound.getFloat("volume"));
        return copy;
    }

    static public boolean equal(List<Bundle> sounds1, List<Bundle> sounds2){
        if(sounds1 == null && sounds2 == null) {
//            Log.v("Metrononme", "SoundProperties:equal: sounds1 and sounds2 are null");
            return true;
        }

        if(sounds1 == null || sounds2 == null) {
//            Log.v("Metrononme", "SoundProperties:equal: Either sounds1 or sounds2 is null");
            return false;
        }

        if(sounds1.size() != sounds2.size()) {
//            Log.v("Metrononme", "SoundProperties:equal: sounds1 and sounds2 have different size");
            return false;
        }

        for(int i = 0; i < sounds1.size(); ++i) {
//            Log.v("Metrononme", "SoundProperties:equal: " + sounds1.get(i).getInt("soundid") + "  " + sounds2.get(i).getInt("soundid") );
            if (!equal(sounds1.get(i), sounds2.get(i))) {
//                Log.v("Metrononme", "SoundProperties:equal: Sound " + i + " is different");
                return false;
            }
        }

        return true;
    }
}
