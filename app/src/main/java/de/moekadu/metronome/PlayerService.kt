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

package de.moekadu.metronome

// import android.util.Log

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.SoundPool
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import de.moekadu.metronome.App.CHANNEL_ID
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PlayerService : Service() {

    private val playerBinder = PlayerBinder()

    companion object {
        private const val BROADCAST_PLAYERACTION = "toc.PlayerService.PLAYERACTION"
        private const val PLAYERSTATE = "PLAYERSTATE"
        private const val PLAYBACKSPEED = "PLAYBACKSPEED"
        private const val INCREMENTSPEED = "INCREMENTSPEED"
        private const val DECREMENTSPEED = "DECREMENTSPEED"

//        fun sendPlayIntent(context: Context) {
//            val intent = Intent(BROADCAST_PLAYERACTION)
//            intent.putExtra(PLAYERSTATE, PlaybackStateCompat.ACTION_PLAY)
//            context.sendBroadcast(intent)
//        }
//
//        fun sendPauseIntent(context: Context) {
//            val intent = Intent(BROADCAST_PLAYERACTION)
//            intent.putExtra(PLAYERSTATE, PlaybackStateCompat.ACTION_PAUSE)
//            context.sendBroadcast(intent)
//        }
//
//        fun sendChangeSpeedIntent(context: Context, speed: Float) {
//            val intent = Intent(BROADCAST_PLAYERACTION)
//            intent.putExtra(PLAYBACKSPEED, speed)
//            context.sendBroadcast(intent)
//        }
    }

    interface StatusChangedListener {
        fun onPlay()
        fun onPause()
        fun onNoteStarted(noteListItem: NoteListItem)
        fun onSpeedChanged(speed : Float)
        fun onNoteListChanged(noteList : NoteList)
    }

    private val statusChangedListeners = mutableSetOf<StatusChangedListener>()

    private var minimumSpeed = 0f
        set(value) {
            if(value > maximumSpeed)
                return
            field = value
            if (speed < minimumSpeed)
                speed = minimumSpeed
        }

    private var maximumSpeed = Float.MAX_VALUE
        set(value) {
            if(value < minimumSpeed)
                return
            field = value
            if (speed > maximumSpeed)
                speed = maximumSpeed
        }

    private var speedIncrement = 0f
        set(value) {
            field = value
            speed = speed // Make sure that current speed fits speedIncrement (so reassigning is intentionally here)
            updateNotification()
        }

    private val notificationID = 3252

    private var mediaSession : MediaSessionCompat? = null
    private val playbackStateBuilder = PlaybackStateCompat.Builder()
    var playbackState: PlaybackStateCompat = playbackStateBuilder.build()
        private set
    private val mediaMetadataBuilder = MediaMetadataCompat.Builder()
    private var notificationView : RemoteViews? = null
    private var notificationBuilder : NotificationCompat.Builder? = null

    //    private TextView notificationSpeedText = null

    /// Sound pool which is used for playing sample sounds when selected in the sound chooser.
    private val soundPool = SoundPool.Builder().setMaxStreams(3).build()
    /// Handles of the available sound used by the sound pool.
    private val soundHandles = ArrayList<Int>()

    /// The audio mixer plays is the instance which does plays the metronome.
    private var audioMixer : AudioMixer? = null

    /// The current note list played by the metronome. This list shares its items with all the other classes
    var noteList = NoteList()
        set(value) {
            val instancesChangedFlag = !noteList.compareNoteListItemInstances(value)

            if (instancesChangedFlag) {
                field.clear()
                field.addAll(value)
            }

            // if instances changed we force the note list update (update noteView, call noteListChangedListener)
            applyNoteList(instancesChangedFlag)
        }


    /// This is a copy of the note list which items are only owned by this service.
    /**
     * This allows to check if something changed when the note list ist checked
     */
    private val noteListCopy = NoteList()

    private var sharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null
    
    inner class PlayerBinder : Binder() {
        val service
            get() = this@PlayerService
    }

    /// Receiver, which allows to control the metronome via sending intents
    private val actionReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
//            Log.v("Metronome", "ActionReceiver:onReceive()");
            val extras = intent?.extras ?: return

            val myAction = extras.getLong(PLAYERSTATE, PlaybackStateCompat.STATE_NONE.toLong())
            val newSpeed = extras.getFloat(PLAYBACKSPEED, -1f)
            val incrementSpeed = extras.getBoolean(INCREMENTSPEED, false)
            val decrementSpeed = extras.getBoolean(DECREMENTSPEED, false)

            if (newSpeed > 0)
                speed = newSpeed
            if (incrementSpeed)
                speed += speedIncrement
            if (decrementSpeed)
                speed -= speedIncrement

            if (myAction == PlaybackStateCompat.ACTION_PLAY) {
                // Log.v("Metronome", "ActionReceiver:onReceive : set state to playing");
                //mediaSession.controller?.transportControls?.play()
                startPlay()
            } else if (myAction == PlaybackStateCompat.ACTION_PAUSE) {
                // Log.v("Metronome", "ActionReceiver:onReceive : set state to pause");
                //mediaSession.controller?.transportControls?.pause()
                stopPlay()
            }
        }
    }

    override fun onCreate() {
        // Log.v("Metronome", "PlayerService::onCreate()")
        super.onCreate()

        val filter = IntentFilter(BROADCAST_PLAYERACTION)
        registerReceiver(actionReceiver, filter)

        audioMixer = AudioMixer(applicationContext)
        audioMixer?.setNoteStartedListener(object : AudioMixer.NoteStartedListener {
            override fun onNoteStarted(noteListItem: NoteListItem?) {
                if(noteListItem != null)
                    statusChangedListeners.forEach {s -> s.onNoteStarted(noteListItem)}

//                var pos = 0
//                for(i in noteList.indices) {
//                    if (noteList[i] == noteListItem) {
//                        pos = i
//                        break
//                    }
//                }
//
//                if(pos < noteList.size) {
//                    playbackState = playbackStateBuilder.setState(state, pos.toLong(), speed).build()
//                    mediaSession.setPlaybackState(playbackState)
//                }
            }
        })

        val activityIntent = Intent(this, MainActivity::class.java)
        val launchActivity = PendingIntent.getActivity(this, 0, activityIntent, 0)

        notificationView = RemoteViews(packageName, R.layout.notification)
        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            val actIntent = Intent(this@PlayerService, MainActivity::class.java)
            val launchAct = PendingIntent.getActivity(this@PlayerService, 0, actIntent, 0)
            setContentTitle(getString(R.string.app_name))
                    .setSmallIcon(R.drawable.ic_toc_swb)
                    .setContentIntent(launchAct)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(notificationView)
        }

        mediaSession = MediaSessionCompat(this, "toc2") // TODO: change tag to de.moekadu.metronome
        mediaSession?.setSessionActivity(launchActivity)

        mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                // Log.v("Metronome", "mediaSession:onPlay()");
                if (state != PlaybackStateCompat.STATE_PLAYING) {
                    startPlay()
                }
                super.onPlay()
            }

            override fun onPause() {
                // Log.v("Metronome", "mediaSession:onPause()");
                if (state == PlaybackStateCompat.STATE_PLAYING) {
                    stopPlay()
                }
                super.onPause()
            }
        })

