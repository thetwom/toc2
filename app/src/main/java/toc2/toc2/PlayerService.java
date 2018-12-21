package toc2.toc2;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import static toc2.toc2.App.CHANNEL_ID;

public class PlayerService extends Service {

    private final IBinder playerBinder = new PlayerBinder();

    private final int PLAYER_STARTED = 1;
    private final int PLAYER_STOPPED = 2;
    private int player_status = PLAYER_STOPPED;

    private PlayerThread playerThread;
    private PlayerQueue playerQueue;

    private class PlayerThread extends HandlerThread {
        private PlayerQueue playerQueue;

        PlayerThread(String name){
            super(name);
            playerQueue = new PlayerQueue();
        }

        public PlayerQueue getPlayerQueue(){
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
        playerThread = new PlayerThread("metronome player");
        playerThread.start();
        playerQueue = playerThread.getPlayerQueue();
        return playerBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        playerThread.quit();
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

    public void changeSound(Bundle soundBundle){
        Message message = new Message();
        message.what = PlayerQueue.MESSAGE_CHANGE_SOUND;
        Bundle bundle = new Bundle();
        bundle.putBundle("sound", soundBundle);
        message.setData(bundle);
        playerQueue.sendMessage(message);
    }

    public void togglePlay() {
        if(player_status == PLAYER_STOPPED)
            startPlay();
        else if(player_status == PLAYER_STARTED)
            stopPlay();
        else
            Toast.makeText(this,"Invalid player status", Toast.LENGTH_LONG);
    }

    public void startPlay() {
        if(player_status == PLAYER_STARTED)
            return;

        startForegroundService();
        player_status = PLAYER_STARTED;

        Message message = new Message();
        message.what = PlayerQueue.MESSAGE_PLAY;
        playerQueue.sendMessage(message);
    }

    public void stopPlay() {
        if(player_status == PLAYER_STOPPED)
            return;
        stopForeground(false);
        player_status = PLAYER_STOPPED;

        Message message_stop = new Message();
        message_stop.what = PlayerQueue.MESSAGE_STOP;
        playerQueue.sendMessage(message_stop);
    }
}
