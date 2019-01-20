package toc2.toc2;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import static toc2.toc2.App.CHANNEL_ID;

public class PlayerService extends Service {

    private final IBinder playerBinder = new PlayerBinder();

    static public final int PLAYER_UNDEF = 0;
    static public final int PLAYER_STARTED = 1;
    static public final int PLAYER_STOPPED = 2;
    private int player_status = PLAYER_STOPPED;

    private PlayerThread playerThread;
    private PlayerQueue playerQueue;

    private class PlayerThread extends HandlerThread {
        private final PlayerQueue playerQueue;

        PlayerThread(){
            super("metronome player");
            playerQueue = new PlayerQueue(getApplicationContext());
        }

        PlayerQueue getPlayerQueue(){
            return playerQueue;
        }
    }

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
        playerThread = new PlayerThread();
        playerThread.setPriority(Thread.MAX_PRIORITY);
        playerThread.start();
        playerQueue = playerThread.getPlayerQueue();
        return playerBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v("Metronome", "PlayerService:onUnbind");
        stopPlay();
        Message message = new Message();
        message.what = PlayerQueue.MESSAGE_DESTROY;
        playerQueue.sendMessage(message);
        playerThread.quitSafely();
        return super.onUnbind(intent);
    }

    private void startForegroundService(){

         Intent notificationIntent = new Intent(this, NavigationActivity.class);
         PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

         Intent notifklick = new Intent(this, NavigationActivity.NotificationReceiver.class);
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
        Message message = new Message();
        message.what = PlayerQueue.MESSAGE_CHANGE_SPEED;
        Bundle bundle = new Bundle();
        bundle.putInt("speed", speed);
        message.setData(bundle);
        playerQueue.sendMessage(message);
    }

    public void changeSound(int soundid){
        Message message = new Message();
        message.what = PlayerQueue.MESSAGE_CHANGE_SOUND;
        Bundle bundle = new Bundle();
        bundle.putInt("sound", soundid);
        message.setData(bundle);
        playerQueue.sendMessage(message);
    }

    int getPlayerStatus(){
        return player_status;
    }

    public void togglePlay() {
        if(player_status == PLAYER_STOPPED)
            startPlay();
        else if(player_status == PLAYER_STARTED)
            stopPlay();
        else
            Toast.makeText(this,"Invalid player status", Toast.LENGTH_LONG).show();
    }

    public void startPlay() {
        Log.v("Metronome", "PlayerService:startPlay");
        if(player_status == PLAYER_STARTED)
            return;

        startForegroundService();
        player_status = PLAYER_STARTED;

        Message message = new Message();
        message.what = PlayerQueue.MESSAGE_PLAY;
        playerQueue.sendMessage(message);
    }

    public void stopPlay() {
        Log.v("Metronome", "PlayerService:stopPlay");
        if(player_status == PLAYER_STOPPED)
            return;
        stopForeground(false);
        player_status = PLAYER_STOPPED;

        Message message_stop = new Message();
        message_stop.what = PlayerQueue.MESSAGE_STOP;
        playerQueue.sendMessage(message_stop);
    }
}
