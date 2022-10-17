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

package de.moekadu.metronome.services

import android.app.PendingIntent
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import de.moekadu.metronome.*
import de.moekadu.metronome.audio.AudioMixer
import de.moekadu.metronome.audio.NoteStartedChannel
import de.moekadu.metronome.metronomeproperties.Bpm
import de.moekadu.metronome.metronomeproperties.NoteListItem
import de.moekadu.metronome.metronomeproperties.deepCopyNoteList
import de.moekadu.metronome.metronomeproperties.getNoteVibrationDuration
import de.moekadu.metronome.misc.InitialValues
import de.moekadu.metronome.notification.PlayerNotification
import de.moekadu.metronome.players.VibratingNote
import de.moekadu.metronome.preferences.SpeedLimiter
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs


class PlayerService : LifecycleService() {

    private val playerBinder = PlayerBinder()

    interface StatusChangedListener {
        fun onPlay()
        fun onPause()
        fun onNoteStarted(noteListItem: NoteListItem, uptimeMillis: Long, noteCount: Long)
        fun onSpeedChanged(bpm: Bpm)
        fun onNoteListChanged(noteList: ArrayList<NoteListItem>)
    }

    private val statusChangedListeners = mutableSetOf<StatusChangedListener>()
    private val speedLimiter by lazy {
        SpeedLimiter(PreferenceManager.getDefaultSharedPreferences(this), this)
    }

    var bpm = InitialValues.bpm
        set(value) {
//            Log.v("Metronome", "PlayerService.bpm: value=$value")
            val newBpm = speedLimiter.limit(value.bpm)
            val tolerance = 1e-6
            if (abs(field.bpm - newBpm) < tolerance && value.noteDuration == field.noteDuration)
                return

            field = Bpm(newBpm, value.noteDuration)
            statusChangedListeners.forEach { s -> s.onSpeedChanged(field) }

            audioMixer?.setBpmQuarter(field.bpmQuarter)

            notification?.bpm = field
            notification?.postNotificationUpdate()
        }

    var isMute = false
        set(value) {
            if (field != value) {
                field = value
                audioMixer?.setMute(value)
            }
        }

    val state
        get() = playbackState.state

    private var notification: PlayerNotification? = null

    private var mediaSession: MediaSessionCompat? = null
    private val playbackStateBuilder = PlaybackStateCompat.Builder()
    var playbackState: PlaybackStateCompat = playbackStateBuilder.build()
        private set

    /// The audio mixer plays is the instance which does plays the metronome.
    private var audioMixer: AudioMixer? = null

    private var vibrator: VibratingNote? = null

    /// The current note list played by the metronome.
    var noteList = ArrayList<NoteListItem>()
        set(value) {
            deepCopyNoteList(value, field)
            audioMixer?.noteList = field
            for (s in statusChangedListeners)
                s.onNoteListChanged(field)
        }

    private var sharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null

    private var noteStartedChannel4Visualization: NoteStartedChannel? = null
    private var noteStartedChannel4Vibration: NoteStartedChannel? = null

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
            val newBpm = extras.getFloat(PLAYBACK_SPEED, -1f)
            val incrementSpeed = extras.getBoolean(INCREMENT_SPEED, false)
            val decrementSpeed = extras.getBoolean(DECREMENT_SPEED, false)

            if (newBpm > 0)
                bpm = bpm.copy(bpm = newBpm)
            if (incrementSpeed)
                bpm = bpm.copy(bpm = bpm.bpm + speedLimiter.bpmIncrement.value!!)
            if (decrementSpeed)
                bpm = bpm.copy(bpm = bpm.bpm - speedLimiter.bpmIncrement.value!!)

