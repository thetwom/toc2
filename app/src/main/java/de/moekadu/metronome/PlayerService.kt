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

import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withCreated
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PlayerService : LifecycleService() {

    private val playerBinder = PlayerBinder()

    companion object {
        const val BROADCAST_PLAYERACTION = "de.moekadu.metronome.playeraction"
        const val PLAYER_STATE = "de.moekadu.metronome.playerstate"
        const val PLAYBACK_SPEED = "de.moekadu.metronome.playbackspeed"
        const val INCREMENT_SPEED = "de.moekadu.metronome.incrementSpeed"
        const val DECREMENT_SPEED = "de.moekadu.metronome.decrementSpeed"

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
    }

    private val statusChangedListeners = mutableSetOf<StatusChangedListener>()

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
            statusChangedListeners.forEach {s -> s.onSpeedChanged(field)}

            val duration = computeNoteDurationInSeconds(field)
            for(i in noteList.indices) {
                noteList.setDuration(i, duration)
            }

            notification?.speed = field
            notification?.postNotificationUpdate()
        }

    val state
        get() = playbackState.state

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
            notification?.speedIncrement = value
            notification?.postNotificationUpdate()
        }

    private var notification: PlayerNotification? = null

    private var mediaSession : MediaSessionCompat? = null
    private val playbackStateBuilder = PlaybackStateCompat.Builder()
    var playbackState: PlaybackStateCompat = playbackStateBuilder.build()
        private set

    /// Sound pool which is used for playing sample sounds when selected in the sound chooser.
    private val soundPool = SoundPool.Builder().setMaxStreams(3).build()
    /// Handles of the available sound used by the sound pool.
    private val soundHandles = ArrayList<Int>()

    /// The audio mixer plays is the instance which does plays the metronome.
    private var audioMixer : AudioMixer? = null

    private var vibrator: VibratingNote? = null

    /// The current note list played by the metronome. This list shares its items with all the other classes
    val noteList = NoteList().apply {
        registerNoteListChangedListener(object : NoteList.NoteListChangedListener {
            override fun onNoteAdded(note: NoteListItem, index: Int) {
                setDuration(indexOf(note), computeNoteDurationInSeconds(speed))
            }
            override fun onNoteRemoved(note: NoteListItem, index: Int) { }
            override fun onNoteMoved(note: NoteListItem, fromIndex: Int, toIndex: Int) { }
            override fun onVolumeChanged(note: NoteListItem, index: Int) { }
            override fun onNoteIdChanged(note: NoteListItem, index: Int) { }
            override fun onDurationChanged(note: NoteListItem, index: Int) { }
            override fun onAllNotesReplaced(noteList: NoteList) {
                val d = computeNoteDurationInSeconds(speed)
                for (i in noteList.indices)
                    setDuration(i, d)
            }
        })
    }

    private var sharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null

    private var noteStartedListener4Vibration: AudioMixer.NoteStartedListener? = null

    inner class PlayerBinder : Binder() {
        val service
            get() = this@PlayerService
    }

    /// Receiver, which allows to control the metronome via sending intents
    private val actionReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
