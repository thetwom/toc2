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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.SoundPool;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.support.v4.media.MediaMetadataCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
// import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static de.moekadu.metronome.App.CHANNEL_ID;

public class PlayerService extends Service {

    private final IBinder playerBinder = new PlayerBinder();

    private static final String BROADCAST_PLAYERACTION = "toc.PlayerService.PLAYERACTION";
    private static final String PLAYERSTATE = "PLAYERSTATE";
    private static final String PLAYBACKSPEED = "PLAYBACKSPEED";

    private int minimumSpeed = 20;
    private int maximumSpeed = 250;

    private long syncKlickTime = -1;

    private final int notificationID = 3252;

    private MediaSessionCompat mediaSession = null;
    private final PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
    private final MediaMetadataCompat.Builder mediaMetadataBuilder = new MediaMetadataCompat.Builder();

    private NotificationCompat.Builder notificationBuilder = null;

    private final SoundPool soundpool = new SoundPool.Builder().setMaxStreams(2).build();
    private int[] soundHandles;

    private int playListPosition = 0;

    private ArrayList<Bundle> playList;

    private final Handler waitHandler = new Handler();

    private final Runnable klickAndWait = new Runnable() {
        @Override
        public void run() {
            if (getState() == PlaybackStateCompat.STATE_PLAYING) {
//                // Log.v("Metronome", "PlayerService:Runnable: currentTime="+System.currentTimeMillis() + ";  nextClick=" + syncKlickTime);
                if(syncKlickTime > SystemClock.uptimeMillis()){
                    long nextKlickTime = syncKlickTime;
                    if(syncKlickTime - SystemClock.uptimeMillis() < 0.5 * getDt())
                        nextKlickTime += getDt();
                    waitHandler.postAtTime(this, nextKlickTime);
                    syncKlickTime = -1;
                }
                else {
                    waitHandler.postDelayed(this, getDt());
//                     waitHandler.postAtTime(this, SystemClock.uptimeMillis() +getDt());
                }

                if (playListPosition >= playList.size())
                    playListPosition = 0;
                int sound = 0;
                float volume = 1.0f;

                if (playList.size() > 0) {
                    sound = playList.get(playListPosition).getInt("soundid");
                    volume = playList.get(playListPosition).getFloat("volume");
                }

                if (!Sounds.isMute(sound))
                    soundpool.play(soundHandles[sound], volume, volume, 1, 0, 1.0f);

                playbackStateBuilder
                        .setState(getState(), playListPosition, getSpeed())
                        .build();
                mediaSession.setPlaybackState(playbackStateBuilder.build());

                playListPosition += 1;

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
            // Log.v("Metronome", "ActionReceiver:onReceive()");
            Bundle extras = intent.getExtras();
            if (extras == null) {
                return;
            }

            long myAction = extras.getLong(PLAYERSTATE, PlaybackStateCompat.STATE_NONE);
            int newSpeed = extras.getInt(PLAYBACKSPEED, -1);

            if (newSpeed > 0) {
                changeSpeed(newSpeed);
            }

            if (myAction == PlaybackStateCompat.ACTION_PLAY) {
                // Log.v("Metronome", "ActionReceiver:onReceive : set state to playing");
                mediaSession.getController().getTransportControls().play();
            } else if (myAction == PlaybackStateCompat.ACTION_PAUSE) {
                // Log.v("Metronome", "ActionReceiver:onReceive : set state to pause");
                mediaSession.getController().getTransportControls().pause();
            }
        }
    };

    public PlayerService() {
        super();
    }

    @Override
    public void onCreate() {
        // Log.v("Metronome", "PlayerService::onCreate()");
        super.onCreate();

        IntentFilter filter = new IntentFilter(BROADCAST_PLAYERACTION);
        registerReceiver(actionReceiver, filter);

        mediaSession = new MediaSessionCompat(this, "toc2");

        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent launchActivity = PendingIntent.getActivity(this, 0, activityIntent, 0);
        mediaSession.setSessionActivity(launchActivity);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                // Log.v("Metronome", "mediaSession:onPlay()");
                if (getState() != PlaybackStateCompat.STATE_PLAYING) {
                    startPlay();
                }
                super.onPlay();
            }

            @Override
            public void onPause() {
                // Log.v("Metronome", "mediaSession:onPause()");
                if (getState() == PlaybackStateCompat.STATE_PLAYING) {
                    stopPlay();
                }
                super.onPause();
            }
        });

        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, InitialValues.speed);
        //playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed);

        mediaSession.setPlaybackState(playbackStateBuilder.build());

        //mediaMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, SoundProperties.createMetaDataString(playList));
        //mediaSession.setMetadata(mediaMetadataBuilder.build());

        mediaSession.setActive(true);
    }

    @Override
    public void onDestroy() {
        // Log.v("Metronome", "PlayerService:onDestroy");
        unregisterReceiver(actionReceiver);
        mediaSession.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Log.v("Metronome", "PlayerService:onBind");

        int numSounds = Sounds.getNumSoundID();
        soundHandles = new int[numSounds];
        for (int i = 0; i < numSounds; ++i) {
            int soundID = Sounds.getSoundID(i);
            soundHandles[i] = soundpool.load(this, soundID, 1);
        }
        return playerBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Log.v("Metronome", "PlayerService:onUnbind");
        stopPlay();
        for (int sH : soundHandles) {
            soundpool.unload(sH);
        }

        return super.onUnbind(intent);
    }

    private Notification createNotification() {
        if (notificationBuilder == null) {
            notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);

            Intent activityIntent = new Intent(this, MainActivity.class);
            PendingIntent launchActivity = PendingIntent.getActivity(this, 0, activityIntent, 0);

            notificationBuilder.setContentTitle(getString(R.string.app_name))
                    .setSmallIcon(R.drawable.ic_toc_swb)
                    .setContentIntent(launchActivity)
                    .setStyle(new MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0));
        }

        Intent intent = new Intent(BROADCAST_PLAYERACTION);
        final int notificationStateID = 3214;

        NotificationCompat.Action controlAction;
        if (getState() == PlaybackStateCompat.STATE_PLAYING) {
            // Log.v("Metronome", "isplaying");
            intent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PAUSE);
            PendingIntent pIntent = PendingIntent.getBroadcast(this, notificationStateID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            controlAction = new NotificationCompat.Action(R.drawable.ic_pause, "pause", pIntent);
        } else { // if(getState() == PlaybackStateCompat.STATE_PAUSED){
            // Log.v("Metronome", "ispaused");
            intent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PLAY);
            PendingIntent pIntent = PendingIntent.getBroadcast(this, notificationStateID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
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

        notificationBuilder.setContentText(getString(R.string.bpm, getSpeed()));

        return notificationBuilder.build();
    }

    public void changeSpeed(int speed) {

        if (getSpeed() == speed || speed < minimumSpeed || speed > maximumSpeed)
            return;

        playbackStateBuilder
                .setState(getState(), PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed)
                .build();
        mediaSession.setPlaybackState(playbackStateBuilder.build());

        if (notificationBuilder != null) {
            notificationBuilder.setContentText(getString(R.string.bpm, getSpeed()));
            NotificationManagerCompat.from(this).notify(notificationID, notificationBuilder.build());
        }
    }

    public void addValueToSpeed(int dSpeed) {
        int newSpeed = getSpeed() + dSpeed;
//        newSpeed = Math.min(newSpeed, MainActivity.SPEED_MAX);
//        newSpeed = Math.max(newSpeed, MainActivity.SPEED_MIN);
        newSpeed = Math.min(newSpeed, maximumSpeed);
        newSpeed = Math.max(newSpeed, minimumSpeed);
        changeSpeed(newSpeed);
    }

    //public void changeSound(int activeSound){
    //    this.activeSound = activeSound;
    //}

    /// Delete this function
