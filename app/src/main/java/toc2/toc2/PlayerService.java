package toc2.toc2;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.SoundPool;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import static toc2.toc2.App.CHANNEL_ID;

public class PlayerService extends Service {

    private final IBinder playerBinder = new PlayerBinder();

    static public final String PLAYER_NOTIFICATION_TOGGLE = "toc2:toggle player service";
    static public final int PLAYER_STARTED = 1;
    static public final int PLAYER_STOPPED = 2;
    private int playerStatus = PLAYER_STOPPED;

    private final SoundPool soundpool = new SoundPool.Builder().setMaxStreams(10).build();
    private int soundHandles[];

    private int activeSound = 4;
    private int speed = NavigationActivity.SPEED_INITIAL;
    private long dt = Math.round(1000.0 * 60.0 / speed);

    private final Handler waitHandler = new Handler();

    private final Runnable klickAndWait = new Runnable() {
        @Override
        public void run() {
            if(playerStatus == PLAYER_STARTED) {
                soundpool.play(soundHandles[activeSound], 0.99f, 0.99f, 1, 0, 1.0f);
                waitHandler.postDelayed(this, dt);
            }
        }
    };

    class PlayerBinder extends Binder {
        PlayerService getService() {
            return PlayerService.this;
        }
    }

    public PlayerService() {
        super();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v("Metronome", "PlayerService:onBind");

        int numSounds = Sounds.getNumSoundID();
        soundHandles = new int[numSounds];
        for(int i = 0; i < numSounds; ++i){
            int soundID = Sounds.getSoundID(i);
            soundHandles[i] = soundpool.load(this, soundID,1);
        }
        return playerBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v("Metronome", "PlayerService:onUnbind");
        stopPlay();
        for(int sH : soundHandles){
            soundpool.unload(sH);
        }

        return super.onUnbind(intent);
    }

    private void startForegroundService(){

         Intent notificationIntent = new Intent(this, NavigationActivity.class);
         PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

         Intent notifklick = new Intent(PLAYER_NOTIFICATION_TOGGLE);
         PendingIntent notifklickpend = PendingIntent.getBroadcast(this, 0 , notifklick, 0);

         Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                 .setContentTitle("toc")
                 //.setContentText("ContentText")
                 //.setTicker("aaa")
                 .setSmallIcon(R.drawable.ic_toc_foreground)
                 .setContentIntent(pendingIntent)
                 .addAction(R.drawable.ic_toc_foreground, "start/stop", notifklickpend)
                 //.addAction(R.drawable.ic_test, "blub", unbindIntentPend)
                 //.setDeleteIntent(unbindIntentPend)
                 .build();

         startForeground(123, notification);
    }

    public void changeSpeed(int speed){
        this.speed = speed;
        this.dt = Math.round(1000.0 * 60.0 / speed);
    }

    public void changeSound(int activeSound){
        this.activeSound = activeSound;
    }

    int getPlayerStatus(){
        return playerStatus;
    }

    //public void togglePlay() {
    //    if(player_status == PLAYER_STOPPED)
    //        startPlay();
    //    else if(player_status == PLAYER_STARTED)
    //        stopPlay();
    //    else
    //        Toast.makeText(this,"Invalid player status", Toast.LENGTH_LONG).show();
    //}

    public void startPlay() {
        Log.v("Metronome", "PlayerService:startPlay");
        if(playerStatus == PLAYER_STARTED)
            return;

        startForegroundService();
        playerStatus = PLAYER_STARTED;

        waitHandler.post(klickAndWait);
    }

    public void stopPlay() {
        Log.v("Metronome", "PlayerService:stopPlay");
        if(playerStatus == PLAYER_STOPPED)
            return;
        stopForeground(false);
        playerStatus = PLAYER_STOPPED;

        waitHandler.removeCallbacksAndMessages(null);
    }
}