            if (myAction == PlaybackStateCompat.ACTION_PLAY) {
                Log.v("Metronome", "ActionReceiver:onReceive : set state to playing on service ${this}")
                startPlay()
            } else if (myAction == PlaybackStateCompat.ACTION_PAUSE) {
                Log.v("Metronome", "ActionReceiver:onReceive : set state to pause on service ${this}");
                stopPlay()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.v("Metronome", "PlayerService.onCreate() : Creating service $this")
        val filter = IntentFilter(BROADCAST_PLAYERACTION)
        registerReceiver(actionReceiver, filter)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        speedLimiter.minimumBpm.observe(this) {
            bpm = speedLimiter.limit(bpm)
        }
        speedLimiter.maximumBpm.observe(this) {
            bpm = speedLimiter.limit(bpm)
        }
        speedLimiter.bpmIncrement.observe(this) {
            bpm = speedLimiter.limit(bpm)
        }

        audioMixer = AudioMixer(applicationContext, lifecycleScope)
        audioMixer?.setBpmQuarter(bpm.bpmQuarter)
        audioMixer?.noteList = noteList

        audioMixer?.setMute(isMute)

        // callback for ui stuff
        noteStartedChannel4Visualization = audioMixer?.getNewNoteStartedChannel(
            0f,
            Dispatchers.Main
        ) { noteListItem, uptimeMillis, noteCount ->
            noteListItem?.let {
                statusChangedListeners.forEach { s ->
                    s.onNoteStarted(it, uptimeMillis, noteCount)
                }
            }
        }

        val activityIntent = Intent(this, MainActivity::class.java)
        val launchActivity = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE)

        notification = PlayerNotification(this)

        mediaSession = MediaSessionCompat(this, "de.moekadu.metronome")
        mediaSession?.setSessionActivity(launchActivity)

        mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Log.v("Metronome", "mediaSession:onPlay() on service ${this}");
                startPlay()
                super.onPlay()
            }

            override fun onPause() {
                Log.v("Metronome", "mediaSession:onPause() on service ${this}");
                stopPlay()
                super.onPause()
            }
        })

        playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
            .setState(
                PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                InitialValues.bpm.bpm
            )

        playbackState = playbackStateBuilder.build()
        mediaSession?.setPlaybackState(playbackState)

        mediaSession?.isActive = true

        sharedPreferenceChangeListener = object : OnSharedPreferenceChangeListener {

            override fun onSharedPreferenceChanged(
                sharedPreferences: SharedPreferences?,
                key: String?
            ) {
                if (sharedPreferences == null || key == null)
                    return
                when (key) {
                    "vibrate" -> {
                        val vibrate = sharedPreferences.getBoolean("vibrate", false)
                        if (vibrate && vibrator == null) {
                            val strength = sharedPreferences.getInt("vibratestrength", 50)
                            val delay = sharedPreferences.getInt("vibratedelay", 0).toFloat()
                            enableVibration(delay, strength)
                        } else if (!vibrate) {
                            disableVibration()
                        }
                    }
                    "vibratestrength" -> {
                        vibrator?.strength = sharedPreferences.getInt("vibratestrength", 50)
                    }
                    "vibratedelay" -> {
                        val delay = sharedPreferences.getInt("vibratedelay", 0).toFloat()
                        noteStartedChannel4Vibration?.setDelay(delay)
                    }
                }
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

        if (sharedPreferences.getBoolean("vibrate", false)) {
            val strength = sharedPreferences.getInt("vibratestrength", 50)
            val delay = sharedPreferences.getInt("vibratedelay", 0).toFloat()
            enableVibration(delay, strength)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(actionReceiver)
        mediaSession?.release()
        mediaSession = null

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        Log.v("Metronome", "PlayerService.onDestroy() : Destroying service $this")
        super.onDestroy()
    }


    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Log.v("Metronome", "PlayerService.onBind() : Binding service $this")
        return playerBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
//        Log.v("Metronome", "PlayerService:onUnbind");
        Log.v("Metronome", "PlayerService.onUnbind() : Unbinding service $this")
        stopPlay()
        return super.onUnbind(intent)
    }

    fun addValueToBpm(bpmDiff: Float) {
        bpm = bpm.copy(bpm = bpm.bpm + bpmDiff)
    }

    fun startPlay() {
        Log.v("Metronome", "PlayerService:startPlay : on service ${this}, playbackState before call=$state")
        if (state == PlaybackStateCompat.STATE_PLAYING)
            return

//        Log.v("Metronome", "PlayerService:startPlay : setting playbackState")
        playbackState = playbackStateBuilder.setState(
            PlaybackStateCompat.STATE_PLAYING,
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
            bpm.bpm
        ).build()
        mediaSession?.setPlaybackState(playbackState)

//        Log.v("Metronome", "PlayerService:startPlay : setting notification")
        notification?.state = state
        notification?.let {
            startForeground(PlayerNotification.id, it.notification)
        }

//        Log.v("Metronome", "PlayerService:startPlay : starting mixer")
        audioMixer?.start()
//        Log.v("Metronome", "PlayerService:startPlay : run statusChangedListeners")
        statusChangedListeners.forEach { s -> s.onPlay() }
    }

    fun stopPlay() {
        Log.v("Metronome", "PlayerService:stopPlay : on service ${this}, playbackState before call=$state")
        if (state == PlaybackStateCompat.STATE_PAUSED)
            return

        playbackState = playbackStateBuilder.setState(
            PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
            bpm.bpm
        ).build()
        mediaSession?.setPlaybackState(playbackState)

        stopForeground(false)

        audioMixer?.stop()

        statusChangedListeners.forEach { s -> s.onPause() }

        notification?.state = state
        notification?.postNotificationUpdate()
    }

    fun syncClickWithUptimeMillis(uptimeMillis: Long) {
        if (state == PlaybackStateCompat.STATE_PLAYING)
            audioMixer?.synchronizeTime(uptimeMillis, bpm.beatDurationInSeconds)
    }

    fun setNextNoteIndex(index: Int) {
        audioMixer?.setNextNoteIndex(index)
    }

    fun restartPlayingNoteList() {
        audioMixer?.restartPlayingNoteList()
    }

    fun registerStatusChangedListener(statusChangedListener: StatusChangedListener) {
        val numBefore = statusChangedListeners.size
        statusChangedListeners.add(statusChangedListener)
        val numAfter = statusChangedListeners.size
        Log.v("Metronome", "PlayerService.registerStatusChangedListener: service: ${this}, number of listeners before: $numBefore, after: $numAfter")
    }

    fun unregisterStatusChangedListener(statusChangedListener: StatusChangedListener) {
        val numBefore = statusChangedListeners.size
        statusChangedListeners.remove(statusChangedListener)
        val numAfter = statusChangedListeners.size
        Log.v("Metronome", "PlayerService.unregisterStatusChangedListener: service: ${this}, number of listeners before: $numBefore, after: $numAfter")
    }

    fun modifyNoteList(op: (ArrayList<NoteListItem>) -> Boolean) {
        val modified = op(noteList)
//        Log.v("Metronome", "PlayerService.modifyNoteList: modified=$modified")
        if (modified) {
            audioMixer?.noteList = noteList
            for (s in statusChangedListeners)
                s.onNoteListChanged(noteList)
        }
    }

    private fun enableVibration(delayInMillis: Float, strength: Int) {
        if (noteStartedChannel4Vibration != null)
            return

        vibrator = VibratingNote(this@PlayerService)
        vibrator?.strength = strength

        noteStartedChannel4Vibration = audioMixer?.getNewNoteStartedChannel(
            delayInMillis, null
        ) { noteListItem, _, _ ->
            if (noteListItem != null) {
                if (getNoteVibrationDuration(noteListItem.id) > 0L)
                    vibrator?.vibrate(noteListItem.volume, noteListItem, bpm.bpmQuarter)
            }
        }
    }

    private fun disableVibration() {
        audioMixer?.unregisterNoteStartedChannel(noteStartedChannel4Vibration)
        vibrator = null
        noteStartedChannel4Vibration = null
    }

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
//        fun sendChangeSpeedIntent(context: Context, bpm: Float) {
//            val intent = Intent(BROADCAST_PLAYERACTION)
//            intent.putExtra(PLAYBACKSPEED, bpm)
//            context.sendBroadcast(intent)
//        }
    }
}
