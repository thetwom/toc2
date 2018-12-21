package toc2.toc2;

import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.widget.Toast;

public class PlayerQueue extends Handler {

    public static final int MESSAGE_PLAY = 2;
    public static final int MESSAGE_STOP = 3;
    public static final int MESSAGE_CHANGE_SPEED = 4;
    public static final int MESSAGE_CHANGE_SOUND = 5;

    private long dt;
    private AudioTrack sound = null;

    @Override
    public void handleMessage(Message message) {
        switch (message.what){
            case MESSAGE_CHANGE_SPEED:{
                Bundle bundle = message.getData();
                int speed = bundle.getInt("speed");
                dt = Math.round(1000.0 * 60.0 / speed);
                break;
            }
            case MESSAGE_CHANGE_SOUND:{
                Bundle bundle = message.getData();
                Bundle soundBundle = bundle.getBundle("sound");
                // Can we, instead of releasing, just rewrite the sound?
                if(sound != null)
                    sound.release();
                if (soundBundle != null) {
                    sound = SoundFactory.createSound(soundBundle);
                }
                break;
            }
            case MESSAGE_PLAY:{
                sound.stop();
                sound.reloadStaticData();

                long millis = SystemClock.uptimeMillis();
                sound.play();

                Message message_play = new Message();
                message_play.what = MESSAGE_PLAY;
                sendMessageAtTime(message_play, millis + dt);
                break;
            }
            case MESSAGE_STOP:{
                removeCallbacksAndMessages(null);
                break;
            }
            default: {
                break;
            }
        }
    }
}
