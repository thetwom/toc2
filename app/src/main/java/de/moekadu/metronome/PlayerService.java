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
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.AudioManagerCompat;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.RemoteViews;
// import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static de.moekadu.metronome.App.CHANNEL_ID;

public class PlayerService extends Service {

    private final IBinder playerBinder = new PlayerBinder();

    private static final String BROADCAST_PLAYERACTION = "toc.PlayerService.PLAYERACTION";
    private static final String PLAYERSTATE = "PLAYERSTATE";
    private static final String PLAYBACKSPEED = "PLAYBACKSPEED";
    private static final String INCREMENTSPEED = "INCREMENTSPEED";
    private static final String DECREMENTSPEED = "DECREMENTSPEED";

    private static int nativeSampleRate = -1;

    private float minimumSpeed;
    private float maximumSpeed;

    private float speedIncrement;

    private long syncKlickTime = -1;

    private final int notificationID = 3252;

    private MediaSessionCompat mediaSession = null;
    private final PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
    private PlaybackStateCompat playbackState = playbackStateBuilder.build();
    private final MediaMetadataCompat.Builder mediaMetadataBuilder = new MediaMetadataCompat.Builder();
    private MediaMetadataCompat metaData = mediaMetadataBuilder.build();

    private NotificationCompat.Builder notificationBuilder = null;
    private RemoteViews notificationView = null;
//    private TextView notificationSpeedText = null;

    private final SoundPool soundpool = new SoundPool.Builder().setMaxStreams(3).build();
    private int[] soundHandles;

    private int playListPosition = 0;

    private final ArrayList<Bundle> playList = new ArrayList<>();

    private SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;
    private final Handler waitHandler = new Handler();

    private final Runnable klickAndWait = new Runnable() {
        @Override
        public void run() {
            if (getState() == PlaybackStateCompat.STATE_PLAYING) {
                long dt = Utilities.speed2dt(getSpeed());
//                // Log.v("Metronome", "PlayerService:Runnable: currentTime="+System.currentTimeMillis() + ";  nextClick=" + syncKlickTime);
                if(syncKlickTime > SystemClock.uptimeMillis()){
                    long nextKlickTime = syncKlickTime;
                    if(syncKlickTime - SystemClock.uptimeMillis() < 0.5 * dt)
                        nextKlickTime += dt;
                    waitHandler.postAtTime(this, nextKlickTime);
                    syncKlickTime = -1;
                }
                else {
                    waitHandler.postDelayed(this, dt);
//                     waitHandler.postAtTime(this, SystemClock.uptimeMillis() + dt);
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

                playbackState = playbackStateBuilder.setState(getState(), playListPosition, getSpeed()).build();
                mediaSession.setPlaybackState(playbackState);
                //Log.v("Metronome", "positionindex: " + playbackState.getPosition()  + "     " + playListPosition);

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
//            Log.v("Metronome", "ActionReceiver:onReceive()");
            Bundle extras = intent.getExtras();
            if (extras == null) {
                return;
            }

            long myAction = extras.getLong(PLAYERSTATE, PlaybackStateCompat.STATE_NONE);
            float newSpeed = extras.getFloat(PLAYBACKSPEED, -1);
            boolean incrementSpeed = extras.getBoolean(INCREMENTSPEED, false);
            boolean decrementSpeed = extras.getBoolean(DECREMENTSPEED, false);

            if (newSpeed > 0) {
                changeSpeed(newSpeed);
            }

            if (incrementSpeed) {
                changeSpeed(getSpeed() + speedIncrement);
            }

            if (decrementSpeed) {
                changeSpeed(getSpeed() - speedIncrement);
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

        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        String sampleRateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        nativeSampleRate = Integer.parseInt(sampleRateString);
//        Log.v("Metronome", "PlayerService:onCreate: native sample rate: " + nativeSampleRate);

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

//        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
//                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, InitialValues.speed);
        //playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed);

        playbackState = playbackStateBuilder.build();
        mediaSession.setPlaybackState(playbackState);

        //mediaMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, SoundProperties.createMetaDataString(playList));
        //mediaSession.setMetadata(mediaMetadataBuilder.build());

        mediaSession.setActive(true);

        sharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if(key.equals("minimumspeed")){
                    String newMinimumSpeed = sharedPreferences.getString("minimumspeed", Float.toString(InitialValues.minimumSpeed));
                    assert newMinimumSpeed != null;
                    setMinimumSpeed(Float.parseFloat(newMinimumSpeed));
                }
                else if(key.equals("maximumspeed")){
                    String newMaximumSpeed = sharedPreferences.getString("maximumspeed", Float.toString(InitialValues.maximumSpeed));
                    assert newMaximumSpeed != null;
                    setMaximumSpeed(Float.parseFloat(newMaximumSpeed));
                }
                else if(key.equals("speedincrement")){
//                    String newSpeedIncrement = sharedPreferences.getString("speedincrement", "1");
                    int newSpeedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex);
//                    assert newSpeedIncrement != null;
                    float newSpeedIncrement = Utilities.speedIncrements[newSpeedIncrementIndex];
                    setSpeedIncrement(newSpeedIncrement);
                }
            }
        };

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        minimumSpeed = Float.parseFloat(Objects.requireNonNull(sharedPreferences.getString("minimumspeed", Float.toString(InitialValues.minimumSpeed))));
        maximumSpeed = Float.parseFloat(Objects.requireNonNull(sharedPreferences.getString("maximumspeed", Float.toString(InitialValues.maximumSpeed))));
//        speedIncrement = Float.parseFloat(sharedPreferences.getString("speedincrement", "1"));
        int speedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex);
        speedIncrement = Utilities.speedIncrements[speedIncrementIndex];
//        Log.v("Metronome", "PlayerService:onCreate: speedIncrement=" + speedIncrement);
    }