//            Log.v("Metronome", "ActionReceiver:onReceive()");
            val extras = intent?.extras ?: return

            val myAction = extras.getLong(PLAYER_STATE, PlaybackStateCompat.STATE_NONE.toLong())
            val newSpeed = extras.getFloat(PLAYBACK_SPEED, -1f)
            val incrementSpeed = extras.getBoolean(INCREMENT_SPEED, false)
            val decrementSpeed = extras.getBoolean(DECREMENT_SPEED, false)

            if (newSpeed > 0)
                speed = newSpeed
            if (incrementSpeed)
                speed += speedIncrement
            if (decrementSpeed)
                speed -= speedIncrement

            if (myAction == PlaybackStateCompat.ACTION_PLAY) {
                // Log.v("Metronome", "ActionReceiver:onReceive : set state to playing");
                startPlay()
            } else if (myAction == PlaybackStateCompat.ACTION_PAUSE) {
                // Log.v("Metronome", "ActionReceiver:onReceive : set state to pause");
                stopPlay()
            }
        }
    }

    override fun onCreate() {
        // Log.v("Metronome", "PlayerService::onCreate()")
        super.onCreate()

        val filter = IntentFilter(BROADCAST_PLAYERACTION)
        registerReceiver(actionReceiver, filter)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        audioMixer = AudioMixer(applicationContext, lifecycleScope)

        // callback for ui stuff
        audioMixer?.registerNoteStartedListener(object : AudioMixer.NoteStartedListener {
            override suspend fun onNoteStarted(noteListItem: NoteListItem?) {
                withContext(Dispatchers.Main) {
                    noteListItem?.original?.let {
                        statusChangedListeners.forEach { s -> s.onNoteStarted(it) }
                    }
                }
            }
        })

        noteStartedListener4Vibration = object : AudioMixer.NoteStartedListener {
            override suspend fun onNoteStarted(noteListItem: NoteListItem?) {
                withContext(Dispatchers.Default) {
                    if (noteListItem != null) {
                        if (getNoteVibrationDuration(noteListItem.id) > 0L)
                            vibrator?.vibrate(noteListItem.volume, noteListItem)
                    }
                }
            }
        }
        // TODO: do not register this if vibration is off
        // callback for vibrator
        audioMixer?.registerNoteStartedListener(noteStartedListener4Vibration,
                vibratingNoteDelay100ToMillis(sharedPreferences.getInt("vibratedelay", 50)))
//        audioMixer?.noteStartedListener = object : AudioMixer.NoteStartedListener {
//            override fun onNoteStarted(noteListItem: NoteListItem?) {
//                if(noteListItem != null) {
//                    statusChangedListeners.forEach { s -> s.onNoteStarted(noteListItem) }
//
//                    if (getNoteVibrationDuration(noteListItem.id) > 0L)
//                        vibrator?.vibrate(noteListItem.volume, noteListItem)
//                }
//            }
//        }
        audioMixer?.noteList = noteList

        val activityIntent = Intent(this, MainActivity::class.java)
        val launchActivity = PendingIntent.getActivity(this, 0, activityIntent, 0)

        notification = PlayerNotification(this)

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

        playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, InitialValues.speed)

        playbackState = playbackStateBuilder.build()
        mediaSession?.setPlaybackState(playbackState)

        mediaSession?.isActive = true

        sharedPreferenceChangeListener = object : OnSharedPreferenceChangeListener {

            override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
                if(sharedPreferences == null || key == null)
                    return
                when (key) {
                    "vibrate" -> {
                        val vibrate = sharedPreferences.getBoolean("vibrate", false)
                        if (vibrate && vibrator == null) {
                            vibrator = VibratingNote(this@PlayerService)
                            vibrator?.strength = sharedPreferences.getInt("vibratestrength", 50)
                        }
                        else if (!vibrate) {
                            vibrator = null
                        }
                    }
                    "vibratestrength" -> {
                        vibrator?.strength = sharedPreferences.getInt("vibratestrength", 50)
                    }
                    "vibratedelay" -> {
                        val delay = vibratingNoteDelay100ToMillis(sharedPreferences.getInt("vibratedelay", 50))
                        audioMixer?.setNoteStartedListenerDelay(noteStartedListener4Vibration, delay)
                    }
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

        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

        minimumSpeed = sharedPreferences.getString("minimumspeed", InitialValues.minimumSpeed.toString())!!.toFloat()
        maximumSpeed = sharedPreferences.getString("maximumspeed", InitialValues.maximumSpeed.toString())!!.toFloat()
        val speedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex)
        speedIncrement = Utilities.speedIncrements[speedIncrementIndex]
        if (sharedPreferences.getBoolean("vibrate", false)) {
            vibrator = VibratingNote(this)
            vibrator?.strength = sharedPreferences.getInt("vibratestrength", 50)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(actionReceiver)
        mediaSession?.release()
        mediaSession = null

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        super.onDestroy()
    }


    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        // Log.v("Metronome", "PlayerService:onBind")

        val numSounds = getNumAvailableNotes()
        for (i in 0 until numSounds) {
            val soundID = getNoteAudioResourceID(i, AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC))
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

    private fun computeNoteDurationInSeconds(speed: Float) : Float {
        return Utilities.speed2dt(speed) / 1000.0f
    }

    fun addValueToSpeed(dSpeed : Float) {
        var newSpeed = speed + dSpeed
        newSpeed = min(newSpeed, maximumSpeed)
        newSpeed = max(newSpeed, minimumSpeed)
        speed = newSpeed
    }

    fun startPlay() {
        // Log.v("Metronome", "PlayerService:startPlay")
        playbackState = playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed).build()
        mediaSession?.setPlaybackState(playbackState)

        notification?.state = state
        notification?.let {
            startForeground(it.id, it.notification)
        }

        audioMixer?.start()
        statusChangedListeners.forEach {s -> s.onPlay()}
    }

    fun stopPlay() {
        // Log.v("Metronome", "PlayerService:stopPlay")

        playbackState = playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed).build()
        mediaSession?.setPlaybackState(playbackState)

        stopForeground(false)

        audioMixer?.stop()

        statusChangedListeners.forEach {s -> s.onPause()}

        notification?.state = state
        notification?.postNotificationUpdate()
    }

    fun syncClickWithUptimeMillis(time : Long) {
        if(state == PlaybackStateCompat.STATE_PLAYING) {
            audioMixer?.synchronizeTime(time, computeNoteDurationInSeconds(speed))
        }
    }

    fun registerStatusChangedListener(statusChangedListener: StatusChangedListener) {
        statusChangedListeners.add(statusChangedListener)
    }

    fun unregisterStatusChangedListener(statusChangedListener: StatusChangedListener) {
        statusChangedListeners.remove(statusChangedListener)
    }

    fun playSpecificSound(noteListItem: NoteListItem, vibrate: Boolean) {
        soundPool.play(soundHandles[noteListItem.id], noteListItem.volume, noteListItem.volume, 1, 0, 1.0f)
        if (vibrate && getNoteVibrationDuration(noteListItem.id) > 0L)
            vibrator?.vibrate(noteListItem.volume, noteListItem)
    }
}
