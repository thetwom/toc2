package toc2.toc2;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.SoundPool;
import android.media.session.MediaSession;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.MediaSessionManager;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.MediaController;
import android.widget.RemoteViews;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static toc2.toc2.App.CHANNEL_ID;

public class PlayerService extends Service {

    private final IBinder playerBinder = new PlayerBinder();

    static public final String BROADCAST_PLAYERACTION = "toc.PlayerService.PLAYERACTION";
    static public final String PLAYERSTATE = "PLAYERSTATE";

    private final int notificationID = 3252;

    MediaSessionCompat mediaSession = null;
    PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();

    NotificationCompat.Builder notificationBuilder = null;

    private final SoundPool soundpool = new SoundPool.Builder().setMaxStreams(10).build();
    private int soundHandles[];

    private int activeSound = 0;
    private int speed = NavigationActivity.SPEED_INITIAL;
    private long dt = Math.round(1000.0 * 60.0 / speed);

    private final Handler waitHandler = new Handler();

    private final Runnable klickAndWait = new Runnable() {
        @Override
        public void run() {
            if(mediaSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) {
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

    private final BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v("Metronome", "ActionReceiver:onReceive()");
            Bundle extras = intent.getExtras();
            if(extras == null){
                return;
            }

            long myAction = extras.getLong(PLAYERSTATE, PlaybackStateCompat.STATE_ERROR);

            if (myAction == PlaybackStateCompat.ACTION_PLAY) {
                Log.v("Metronome", "ActionReceiver:onReceive : set state to playing");
                mediaSession.getController().getTransportControls().play();
            } else if (myAction == PlaybackStateCompat.ACTION_PAUSE) {
                Log.v("Metronome", "ActionReceiver:onReceive : set state to pause");
                mediaSession.getController().getTransportControls().pause();
            } else {
                Log.v("Metronome", "ActionReceiver:onReceive : set state to error");
                playbackStateBuilder.setState(PlaybackStateCompat.STATE_ERROR, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed);
                mediaSession.setPlaybackState(playbackStateBuilder.build());
            }
        }
    };

    public PlayerService() {
        super();
    }

    @Override
    public void onCreate() {
        Log.v("Metronome", "PlayerService::onCreate()");
        super.onCreate();

        //IntentFilter filter = new IntentFilter("blub");
        IntentFilter filter = new IntentFilter(BROADCAST_PLAYERACTION);
        registerReceiver(actionReceiver, filter);

        mediaSession = new MediaSessionCompat(this, "toc2");

        Intent activityIntent = new Intent(this, NavigationActivity.class);
        PendingIntent launchActivity = PendingIntent.getActivity(this, 0, activityIntent, 0);
        mediaSession.setSessionActivity(launchActivity);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                Log.v("Metronome", "mediaSession:onPlay()");
                if(mediaSession.getController().getPlaybackState().getState() != PlaybackStateCompat.STATE_PLAYING) {
                    startPlay();
                }
                super.onPlay();
            }

            @Override
            public void onPause() {
                Log.v("Metronome", "mediaSession:onPause()");
                if(mediaSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) {
                    stopPlay();
                }
                super.onPause();
            }
        });

        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_PLAY_PAUSE)
        //                    .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed);
        playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setActive(true);
    }

    @Override
    public void onDestroy() {
        Log.v("Metronome", "PlayerService:onDestroy");
        unregisterReceiver(actionReceiver);
        mediaSession.release();
        super.onDestroy();
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

    private Notification createNotification()
    {
        if(notificationBuilder == null) {
            notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);

            Intent activityIntent = new Intent(this, NavigationActivity.class);
            PendingIntent launchActivity = PendingIntent.getActivity(this, 0, activityIntent, 0);

            notificationBuilder.setContentTitle("Metronome")
                    .setSmallIcon(R.drawable.ic_toc_foreground)
                    .setContentIntent(launchActivity)
                    .setStyle(new MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0));
        }

        Intent intent = new Intent(BROADCAST_PLAYERACTION);
        final int notificationStateID = 3214;

        NotificationCompat.Action controlAction;
        if(mediaSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING){
            Log.v("Metronome","isplaying");
            intent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PAUSE);
            PendingIntent pIntent = PendingIntent.getBroadcast(this, notificationStateID , intent, PendingIntent.FLAG_UPDATE_CURRENT);
            controlAction = new NotificationCompat.Action(R.drawable.ic_pause, "pause", pIntent);
        }
        else{ // if(mediaSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_PAUSED){
            Log.v("Metronome","ispaused");
            intent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PLAY);
            PendingIntent pIntent = PendingIntent.getBroadcast(this, notificationStateID , intent, PendingIntent.FLAG_UPDATE_CURRENT);
            controlAction = new NotificationCompat.Action(R.drawable.ic_play, "play", pIntent);
        }

        // Clear actions: code copies from stackoverflow
        try {
            //Use reflection clean up old actions
            Field f = notificationBuilder.getClass().getDeclaredField("mActions");
            f.setAccessible(true);
            f.set(notificationBuilder, new ArrayList<NotificationCompat.Action>());
        } catch (NoSuchFieldException e) {
            // no field
        } catch (IllegalAccessException e) {
            // wrong types
        }

        notificationBuilder.addAction(controlAction);

        notificationBuilder.setContentText(Integer.toString(speed) + " bpm");

        return notificationBuilder.build();
    }

    public void changeSpeed(int speed){
        this.speed = speed;
        NotificationManagerCompat.from(this).notify(notificationID, createNotification());
        this.dt = Math.round(1000.0 * 60.0 / speed);
    }

    public void changeSound(int activeSound){
        this.activeSound = activeSound;
    }

    private void startPlay() {
        Log.v("Metronome", "PlayerService:startPlay");

        playbackStateBuilder
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed)
                .build();
        mediaSession.setPlaybackState(playbackStateBuilder.build());

        startForeground(notificationID, createNotification());

        waitHandler.post(klickAndWait);
    }

    private void stopPlay() {
        Log.v("Metronome", "PlayerService:stopPlay");

        playbackStateBuilder
                .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed)
                .build();
        mediaSession.setPlaybackState(playbackStateBuilder.build());
        NotificationManagerCompat.from(this).notify(notificationID, createNotification());

        stopForeground(false);

        waitHandler.removeCallbacksAndMessages(null);
    }

    public void registerMediaControllerCallback(MediaControllerCompat.Callback callback){
        mediaSession.getController().registerCallback(callback);
    }

    public void unregisterMediaControllerCallback(MediaControllerCompat.Callback callback){
        mediaSession.getController().unregisterCallback(callback);
    }

    public PlaybackStateCompat getPlaybackState(){
        return mediaSession.getController().getPlaybackState();
    }
}