    @Override
    public void onDestroy() {
        // Log.v("Metronome", "PlayerService:onDestroy");
        unregisterReceiver(actionReceiver);
        mediaSession.release();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Log.v("Metronome", "PlayerService:onBind");

        int numSounds = Sounds.getNumSoundID();
        soundHandles = new int[numSounds];
        for (int i = 0; i < numSounds; ++i) {
            int soundID = Sounds.getSoundID(i, nativeSampleRate);
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
            notificationView = new RemoteViews(getPackageName(), R.layout.notification);

            Intent activityIntent = new Intent(this, MainActivity.class);
            PendingIntent launchActivity = PendingIntent.getActivity(this, 0, activityIntent, 0);

            notificationBuilder.setContentTitle(getString(R.string.app_name))
                    .setSmallIcon(R.drawable.ic_toc_swb)
                    .setContentIntent(launchActivity)
                    .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(notificationView);
                    //.setStyle(new MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0));
        }
        notificationView.setTextViewText(R.id.notification_speedtext, getString(R.string.bpm, Utilities.getBpmString(getSpeed(), speedIncrement)));

        Intent intent = new Intent(BROADCAST_PLAYERACTION);
        final int notificationStateID = 3214;

//        NotificationCompat.Action controlAction;
        if (getState() == PlaybackStateCompat.STATE_PLAYING) {
            // Log.v("Metronome", "isplaying");
             notificationView.setImageViewResource(R.id.notification_button, R.drawable.ic_pause2);
            intent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PAUSE);
            PendingIntent pIntent = PendingIntent.getBroadcast(this, notificationStateID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            notificationView.setOnClickPendingIntent(R.id.notification_button, pIntent);
//            controlAction = new NotificationCompat.Action(R.drawable.ic_pause, "pause", pIntent);
        } else { // if(getState() == PlaybackStateCompat.STATE_PAUSED){
            // Log.v("Metronome", "ispaused");
            notificationView.setImageViewResource(R.id.notification_button, R.drawable.ic_play2);
            intent.putExtra(PlayerService.PLAYERSTATE, PlaybackStateCompat.ACTION_PLAY);
            PendingIntent pIntent = PendingIntent.getBroadcast(this, notificationStateID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            notificationView.setOnClickPendingIntent(R.id.notification_button, pIntent);
//            controlAction = new NotificationCompat.Action(R.drawable.ic_play, "play", pIntent);
        }

        notificationView.setTextViewText(R.id.notification_button_p, "   + " + Utilities.getBpmString(speedIncrement,speedIncrement) + " ");
        Intent incrIntent =  new Intent(BROADCAST_PLAYERACTION);
        incrIntent.putExtra(PlayerService.INCREMENTSPEED, true);
        PendingIntent pIncrIntent = PendingIntent.getBroadcast(this, notificationStateID+1, incrIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationView.setOnClickPendingIntent(R.id.notification_button_p_toucharea, pIncrIntent);

        notificationView.setTextViewText(R.id.notification_button_m, " - " + Utilities.getBpmString(speedIncrement,speedIncrement) + "   ");
        Intent decrIntent =  new Intent(BROADCAST_PLAYERACTION);
        decrIntent.putExtra(PlayerService.DECREMENTSPEED, true);
        PendingIntent pDecrIntent = PendingIntent.getBroadcast(this, notificationStateID+2, decrIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationView.setOnClickPendingIntent(R.id.notification_button_m_toucharea, pDecrIntent);

//        // Clear actions: code copies from stackoverflow
//        try {
//            //Use reflection clean up old actions
//            Field f = notificationBuilder.getClass().getDeclaredField("mActions");
//            f.setAccessible(true);
//            f.set(notificationBuilder, new ArrayList<NotificationCompat.Action>());
//        } catch (NoSuchFieldException e) {
//            // no field
//        } catch (IllegalAccessException e) {
//            // wrong types
//        }
//
//        notificationBuilder.addAction(controlAction);
//
//        notificationBuilder.setContentText(getString(R.string.bpm, Utilities.getBpmString(getSpeed(), speedIncrement)));

        return notificationBuilder.build();
    }

    public void changeSpeed(float speed) {

        final float tolerance = 1.0e-6f;

        speed = Math.min(speed, maximumSpeed);
        speed = Math.max(speed, minimumSpeed);
        // Make speed match the increment
        speed = Math.round(speed / speedIncrement) * speedIncrement;

        if (Math.abs(getSpeed() - speed) < tolerance)
            return;

        if(speed < minimumSpeed - tolerance)
            speed += speedIncrement;
        if(speed > maximumSpeed + tolerance)
            speed -= speedIncrement;

        playbackState = playbackStateBuilder.setState(getState(), PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed).build();
        mediaSession.setPlaybackState(playbackState);

        if (notificationBuilder != null) {
//            notificationBuilder.setContentText(getString(R.string.bpm, Utilities.getBpmString(getSpeed(), speedIncrement)));
            notificationView.setTextViewText(R.id.notification_speedtext, getString(R.string.bpm, Utilities.getBpmString(getSpeed(), speedIncrement)));
            NotificationManagerCompat.from(this).notify(notificationID, notificationBuilder.build());
        }
    }

    public void addValueToSpeed(float dSpeed) {
        float newSpeed = getSpeed() + dSpeed;
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

    private void updateMetadata() {
        String soundString = SoundProperties.createMetaDataString(playList);
//        Log.v("Metronome", "PlayerService:setSounds: " + soundString);
        metaData = mediaMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, soundString).build();
        mediaSession.setMetadata(metaData);
    }

    public void setSounds(List<Bundle> sounds) {
//        Log.v("Metronome", "PlayerService:setSounds");
        // Do not do anything if we already have the correct sounds
        if (SoundProperties.equal(sounds, playList)) {
//            Log.v("Metronome", "PlayerService:setSounds: new sounds are equal to old sounds");
            return;
        }

        playList.clear();
        for(Bundle b : sounds)
            playList.add(SoundProperties.deepCopy(b));
        updateMetadata();
//        playList = sounds;
//        String soundString = SoundProperties.createMetaDataString(playList);
//        Log.v("Metronome", "PlayerService:setSounds: " + soundString);
//        metaData = mediaMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, soundString).build();
//        mediaSession.setMetadata(metaData);
    }

    public void setVolume(int playListIndex, float volume) {
        boolean volumeChanged = false;
        if(playListIndex < playList.size()) {
            Bundle b = playList.get(playListIndex);
            float oldVolume = b.getFloat("volume");
            if(Math.abs(volume-oldVolume) > 1e-8) {
                b.putFloat("volume", volume);
                volumeChanged = true;
            }
        }

        if(volumeChanged) {
            updateMetadata();
        }
    }

    public void startPlay() {
        // Log.v("Metronome", "PlayerService:startPlay");

        playbackState = playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, getSpeed()).build();
        mediaSession.setPlaybackState(playbackState);

        startForeground(notificationID, createNotification());

        playListPosition = 0;
        waitHandler.post(klickAndWait);
    }

    public void stopPlay() {
        // Log.v("Metronome", "PlayerService:stopPlay");

        playbackState = playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, getSpeed()).build();
        mediaSession.setPlaybackState(playbackState);

        stopForeground(false);

        waitHandler.removeCallbacksAndMessages(null);

        updateNotification();
//        // Update notification only if it still exists (check is necessary, when app is canceled)
//        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//        if (notificationManager == null)
//            return;
//
//        StatusBarNotification[] notifications = notificationManager.getActiveNotifications();
//        for (StatusBarNotification notification : notifications) {
//            if (notification.getId() == notificationID) {
//                // This is the line which updates the notification
//                NotificationManagerCompat.from(this).notify(notificationID, createNotification());
//            }
//        }
    }

    private void updateNotification() {
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

        return playbackState;
    }

    public MediaMetadataCompat getMetaData() {
        return metaData;
    }

    public float getSpeed() {
        return playbackState.getPlaybackSpeed();
    }

    public int getState() {
        return playbackState.getState();
    }

    public List<Bundle> getSound() {
        return Collections.unmodifiableList(playList);
    }

    private boolean setMinimumSpeed(float speed) {
        if (speed >= maximumSpeed)
            return false;

        minimumSpeed = speed;
        if (getSpeed() < minimumSpeed) {
            changeSpeed(minimumSpeed);
        }
        return true;
    }

    private boolean setMaximumSpeed(float speed) {
        if (speed <= minimumSpeed)
            return false;

        maximumSpeed = speed;
        if (getSpeed() > maximumSpeed) {
            changeSpeed(maximumSpeed);
        }
        return true;
    }

    private void setSpeedIncrement(float speedIncrement) {
        this.speedIncrement = speedIncrement;
        changeSpeed(getSpeed()); // Make sure that current speed fits speedIncrement
        updateNotification();
    }

    void playSpecificSound(int playListPosition) {
        if(playListPosition >= playList.size())
            return;
        Bundle b = playList.get(playListPosition);
        playSpecificSound(b.getInt("soundid"), b.getFloat("volume"));
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

    static public void sendChangeSpeedIntent(Context context, float speed){
        Intent intent = new Intent(PlayerService.BROADCAST_PLAYERACTION);
        intent.putExtra(PlayerService.PLAYBACKSPEED, speed);
        context.sendBroadcast(intent);
    }
}
