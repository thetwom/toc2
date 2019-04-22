package toc2.toc2;

import android.os.Bundle;

import java.util.ArrayList;

public class SoundProperties {

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

    static public String createMetaDataString(ArrayList<Bundle> sounds){
        StringBuilder mdata = new StringBuilder();
        for(Bundle b: sounds){
            int soundid = b.getInt("soundid", 0);
            float volume = b.getFloat("volume", 1.0f);
            mdata.append(soundid).append(" ").append(volume).append(" ");
        }
        return mdata.toString();
    }

    static public boolean equal(Bundle sound1, Bundle sound2) {
        if(Math.abs(sound1.getFloat("volume", 1.0f) - sound2.getFloat("volume", 1.0f)) > 1e-3)
            return false;
        if(sound1.getInt("soundid", 0) != sound2.getInt("soundid",0))
            return false;
        return true;
    }

    static public boolean equal(ArrayList<Bundle> sounds1, ArrayList<Bundle> sounds2){
        if(sounds1 == null && sounds2 == null)
            return true;

        if(sounds1 == null || sounds2 == null)
            return false;

        if(sounds1.size() != sounds2.size())
            return false;

        for(int i = 0; i < sounds1.size(); ++i)
            if(!equal(sounds1.get(i), sounds2.get(i)))
                return false;

        return true;
    }
}
