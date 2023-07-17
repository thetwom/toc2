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
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import de.moekadu.metronome.*
import de.moekadu.metronome.audio.AudioMixer
import de.moekadu.metronome.audio.NoteStartedHandler
import de.moekadu.metronome.metronomeproperties.Bpm
import de.moekadu.metronome.metronomeproperties.NoteListItem
import de.moekadu.metronome.metronomeproperties.deepCopyNoteList
import de.moekadu.metronome.misc.InitialValues
import de.moekadu.metronome.notification.PlayerNotification
import de.moekadu.metronome.players.VibratingNote
import de.moekadu.metronome.preferences.SpeedLimiter
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs


class PlayerService : LifecycleService() {

    /** Class which gives access to our own object. */
    private val playerBinder = PlayerBinder()

    /** Interface for callbacks that something changed regarding the player. */
    interface StatusChangedListener {
        /** Called when the metronome starts playing. */
        fun onPlay()
        /** Called when the metronome stops playing. */
        fun onPause()

        /** Called when a note is registered for being played.
         * This will be called when the note is queued for playing, not when the note actually
         * starts playing.
         * @param noteListItem Note which will be played
         * @param nanoTime Time as given by System.nanoTime(), when the note actually will start
         *   playing.
         * @param nanoDuration Duration of note (as derived from bpm).
         * @param noteCount Total count of notes since the play was pressed.
         * @param callDelayNanos Delay of call of this function after the note actually started
         *   playing, in nano seconds. If negative, this function is called before the note
         *   starts playing.
         */
        fun onNoteStarted(
            noteListItem: NoteListItem, nanoTime: Long, nanoDuration:Long, noteCount: Long,
            callDelayNanos: Long
        )

        /** Called when metronome speed changed.
         * @param bpm New speed.
         */
        fun onSpeedChanged(bpm: Bpm)

        /** Called when the list of notes changed.
         * @param noteList New note list.
         */
        fun onNoteListChanged(noteList: ArrayList<NoteListItem>)
    }

    /** Callback for status changes of the metronome. */
    private val statusChangedListeners = mutableSetOf<StatusChangedListener>()

    /** Limit lower and upper bound. */
    private val speedLimiter by lazy {
        SpeedLimiter(PreferenceManager.getDefaultSharedPreferences(this), this)
    }

    /** Metronome speed. */
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

    /** (Un)mute the player. */
    var isMute = false
        set(value) {
            if (field != value) {
                field = value
                audioMixer?.setMute(value)
            }
        }

    /** Notification handling. */
    private var notification: PlayerNotification? = null

    /** Allows to handle external button play-control buttons. */
    private var mediaSession: MediaSessionCompat? = null

    /** Builder for changing the playback state. */
    private val playbackStateBuilder = PlaybackStateCompat.Builder()

    /** The current playback state. */
    val state
        get() = playbackState.state

    /** The current playback state with extra info (compared to state). */
    var playbackState: PlaybackStateCompat = playbackStateBuilder.build()
        private set

    /** The audio mixer is the instance which actually creates sound. */
    private var audioMixer: AudioMixer? = null

    /** Vibrate when the note starts playing. */
    private var vibrator: VibratingNote? = null

    /** The currently played note list. */
    var noteList = ArrayList<NoteListItem>()
        set(value) {
            deepCopyNoteList(value, field)
            audioMixer?.setNoteList(field)
            for (s in statusChangedListeners)
                s.onNoteListChanged(field)
        }

    /** Listener to changes of shared preferences. */
    private var sharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null

    /** Callback for triggering visual effects when a note is started. */
    private var noteStartedHandler4Visualization: NoteStartedHandler? = null

    /** Callback for triggering vibration when a note is started. */
    private var noteStartedHandler4Vibration: NoteStartedHandler? = null

    /** Class which gives access to the PlayerBinder service object. */
    inner class PlayerBinder : Binder() {
        val service
            get() = this@PlayerService
    }

    /** Receiver, which allows to control the metronome via sending intents. */
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
        audioMixer?.setNoteList(noteList)

        audioMixer?.setMute(isMute)

        val visualDelay = sharedPreferences.getInt("visualdelay", 0)
        // callback for ui stuff
        noteStartedHandler4Visualization = audioMixer?.createAndRegisterNoteStartedHandler(
            visualDelay,
            NoteStartedHandler.CallbackWhen.NoteStarted,
            Dispatchers.Main
        ) { noteListItem, nanoTime, nanoDuration, noteCount, handlerDelayNanos ->
            noteListItem?.let {
                statusChangedListeners.forEach { s ->
                    s.onNoteStarted(it, nanoTime, nanoDuration, noteCount, handlerDelayNanos)
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

            override fun onStop() {
                if (state == PlaybackStateCompat.STATE_PLAYING) {
                    stopPlay()
                }
                super.onStop()
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
                            val delay = sharedPreferences.getInt("vibratedelay", 0)
                            enableVibration(delay, strength)
                        } else if (!vibrate) {
                            disableVibration()
                        }
                    }
                    "vibratestrength" -> {
                        vibrator?.strength = sharedPreferences.getInt("vibratestrength", 50)
                    }
                    "vibratedelay" -> {
                        val delay = sharedPreferences.getInt("vibratedelay", 0)
                        noteStartedHandler4Vibration?.delayInMillis = delay
                    }
                    "visualdelay" -> {
                        val delay = sharedPreferences.getInt("visualdelay", 0)
                        noteStartedHandler4Visualization?.delayInMillis = delay
                    }
                }
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

        if (sharedPreferences.getBoolean("vibrate", false)) {
            val strength = sharedPreferences.getInt("vibratestrength", 50)
            val delay = sharedPreferences.getInt("vibratedelay", 0)
            enableVibration(delay, strength)
        }
    }