//    public void changeSound(int[] newPlayList){
//        playList = newPlayList;
//        String soundString = SoundProperties.createMetaDataString(newPlayList);
//        mediaMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, soundString);
//        mediaSession.setMetadata(mediaMetadataBuilder.build());
//    }

    public void setSounds(ArrayList<Bundle> sounds) {
        // Do not do anything if we already have the correct sounds
        if (SoundProperties.equal(sounds, playList))
            return;

        playList = sounds;
        String soundString = SoundProperties.createMetaDataString(playList);
        mediaMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, soundString);
        mediaSession.setMetadata(mediaMetadataBuilder.build());
    }

    public void startPlay() {
        // Log.v("Metronome", "PlayerService:startPlay");

        playbackStateBuilder
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, getSpeed())
                .build();
        mediaSession.setPlaybackState(playbackStateBuilder.build());

        startForeground(notificationID, createNotification());

        playListPosition = 0;
        waitHandler.post(klickAndWait);
    }

    public void stopPlay() {
        // Log.v("Metronome", "PlayerService:stopPlay");

        playbackStateBuilder
                .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, getSpeed())
                .build();
        mediaSession.setPlaybackState(playbackStateBuilder.build());

        stopForeground(false);

        waitHandler.removeCallbacksAndMessages(null);

        // Update notification only if it still exists (check is necessary, when app is canceled)
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager == null)
            return;

        StatusBarNotification[] notifications = notificationManager.getActiveNotifications();
        for (StatusBarNotification notification : notifications) {
            if (notification.getId() == notificationID) {
                // This is the line which updates the notification
                NotificationManagerCompat.from(this).notify(notificationID, createNotification());
            }
        }
    }

    public void syncKlickWithUptimeMillis(long time) {
        if(getState() == PlaybackStateCompat.STATE_PLAYING) {
            syncKlickTime = time;
//            waitHandler.removeCallbacksAndMessages(null);
//            waitHandler.postAtTime(klickAndWait, time);
        }
    }

    public void registerMediaControllerCallback(MediaControllerCompat.Callback callback) {
        mediaSession.getController().registerCallback(callback);
    }

    public void unregisterMediaControllerCallback(MediaControllerCompat.Callback callback) {
        mediaSession.getController().unregisterCallback(callback);
    }

    public PlaybackStateCompat getPlaybackState() {
        return mediaSession.getController().getPlaybackState();
    }

    public MediaMetadataCompat getMetaData() {
        return mediaSession.getController().getMetadata();
    }

    private long getDt() {
        return Math.round(1000.0 * 60.0 / mediaSession.getController().getPlaybackState().getPlaybackSpeed());
    }

    public int getSpeed() {
        return Math.round(mediaSession.getController().getPlaybackState().getPlaybackSpeed());
    }

    private int getState() {
        return mediaSession.getController().getPlaybackState().getState();
    }

    public ArrayList<Bundle> getSound() {
        return playList;
    }

    public boolean setMinimumSpeed(int speed) {
        if (speed >= maximumSpeed)
            return false;

        minimumSpeed = speed;
        if (getSpeed() < minimumSpeed) {
            changeSpeed(minimumSpeed);
        }
        return true;
    }

    public boolean setMaximumSpeed(int speed) {
        if (speed <= minimumSpeed)
            return false;

        maximumSpeed = speed;
        if (getSpeed() > maximumSpeed) {
            changeSpeed(maximumSpeed);
        }
        return true;
    }

    void playSpecificSound(int sound, float volume) {
        if (!Sounds.isMute(sound))
            soundpool.play(soundHandles[sound], volume, volume, 1, 0, 1.0f);
    }

    static public void sendPlayIntent(Context context){
        Intent intent = new Intent(PlayerService.BROADCAST_PLAYERACTION);
        intent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PLAY);
        context.sendBroadcast(intent);
    }

    static public void sendPauseIntent(Context context){
        Intent intent = new Intent(PlayerService.BROADCAST_PLAYERACTION);
        intent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PAUSE);
        context.sendBroadcast(intent);
    }

    static public void sendChangeSpeedIntent(Context context, int speed){
        Intent intent = new Intent(PlayerService.BROADCAST_PLAYERACTION);
        intent.putExtra(PlayerService.PLAYBACKSPEED, speed);
        context.sendBroadcast(intent);
    }
}
