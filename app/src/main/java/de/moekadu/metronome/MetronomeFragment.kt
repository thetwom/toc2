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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import kotlin.math.roundToInt


/**

 */
class MetronomeFragment : Fragment() {

    private var speedText: TextView? = null
    private var playButton: PlayButton? = null
    private var noteView : NoteView? = null
    private var plusButton : ImageButton? = null

    val noteListChangeListener = object : NoteList.NoteListChangedListener {
        private fun playTestSoundIfRequired(note: NoteListItem) {
            if(soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC && playerService?.state != PlaybackStateCompat.STATE_PLAYING)
                playerService?.playSpecificSound(note)
        }
        override fun onNoteAdded(note: NoteListItem, index: Int) { }
        override fun onNoteRemoved(note: NoteListItem, index: Int) { }
        override fun onNoteMoved(note: NoteListItem, fromIndex: Int, toIndex: Int) { }

        override fun onVolumeChanged(note: NoteListItem, index: Int) {
            playTestSoundIfRequired(note)
        }

        override fun onNoteIdChanged(note: NoteListItem, index: Int) {
            playTestSoundIfRequired(note)
        }

        override fun onDurationChanged(note: NoteListItem, index: Int) { }
    }

    /// Note list, that contains current metronome notes.
    var noteList : NoteList? = null
        set(value) {
            field?.unregisterNoteListChangedListener(noteListChangeListener)
            field = value
            field?.registerNoteListChangedListener(noteListChangeListener)
            noteView?.noteList = value
            volumeSliders?.noteList = value
            soundChooser?.noteList = value
        }

//    /// Note list, that contains current metronome notes.
//    /**
//     *  The list items are shared with other instances like the player service. So if e.g. the
//     * player service changes an item, the change is also visible here.
//     */
//    var noteList = NoteList()
//        set(value) {
//            val instancesChangedFlag = !noteList.compareNoteListItemInstances(value)
//            Log.v("Metronome", "MetronomeFragment.noteList : given size = ${value.size}")
//            if (instancesChangedFlag) {
//                field.clear()
//                field.addAll(value)
//            }
//
//            // if instances changed we force the note list update (update noteView, call noteListChangedListener)
//            applyNoteList(instancesChangedFlag)
//        }
//
//    /// Deep copy of noteList, thus each item is a real copy.
//    /**
//     * We need this attribute to keep track of changes in the noteList.
//     */
//    private val noteListCopy = NoteList()

    //    private SpeedIndicator speedIndicator;
    private var tickVisualizer: TickVisualizer? = null
    private var soundChooser: SoundChooser? = null
    private var savedSoundChooserNoteIndex = -1
    private var volumeSliders: VolumeSliders? = null
    private var savedVolumeSlidersFolded = true

    private var playerConnection: ServiceConnection? = null
    private var playerService: PlayerService? = null
    private var playerContext: Context? = null

    private var sharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null
    private var speedIncrement = Utilities.speedIncrements[InitialValues.speedIncrementIndex]

//    private var volumes = FloatArray(0)