    override fun onDestroy() {
//        Log.v("Metronome", "PlayerService:onDestroy")
        unregisterReceiver(actionReceiver)
        mediaSession?.release()
        mediaSession = null
        audioMixer?.destroy()
        PlayerNotification.destroyNotification(this)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        super.onDestroy()
    }


    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
//        Log.v("Metronome", "PlayerService:onBind")
        return playerBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
//        Log.v("Metronome", "PlayerService:onUnbind")
        stopPlay()
        // we are bound only once, so as soon, as we unbind we stop being a started service
        // this makes sure, that "onTaskRemoved" will be called, when the app is killed and
        // so we can also reliably kill the notification.
        stopSelf()
        // destroying notifications seems not very reliable, so we do it also here
        PlayerNotification.destroyNotification(this)
        return super.onUnbind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
//        Log.v("Metronome", "PlayerService:onTaskRemoved")
        // we must do this explicitly here, since onDestroy will sometimes not be called
        // and since the notification lives on another process, it won't be kill, when the
        // app process is killed.
        PlayerNotification.destroyNotification(this)
        super.onTaskRemoved(rootIntent)
    }

    /** Change metronome speed.
     * @param bpmDiff Value by which the current speed should be changed.
     */
    fun addValueToBpm(bpmDiff: Float) {
        bpm = bpm.copy(bpm = bpm.bpm + bpmDiff)
    }

    /** Start playing. */
    fun startPlay() {
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
            startForeground(PlayerNotification.NOTIFICATION_ID, it.buildNotification())
        }

//        Log.v("Metronome", "PlayerService:startPlay : starting mixer")
        audioMixer?.start()
//        Log.v("Metronome", "PlayerService:startPlay : run statusChangedListeners")
        statusChangedListeners.forEach { s -> s.onPlay() }
    }

    /** Stop playing. */
    fun stopPlay() {
        // Log.v("Metronome", "PlayerService:stopPlay")

        playbackState = playbackStateBuilder.setState(
            PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
            bpm.bpm
        ).build()
        mediaSession?.setPlaybackState(playbackState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            stopForeground(false)
        }

        audioMixer?.stop()

        statusChangedListeners.forEach { s -> s.onPause() }

        notification?.state = state
        notification?.postNotificationUpdate()
    }

    /** Synchronize the currently playing notes with a given time.
     * @param timeNanos Time as given by System.nanoTime(), which serves as reference time
     *   when the note list starts.
     */
    fun syncClickWithSystemNanos(timeNanos: Long) {
        if (state == PlaybackStateCompat.STATE_PLAYING)
            audioMixer?.synchronizeWithSystemNanos(timeNanos, bpm.beatDurationInSeconds)
    }

    /** Change the next note index to be played.
     * @param index Index of note in the note list, which should be played next.
     */
    fun setNextNoteIndex(index: Int) {
        audioMixer?.setNextNoteIndex(index)
    }

    /** Start playing the currently being placed note list from the beginning. */
    fun restartPlayingNoteList() {
        audioMixer?.restartPlayingNoteList()
    }

    /** Register a callback that the metronome status changed.
     * @param statusChangedListener Callback to be registered.
     */
    fun registerStatusChangedListener(statusChangedListener: StatusChangedListener) {
        statusChangedListeners.add(statusChangedListener)
    }

    /** Unregister a callback that the metronome status changed.
     * @param statusChangedListener Callback to be unregistered.
     */
    fun unregisterStatusChangedListener(statusChangedListener: StatusChangedListener) {
        statusChangedListeners.remove(statusChangedListener)
    }

    /** Change the currently played note list.
     * @param op Function which modifies a given note list.
     *   Input of function will be a note list, which will be modified, and the function must
     *   return true, if the input note list was changed or false, if the input note list was
     *   not changed.
     */
    fun modifyNoteList(op: (noteList: ArrayList<NoteListItem>) -> Boolean) {
        val modified = op(noteList)
//        Log.v("Metronome", "PlayerService.modifyNoteList: modified=$modified")
        if (modified) {
            audioMixer?.setNoteList(noteList)
            for (s in statusChangedListeners)
                s.onNoteListChanged(noteList)
        }
    }

    /** Enable vibration.
     * @param delayInMillis Initial delay in millis of a vibration in comparison to the sound.
     * @param strength Vibration strength.
     */
    private fun enableVibration(delayInMillis: Int, strength: Int) {
        if (noteStartedHandler4Vibration != null)
            return

        vibrator = VibratingNote(this@PlayerService)
        vibrator?.strength = strength

        noteStartedHandler4Vibration = audioMixer?.createAndRegisterNoteStartedHandler(
            delayInMillis, NoteStartedHandler.CallbackWhen.NoteStarted, null
        ) { noteListItem, _, durationNanos, _, _ ->
            if (noteListItem != null) {
                vibrator?.vibrate(noteListItem, durationNanos)
            }
        }
    }

    /** Disable vibration. */
    private fun disableVibration() {
        audioMixer?.unregisterNoteStartedChannel(noteStartedHandler4Vibration)
        vibrator = null
        noteStartedHandler4Vibration = null
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
