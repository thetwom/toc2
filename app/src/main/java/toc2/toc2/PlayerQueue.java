package toc2.toc2;

import android.content.Context;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

class PlayerQueue extends Handler {

    public static final int MESSAGE_PLAY = 2;
    public static final int MESSAGE_STOP = 3;
    public static final int MESSAGE_CHANGE_SPEED = 4;
    public static final int MESSAGE_CHANGE_SOUND = 5;
    public static final int MESSAGE_DESTROY = 6;

    private long dt;
    private final SoundPool soundpool;
    private int soundid = -1;
    private final Context context;

    PlayerQueue(Context context){
        this.context = context;
        soundpool = new SoundPool.Builder().setMaxStreams(10).build();
    }

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
                if(soundid < 0)
                    soundpool.unload(soundid);

                int soundResID = bundle.getInt("sound");
                soundid = soundpool.load(context, soundResID,1);
                break;
            }
            case MESSAGE_PLAY:{

                long millis = SystemClock.uptimeMillis();
                soundpool.play(soundid, 0.99f, 0.99f, 1, 0, 1.0f);

                Message message_play = new Message();
                message_play.what = MESSAGE_PLAY;
                sendMessageAtTime(message_play, millis + dt);
                break;
            }
            case MESSAGE_STOP:{
                removeCallbacksAndMessages(null);
                break;
            }
            case MESSAGE_DESTROY: {
                soundpool.release();
                break;
            }
            default: {
                break;
            }
        }
    }
}