    private val playerServiceStatusChangedListener = object : PlayerService.StatusChangedListener {

        override fun onNoteStarted(noteListItem: NoteListItem) {
            playerService?.let { tickVisualizer?.tick(Utilities.speed2dt(it.speed))}
            noteView?.animateNote(noteListItem)
            soundChooser?.animateNote(noteListItem)
        }

        override fun onPlay() {
            playButton?.changeStatus(PlayButton.STATUS_PLAYING, true)
        }

        override fun onPause() {
            tickVisualizer?.stop()
            playButton?.changeStatus(PlayButton.STATUS_PAUSED, true)
        }

        override fun onSpeedChanged(speed: Float) {
            if(isAdded)
                speedText?.text = getString(R.string.bpm, Utilities.getBpmString(speed, speedIncrement))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
         // Log.v("Metronome", "MetronomeFragment:onCreateView");
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_metronome, container, false)

        speedText = view.findViewById(R.id.speed_text)
        // wakeupErrorText = view.findViewById(R.id.wakeup_error);

        val speedPanel = view.findViewById(R.id.speed_panel) as SpeedPanel?

        volumeSliders = view.findViewById(R.id.volume_sliders)
        volumeSliders?.noteList = noteList

        tickVisualizer = view.findViewById(R.id.tick_visualizer)

        speedPanel?.speedChangedListener = object : SpeedPanel.SpeedChangedListener {
            override fun onSpeedChanged(dSpeed: Float) {
                playerService?.addValueToSpeed(dSpeed)
            }

            override fun onAbsoluteSpeedChanged(newSpeed: Float, nextKlickTimeInMillis: Long) {
                playerService?.speed = newSpeed
                playerService?.syncClickWithUptimeMillis(nextKlickTimeInMillis)
            }
        }

        playButton = view.findViewById(R.id.play_button)

        playButton?.buttonClickedListener = object : PlayButton.ButtonClickedListener {
            override fun onPause() {
                // Log.v("Metronome", "playButton:onPause()")
                playerService?.stopPlay()
            }

            override fun onPlay() {
                // Log.v("Metronome", "playButton:onPause()")
                playerService?.startPlay()
            }
        }

        noteView = view.findViewById(R.id.note_view)
        noteView?.noteList = noteList

        noteView?.onNoteClickListener = object : NoteView.OnNoteClickListener {
            override fun onDown(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {
                Log.v("Metronome", "MetronomeFragment.noteView.onClickListener.onDown: noteIndex=$noteIndex")
                if(note != null) {
                    soundChooser?.setActiveNote(note)
                    noteView?.highlightNote(note, true)
                }
                return false
            }

            override fun onUp(event: MotionEvent?, note: NoteListItem?,noteIndex: Int): Boolean {
                return true
            }

            override fun onMove(event: MotionEvent?, note: NoteListItem?,noteIndex: Int): Boolean {
                return true
            }
        }

        noteView?.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if(left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                volumeSliders?.setNoteViewBoundingBox(
                        left + v.paddingLeft,
                        top + v.paddingTop,
                        right - v.paddingLeft,
                        bottom - v.paddingBottom
                )
                soundChooser?.setNoteViewBoundingBox(
                        left + v.paddingLeft,
                        top + v.paddingTop,
                        right - v.paddingLeft,
                        bottom - v.paddingBottom
                )
                Log.v("Metronome", "MetronomeFragment.noteView.onLayoutChangedListener: height = ${bottom-top}, ${v.height}, ${noteView?.height}")
            }
        }
//        noteView?.boundingBoxesChangedListener = object : NoteView.BoundingBoxesChangedListener {
//            override fun onBoundingBoxesChanged(boundingBoxes: Array<Rect>) {
//                Log.v("Metronome", "MetronomeFragment.noteView.onBoundingBoxesChanged")
//                soundChooser?.setBoundingBoxes(boundingBoxes)
////                if (volumes.size != noteView?.numNotes)
////                    volumes = FloatArray(noteView?.numNotes ?: 0)
////                for (i in volumes.indices) {
////                    volumes[i] = noteView?.getNoteListItem(i)?.volume ?: 0f
////                }
//                volumeSliders?.setBoundingBoxes(boundingBoxes)
//            }
//        }

        plusButton = view.findViewById(R.id.plus_button)
        plusButton?.setOnClickListener {
            if(noteList?.size == 0)
                noteList?.add(NoteListItem(defaultNote, 1.0f, -1.0f))
            else
                noteList?.let {it.add(it.last().clone())}
//            applyNoteList(false)

            if(soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC) {
                noteList?.let { notes ->
                    val noteIndex = notes.size - 1
                    val note = notes[noteIndex]
                    //val noteBoxes = noteView?.noteBoundingBoxes
                    //if (noteBoxes != null) {
                    soundChooser?.setActiveNote(note)
//                    soundChooser?.activateStaticChoices()
                    noteView?.highlightNote(note, true)
                    //}
                }
            }
        }

        soundChooser = view.findViewById(R.id.sound_chooser)
        soundChooser?.noteList = noteList

        soundChooser?.stateChangedListener = object : SoundChooser.StateChangedListener {
//            override fun onPositionChanged(note : NoteListItem, boxIndex: Int) {
//                noteList.removeNote(note)
//                noteList.add(boxIndex, note)
//                applyNoteList(true)
//                // Reset volumes
//                for(i in noteList.indices)
//                    volumes[i] = noteList[i].volume
//                volumeSliders?.setVolumes(volumes, 300L)
//            }
//
//            override fun onNoteDeleted(note: NoteListItem) {
//                var index = noteList.indexOfObject(note)
//                Log.v("Metronome", "MetronmeFragment.onNoteDeleted: index of note to be deleted = $index")
//                noteList.remove(note)
//                noteView?.highlightNote(note, false)
//                applyNoteList(false)
//
//                if(soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC) {
//                    if(noteList.size == 0) {
//                        soundChooser?.deactivate()
//                    }
//                    else {
//                        if (index < 0 || index >= noteList.size)
//                            index = noteList.size - 1
//                        soundChooser?.setActiveNote(index, noteList[index])
//                        noteView?.highlightNote(noteList[index], true)
//                    }
//                }
//            }
//
//            override fun onNoteChanged(note: NoteListItem, noteID: Int) {
//                note.id = noteID
////                if(noteList.size >= 2) {
////                    Log.v("Metronome", "MainActivity.onNoteChanged : new noteListId=${note.id}, note=${note}")
////                    for(n in noteList)
////                        Log.v("Metronome", "MainActivity.onNoteChanged : noteListId=${n.id}, note=${n}")
////                }
//                applyNoteList(false)
//
//                if(soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC && playerService?.state != PlaybackStateCompat.STATE_PLAYING) {
//                    playerService?.playSpecificSound(note)
//                }
//            }

            override fun onSoundChooserDeactivated(note: NoteListItem?) {
                if(note != null)
                    noteView?.highlightNote(note, false)

            }

//            override fun onVolumeChanged(note: NoteListItem, volume: Float) {
//                note.volume = volume
//                applyNoteList(false)
//
//                if(soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC && playerService?.state != PlaybackStateCompat.STATE_PLAYING) {
//                    playerService?.playSpecificSound(note)
//                }
//
//                for(i in noteList.indices)
//                    volumes[i] = noteList[i].volume
//                volumeSliders?.setVolumes(volumes, 300L)
//            }
        }

//        volumeSliders?.volumeChangedListener = object : VolumeSliders.VolumeChangedListener {
//            override fun onVolumeChanged(sliderIdx: Int, volume: Float) {
//                playerService?.setVolume(sliderIdx, volume)
//                playerService?.let {
//                    if (it.state != PlaybackStateCompat.STATE_PLAYING)
//                        it.playSpecificSound(it.noteList[sliderIdx])
//                }
//            }
//        }

        sharedPreferenceChangeListener = OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                "speedincrement" -> {
                    val newSpeedIncrementIndex = sharedPreferences!!.getInt("speedincrement", InitialValues.speedIncrementIndex)
                    speedIncrement = Utilities.speedIncrements[newSpeedIncrementIndex]
                    speedPanel?.speedIncrement = speedIncrement
                }
                "speedsensitivity" -> {
                    val newSpeedSensitivity = sharedPreferences!!.getInt("speedsensitivity", (Utilities.sensitivity2percentage(InitialValues.speedSensitivity)).roundToInt()).toFloat()
                    speedPanel?.sensitivity = Utilities.percentage2sensitivity(newSpeedSensitivity)
                }
            }
        }

        require(context != null)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val speedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex)
        speedIncrement = Utilities.speedIncrements[speedIncrementIndex]
        speedPanel?.speedIncrement = speedIncrement

        val speedSensitivity = sharedPreferences.getInt("speedsensitivity", (Utilities.sensitivity2percentage(InitialValues.speedSensitivity)).roundToInt()).toFloat()
        speedPanel?.sensitivity = Utilities.percentage2sensitivity(speedSensitivity)

        savedSoundChooserNoteIndex = savedInstanceState?.getInt("soundChooserNoteIndex", -1) ?: -1
        savedVolumeSlidersFolded = savedInstanceState?.getBoolean("volumeSlidersFolded", true) ?: true
        return view
    }

    override fun onResume() {
        Log.v("Metronome", "MetronomeFragment:onResume")
        super.onResume()
        require(context != null)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        // We can bind service only after our fragment is fully inflated since while binding,
        // we call commands which require our view fully set up!
        val run = Runnable {
            val act = activity as MainActivity?
            if(act != null) {
                bindService(act.applicationContext)
            }
        }

        view?.post(run)
    }

    override fun onPause() {
        Log.v("Metronome", "MetronomeFragment:onPause")
        require(context != null)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        unbindPlayerService()
        super.onPause()
    }

    override fun onDestroy() {
        noteList = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if(soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC) {
            noteList?.let { notes ->
                val noteIndex = notes.indexOf(soundChooser?.activeNote)
                outState.putInt("soundChooserNoteIndex", noteIndex)
            }
        }
        outState.putBoolean("volumeSlidersFolded", volumeSliders?.folded ?: true)

        super.onSaveInstanceState(outState)
    }

    private fun unbindPlayerService() {
        if (playerService == null)
            return
        playerService?.unregisterStatusChangedListener(playerServiceStatusChangedListener)
        // playerService?.unregisterMediaControllerCallback(mediaControllerCallback)
        playerConnection?.let { playerContext?.unbindService(it) }
        playerService = null
    }

    private fun updateView() {
        Log.v("Metronome", "MetronomeFragment.updateView")
        playerService?.playbackState?.let { playbackState ->
            when(playbackState.state) {
                PlaybackStateCompat.STATE_PLAYING -> {
                    playButton?.changeStatus(PlayButton.STATUS_PLAYING, false)
                }
                        PlaybackStateCompat.STATE_PAUSED -> {
                    tickVisualizer?.stop()
                    playButton?.changeStatus(PlayButton.STATUS_PAUSED, false)
                }
            }
            true // we have to add this true to since the "let" expects a return value
        }

//        playerService?.let {
//            Log.v("Metronome", "MetronomeFragment.updateView: set note list")
//            noteList = it.noteList
//            noteView?.setNotes(noteList, 0)
//            speedText?.text = getString(R.string.bpm, Utilities.getBpmString(it.speed, speedIncrement))
//        }
        speedText?.text = getString(R.string.bpm, Utilities.getBpmString(playerService?.speed ?: InitialValues.speed, speedIncrement))
    }

    private fun bindService(context : Context?) {
        if(context == null)
        {
            // we should throw some error here
            return
        }

        if(playerService == null) {
            playerConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    Log.v("Metronome", "PlayerService:onServiceConnected")
                    // We've bound to LocalService, cast the IBinder and get LocalService instance
                    val binder = service as PlayerService.PlayerBinder
                    playerService = binder.service
                    playerContext = context
                    playerService?.registerStatusChangedListener(playerServiceStatusChangedListener)
                    noteList = playerService?.noteList
                    updateView()
//                    updateSpeedIndicatorMarksAndVolumeSliders()

                    noteList?.let { notes ->
                        if (savedSoundChooserNoteIndex >= 0 && savedSoundChooserNoteIndex < notes.size) {
                            val note = notes[savedSoundChooserNoteIndex]
                            soundChooser?.setActiveNote(note)
                            noteView?.highlightNote(note, true)
                            soundChooser?.activateStaticChoices(0L)
                        }
                    }

                    if(!savedVolumeSlidersFolded)
                        volumeSliders?.unfold(0L)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // Log.v("Metronome", "PlayerService:onServiceDisconnected")
                    playerService?.unregisterStatusChangedListener(playerServiceStatusChangedListener)
                    playerService = null
                    playerContext = null
                }
            }

            val serviceIntent = Intent(context, PlayerService::class.java)
            context.bindService(serviceIntent, playerConnection as ServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

//    /// Apply note list to audio mixer and call noteListChangedListeners
//    /**
//     * @param force Reapply note list even if note and volume are already equal
//     */
//    private fun applyNoteList(force: Boolean) {
//        if (SoundProperties.noteIdAndVolumeEqual(noteList, noteListCopy) && !force)
//            return
//
//        if(noteListCopy.size == noteList.size) {
//            for(i in noteList.indices)
//                noteListCopy[i].set(noteList[i])
//        }
//        else {
//            noteListCopy.clear()
//            for (n in noteList)
//                noteListCopy.add(n.clone())
//        }
//
//        playerService?.noteList = noteList
//        noteView?.setNotes(noteList)
//        Log.v("Metronome", "MetronomeFragment.noteList : size at end = ${noteList.size}")
////        updateSpeedIndicatorMarksAndVolumeSliders()
//        //noteView?.let { soundChooser?.setBoundingBoxes(it.noteBoundingBoxes) }
//    }

//    private fun updateSpeedIndicatorMarksAndVolumeSliders() {
//
//        //val noteBoxes = noteView?.noteBoundingBoxes
//        val noteList = noteView?.noteList
//        //if (noteBoxes == null || noteBoxes.isEmpty() || noteList == null)
//        //    return
//
//        volumes.clear()
//
//        for(i in noteList.indices)
//            volumes.add(noteList[i].volume)
//
//        volumeSliders?.setTunersAt(noteBoxes, volumes)
//    }
}