//        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
//                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, InitialValues.speed)
        //playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed);

        playbackState = playbackStateBuilder.build()
        mediaSession?.setPlaybackState(playbackState)

        //mediaMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, SoundProperties.createMetaDataString(playList));
        //mediaSession.setMetadata(mediaMetadataBuilder.build());

        mediaSession?.isActive = true

        sharedPreferenceChangeListener = object : OnSharedPreferenceChangeListener {

            override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
                if(sharedPreferences == null || key == null)
                    return
                when (key) {
                    "minimumspeed" -> {
                        val newMinimumSpeed = sharedPreferences.getString("minimumspeed", InitialValues.minimumSpeed.toString())
                        minimumSpeed = newMinimumSpeed!!.toFloat()
                    }
                    "maximumspeed" -> {
                        val newMaximumSpeed = sharedPreferences.getString("maximumspeed", InitialValues.maximumSpeed.toString())
                        maximumSpeed = newMaximumSpeed!!.toFloat()
                    }
                    "speedincrement" -> {
                        val newSpeedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex)
                        val newSpeedIncrement = Utilities.speedIncrements[newSpeedIncrementIndex]
                        speedIncrement = newSpeedIncrement
                    }
                }
            }
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

        minimumSpeed = sharedPreferences.getString("minimumspeed", InitialValues.minimumSpeed.toString())!!.toFloat()
        maximumSpeed = sharedPreferences.getString("maximumspeed", InitialValues.maximumSpeed.toString())!!.toFloat()
        val speedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex)
        speedIncrement = Utilities.speedIncrements[speedIncrementIndex]
    }

    override fun onDestroy() {
        unregisterReceiver(actionReceiver)
        mediaSession?.release()
        mediaSession = null

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Log.v("Metronome", "PlayerService:onBind")

        val numSounds = getNumAvailableNotes()
        for (i in 0 until numSounds) {
            val soundID = getNoteAudioResourceID(i)
            soundHandles.add(soundPool.load(this, soundID, 1))
        }
        return playerBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Log.v("Metronome", "PlayerService:onUnbind");
        stopPlay()
        for (sH in soundHandles) {
            soundPool.unload(sH)
        }
        return super.onUnbind(intent)
    }

    private fun createNotification() : Notification? {

        notificationView?.setTextViewText(R.id.notification_speedtext, getString(R.string.bpm, Utilities.getBpmString(speed, speedIncrement)))

        val intent = Intent(BROADCAST_PLAYERACTION)
        val notificationStateID = 3214

//        NotificationCompat.Action controlAction;
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            // Log.v("Metronome", "isplaying");
             notificationView?.setImageViewResource(R.id.notification_button, R.drawable.ic_pause2)
            intent.putExtra(PLAYERSTATE, PlaybackStateCompat.ACTION_PAUSE)
            val pIntent = PendingIntent.getBroadcast(this, notificationStateID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            notificationView?.setOnClickPendingIntent(R.id.notification_button, pIntent)
        }
        else { // if(getState() == PlaybackStateCompat.STATE_PAUSED){
            // Log.v("Metronome", "ispaused");
            notificationView?.setImageViewResource(R.id.notification_button, R.drawable.ic_play2)
            intent.putExtra(PLAYERSTATE, PlaybackStateCompat.ACTION_PLAY)
            val pIntent = PendingIntent.getBroadcast(this, notificationStateID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            notificationView?.setOnClickPendingIntent(R.id.notification_button, pIntent)
        }

        notificationView?.setTextViewText(R.id.notification_button_p, "   + " + Utilities.getBpmString(speedIncrement,speedIncrement) + " ")
        val incrIntent = Intent(BROADCAST_PLAYERACTION)
        incrIntent.putExtra(INCREMENTSPEED, true)
        val pIncrIntent = PendingIntent.getBroadcast(this, notificationStateID+1, incrIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        notificationView?.setOnClickPendingIntent(R.id.notification_button_p_toucharea, pIncrIntent)

        notificationView?.setTextViewText(R.id.notification_button_m, " - " + Utilities.getBpmString(speedIncrement,speedIncrement) + "   ")
        val decrIntent = Intent(BROADCAST_PLAYERACTION)
        decrIntent.putExtra(DECREMENTSPEED, true)
        val pDecrIntent = PendingIntent.getBroadcast(this, notificationStateID+2, decrIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        notificationView?.setOnClickPendingIntent(R.id.notification_button_m_toucharea, pDecrIntent)

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

        return notificationBuilder?.build()
    }

    var speed = InitialValues.speed
        set(value) {
            val tolerance = 1.0e-6f
            var newSpeed = value
            newSpeed = min(newSpeed, maximumSpeed)
            newSpeed = max(newSpeed, minimumSpeed)
            // Make speed match the increment
            newSpeed = (newSpeed / speedIncrement).roundToInt() * speedIncrement

            if(newSpeed < minimumSpeed - tolerance)
                newSpeed += speedIncrement
            if(newSpeed > maximumSpeed + tolerance)
                newSpeed -= speedIncrement

            if (abs(field - newSpeed) < tolerance)
                return

            field = newSpeed
            // playbackState = playbackStateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, newSpeed).build()
            // mediaSession.setPlaybackState(playbackState)
            statusChangedListeners.forEach {s -> s.onSpeedChanged(field)}
            setNoteListInAudioMixer()

//            notificationBuilder.setContentText(getString(R.string.bpm, Utilities.getBpmString(getSpeed(), speedIncrement)));
            notificationView?.setTextViewText(R.id.notification_speedtext, getString(R.string.bpm, Utilities.getBpmString(field, speedIncrement)))
            notificationBuilder?.build()?.let { NotificationManagerCompat.from(this).notify(notificationID, it) }
        }

    val state
        get() = playbackState.state

//    fun changeSpeed(speed : Float) {
//
//        val tolerance = 1.0e-6f
//        var newSpeed = speed
//        newSpeed = min(newSpeed, maximumSpeed)
//        newSpeed = max(newSpeed, minimumSpeed)
//        // Make speed match the increment
//        newSpeed = (newSpeed / speedIncrement).roundToInt() * speedIncrement
//
//        if (abs(getSpeed() - newSpeed) < tolerance)
//            return
//
//        if(newSpeed < minimumSpeed - tolerance)
//            newSpeed += speedIncrement
//        if(newSpeed > maximumSpeed + tolerance)
//            newSpeed -= speedIncrement
//
//        playbackState = playbackStateBuilder.setState(getState(), PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, newSpeed).build()
//        mediaSession.setPlaybackState(playbackState)
//
//        setMissingMembersAndCopyPlayListToAudioMixer()
//
////            notificationBuilder.setContentText(getString(R.string.bpm, Utilities.getBpmString(getSpeed(), speedIncrement)));
//        notificationView.setTextViewText(R.id.notification_speedtext, getString(R.string.bpm, Utilities.getBpmString(getSpeed(), speedIncrement)))
//        NotificationManagerCompat.from(this).notify(notificationID, notificationBuilder.build())
//    }

    fun addValueToSpeed(dSpeed : Float) {
        var newSpeed = speed + dSpeed
//        newSpeed = Math.min(newSpeed, MainActivity.SPEED_MAX);
//        newSpeed = Math.max(newSpeed, MainActivity.SPEED_MIN);
        newSpeed = min(newSpeed, maximumSpeed)
        newSpeed = max(newSpeed, minimumSpeed)
        speed = newSpeed
    }

//    private fun updateMetadata() {
//        val noteListString = SoundProperties.createMetaDataString(noteList)
//        metaData = mediaMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, noteListString).build()
//        mediaSession.setMetadata(metaData)
//    }

//    private fun updateMetadataDelayed() {
//        val delayMillis = 5L
//        delayedMetaUpdateHandler.removeCallbacksAndMessages(null)
//        delayedMetaUpdateHandler.postDelayed(delayedMetaUpdate, delayMillis)
//    }

//    fun setSounds(sounds : NoteList) {
////        Log.v("Metronome", "PlayerService:setSounds");
//
//        // Do not do anything if we already have the correct sounds
//        if (SoundProperties.noteIdAndVolumeEqual(sounds, noteList)) {
////            Log.v("Metronome", "PlayerService:setSounds: new sounds are equal to old sounds");
//            return
//        }
//        noteList.clear()
//        noteList.addAll(sounds)
//
//        setMissingMembersAndCopyPlayListToAudioMixer()
//
////        updateMetadata();
//        // Delay update a little, to avoid any feedback loop with updates of other instances
//        updateMetadataDelayed()
////        playList = sounds;
////        String soundString = SoundProperties.createMetaDataString(playList);
////        Log.v("Metronome", "PlayerService:setSounds: " + soundString);
////        metaData = mediaMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, soundString).build();
////        mediaSession.setMetadata(metaData);
//    }

     fun setVolume(noteListIndex : Int, volume : Float) {
         var volumeChanged = false
         if(noteListIndex < noteList.size) {
             val oldVolume = noteList[noteListIndex].volume
             if(abs(volume - oldVolume) > 1e-8) {
                 volumeChanged = true
                 noteList[noteListIndex].volume = volume
                 noteListCopy[noteListIndex].volume = volume
             }
         }

         if(volumeChanged) {
             setNoteListInAudioMixer()
             statusChangedListeners.forEach {s -> s.onNoteListChanged(noteList)}
//            updateMetadata();
             // Delay update a little, to avoid any feedback loop with updates of other instances
//             updateMetadataDelayed()
         }
     }

    fun startPlay() {
        // Log.v("Metronome", "PlayerService:startPlay")
        playbackState = playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed).build()
        mediaSession?.setPlaybackState(playbackState)

        startForeground(notificationID, createNotification())

        audioMixer?.start()
        statusChangedListeners.forEach {s -> s.onPlay()}
    }

    fun stopPlay() {
        // Log.v("Metronome", "PlayerService:stopPlay")

        playbackState = playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed).build()
        mediaSession?.setPlaybackState(playbackState)

        stopForeground(false)

        // waitHandler.removeCallbacksAndMessages(null)
        audioMixer?.stop()

        statusChangedListeners.forEach {s -> s.onPause()}
        updateNotification()
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

    private fun updateNotification() {
        // Update notification only if it still exists (check is necessary, when app is canceled)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
                ?: return

        val notifications = notificationManager.activeNotifications
        for (notification in notifications) {
            if (notification.id == notificationID) {
                // This is the line which updates the notification
                createNotification()?.let { NotificationManagerCompat.from(this).notify(notificationID, it) }
            }
        }
    }

    fun syncClickWithUptimeMillis(time : Long) {
        if(state == PlaybackStateCompat.STATE_PLAYING) {
            audioMixer?.synchronizeTime(time, Utilities.speed2dt(speed) / 1000.0f)
        }
    }

//    fun registerMediaControllerCallback(callback : MediaControllerCompat.Callback) {
//        mediaSession.controller.registerCallback(callback)
//    }
//
//    fun unregisterMediaControllerCallback(callback : MediaControllerCompat.Callback) {
//        mediaSession.controller.unregisterCallback(callback)
//    }

    fun registerStatusChangedListener(statusChangedListener: StatusChangedListener) {
        statusChangedListeners.add(statusChangedListener)
    }

    fun unregisterStatusChangedListener(statusChangedListener: StatusChangedListener) {
        statusChangedListeners.remove(statusChangedListener)
    }
//    fun getSpeed() : Float {
//        return playbackState.playbackSpeed
//    }

//    private fun setMinimumSpeed(newMinimumSpeed : Float) : Boolean {
//        if (newMinimumSpeed >= maximumSpeed)
//            return false
//
//        minimumSpeed = newMinimumSpeed
//        if (speed < minimumSpeed) {
//            speed = minimumSpeed
//        }
//        return true
//    }

//    private fun setMaximumSpeed(newMaximumSpeed : Float) : Boolean {
//        if (newMaximumSpeed <= minimumSpeed)
//            return false
//
//        maximumSpeed = newMaximumSpeed
//        if (speed > maximumSpeed) {
//            speed = maximumSpeed
//        }
//        return true
//    }
//    private fun setSpeedIncrement(speedIncrement : Float) {
//        this.speedIncrement = speedIncrement
//        speed = speed // Make sure that current speed fits speedIncrement (so reassigning is intenionally here)
//        updateNotification()
//    }

//    fun playSpecificSound(playListPosition : Int) {
//        if(playListPosition >= noteList.size)
//            return
//        playSpecificSound(noteList[playListPosition].id, noteList[playListPosition].volume)
//    }
//
//    fun playSpecificSound(sound : Int, volume : Float) {
//        soundPool.play(soundHandles[sound], volume, volume, 1, 0, 1.0f)
//    }

    fun playSpecificSound(noteListItem: NoteListItem) {
        soundPool.play(soundHandles[noteListItem.id], noteListItem.volume, noteListItem.volume, 1, 0, 1.0f)
    }

    private fun setNoteListInAudioMixer() {
        if(noteList.size == 0)
            return

        val duration = Utilities.speed2dt(speed) / 1000.0f
//        Log.v("Metronome", "PlayerService.setMissingMembersAndCopyPlayListToAudioMixer: duration = " +duration + " speed= " + getSpeed());
        require(noteList.size > 0)
        for (noteListItem in noteList) {
            noteListItem.duration = duration
        }
        audioMixer?.noteList = noteList
    }

    private fun applyNoteList(force : Boolean) {
         if (SoundProperties.noteIdAndVolumeEqual(noteList, noteListCopy) && !force)
            return

        // Delay update a little, to avoid any feedback loop with updates of other instances
//            updateMetadataDelayed()

        // set copy of note list which allows reliable checks if something changed in the note list
        if(noteListCopy.size == noteList.size) {
            for(i in noteList.indices)
                noteListCopy[i].set(noteList[i])
        }
        else {
            noteListCopy.clear()
            for (n in noteList)
                noteListCopy.add(n.clone())
        }

        statusChangedListeners.forEach {s -> s.onNoteListChanged(noteList)}
        setNoteListInAudioMixer()
    }

}
