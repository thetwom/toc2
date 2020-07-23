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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import kotlin.math.roundToInt


/**

 */
class MetronomeFragment : Fragment() {

    //    private TextView wakeupErrorText;
    private var speedText: TextView? = null
    private var playButton: PlayButton? = null
    private var noteView : NoteView? = null
    private var plusButton : ImageButton? = null

    /// Note list, that contains current metronome notes.
    /**
     *  The list items are shared with other instances like the player service. So if e.g. the
     * player service changes an item, the change is also visible here.
     */
    var noteList = NoteList()
        set(value) {
            val instancesChangedFlag = !noteList.compareNoteListItemInstances(value)
            Log.v("Metronome", "MetronomeFragment.noteList : given size = ${value.size}")
            if (instancesChangedFlag) {
                field.clear()
                field.addAll(value)
            }

            // if instances changed we force the note list update (update noteView, call noteListChangedListener)
            applyNoteList(instancesChangedFlag)
        }

    /// Deep copy of noteList, thus each item is a real copy.
    /**
     * We need this attribute to keep track of changes in the noteList.
     */
    private val noteListCopy = NoteList()

    //    private SpeedIndicator speedIndicator;
    private var tickVisualizer: TickVisualizer? = null
    private var soundChooser: SoundChooser? = null
    private var savedSoundChooserNoteIndex = -1
    private var volumeSliders: VolumeSliders? = null

    private var playerConnection: ServiceConnection? = null
    private var playerService: PlayerService? = null
    private var playerContext: Context? = null

    private var sharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null
    private var speedIncrement = Utilities.speedIncrements[InitialValues.speedIncrementIndex]

    // private final Vector<Float> buttonPositions = new Vector<>();
    private val volumes = ArrayList<Float>()

//    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
//        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
//            super.onPlaybackStateChanged(state)
//            if(state != null)
//                updateView(state, true)
//        }
//
//        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
//            super.onMetadataChanged(metadata)
//            if(metadata != null)
//                updateView(metadata)
//        }
//    }

    private val playerServiceStatusChangedListener = object : PlayerService.StatusChangedListener {
        override fun onNoteListChanged(noteList: NoteList) {
            this@MetronomeFragment.noteList = noteList
        }

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

        noteView?.onClickListener = object : NoteView.OnClickListener {
            override fun onDown(event: MotionEvent?, note: NoteListItem?, noteIndex: Int, noteBoundingBoxes: Array<Rect>
            ): Boolean {
                if(note != null) {
                    soundChooser?.activate(note, noteIndex, noteBoundingBoxes)
                    noteView?.highlightNote(note, true)
                }
                return false
            }

            override fun onUp(event: MotionEvent?, note: NoteListItem?,noteIndex: Int, noteBoundingBoxes: Array<Rect>
            ): Boolean {
                return true
            }

            override fun onMove(event: MotionEvent?, note: NoteListItem?,noteIndex: Int): Boolean {
                return true
            }
        }

        plusButton = view.findViewById(R.id.plus_button)
        plusButton?.setOnClickListener {
            if(noteList.size == 0)
                noteList.add(NoteListItem(defaultNote, 1.0f, -1.0f))
            else
                noteList.add(noteList.last().clone())
            applyNoteList(false)

            if(soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC) {
                val noteIndex = noteList.size - 1
                val note = noteList[noteIndex]
                val noteBoxes = noteView?.noteBoundingBoxes
                if (noteBoxes != null) {
                    soundChooser?.activate(note, noteIndex, noteBoxes)
                    noteView?.highlightNote(note, true)
                }
            }
        }
//        plusButton?.setBackgroundResource(R.drawable.ic_notelines)
//        plusButton?.backgroundTintList = context?.let { ContextCompat.getColorStateList(it, R.color.plus_button_lines) }
        //plusButton?.background = resources.getDrawable(R.drawable.ic_notelines, null)

        soundChooser = view.findViewById(R.id.sound_chooser)

        soundChooser?.stateChangedListener = object : SoundChooser.StateChangedListener {
            override fun onPositionChanged(note : NoteListItem, boxIndex: Int) {
                noteList.removeNote(note)
                noteList.add(boxIndex, note)
                applyNoteList(false)
            }

            override fun onNoteDeleted(note: NoteListItem) {
                noteList.remove(note)
                applyNoteList(false)
            }
            override fun onNoteChanged(note: NoteListItem, noteID: Int) {
                note.id = noteID
//                if(noteList.size >= 2) {
//                    Log.v("Metronome", "MainActivity.onNoteChanged : new noteListId=${note.id}, note=${note}")
//                    for(n in noteList)
//                        Log.v("Metronome", "MainActivity.onNoteChanged : noteListId=${n.id}, note=${n}")
//                }
                applyNoteList(false)

                if(soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC && playerService?.state != PlaybackStateCompat.STATE_PLAYING) {
                    playerService?.playSpecificSound(note)
                }
            }

            override fun onSoundChooserDeactivated(note: NoteListItem?) {
                if(note != null)
                    noteView?.highlightNote(note, false)

            }

            override fun onVolumeChanged(note: NoteListItem, volume: Float) {
                note.volume = volume
                applyNoteList(false)

                if(soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC && playerService?.state != PlaybackStateCompat.STATE_PLAYING) {
                    playerService?.playSpecificSound(note)
                }
            }
        }


//        soundChooser.setButtonClickedListener(new SoundChooserOld.ButtonClickedListener() {
//            @Override
//            public void onButtonClicked(MoveableButton button) {
//                MainActivity act = (MainActivity) getActivity();
//                assert act != null;
//                act.loadSoundChooserDialog(button, playerService);
////                SoundChooserDialog soundChooserDialog = new SoundChooserDialog(act, button.getProperties());
////                soundChooserDialog.setNewButtonPropertiesListener(new SoundChooserDialog.NewButtonPropertiesListener() {
////                    @Override
////                    public void onNewButtonProperties(Bundle properties) {
////                        // Log.v("Metronome", "Setting new button properties ");
////                        button.setProperties(properties);
////                        setNewSound(soundChooser.getSounds());
////                    }
////                });
//            }
//        });

//        soundChooser.setSoundChangedListener(new SoundChooserOld.SoundChangedListener() {
//            @Override
//            public void onSoundChanged(ArrayList<NoteListItem> sounds) {
////                Log.v("Metronome", "MetronomeFragment:onSoundChanged");
//                setNewSound(sounds);
//            }
//        });

        volumeSliders?.volumeChangedListener = object : VolumeSliders.VolumeChangedListener {
            override fun onVolumeChanged(sliderIdx: Int, volume: Float) {
                playerService?.setVolume(sliderIdx, volume)
                playerService?.let {
                    if (it.state != PlaybackStateCompat.STATE_PLAYING)
                        it.playSpecificSound(it.noteList[sliderIdx])
                }
            }
        }

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

    override fun onSaveInstanceState(outState: Bundle) {
        if(soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC) {
            val noteIndex = noteList.indexOf(soundChooser?.note)
            outState.putInt("soundChooserNoteIndex", noteIndex)
        }

        super.onSaveInstanceState(outState)
    }

//    override fun onDestroyView() {
//        // Log.v("Metronome", "MetronomeFragment:onDestroyView")
//        //if(playerServiceBound)
//        // unbindPlayerService()
//        super.onDestroyView()
//    }

//    override fun onDestroy() {
//        // Log.v("Metronome", "MetronomeFragment:onDestroy")
//        // if(playerServiceBound)
//        //  unbindPlayerService()
//        super.onDestroy()
//    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        int id = item.getItemId();
//        if(id == R.id.action_settings){
//            if(onFragmentInteractionListener != null) {
//                onFragmentInteractionListener.onSettingsClicked();
//                return true;
//            }
//        }
//        return super.onOptionsItemSelected(item);
//    }

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
//            // We need this to avoid rare errors that there is no context
//            if (isAdded)
//                speedText?.text = getString(R.string.bpm, Utilities.getBpmString(playerService.speed, speedIncrement))
        }

        playerService?.let {
            Log.v("Metronome", "MetronomeFragment.updateView: set note list")
            noteList = it.noteList
            noteView?.setNotes(noteList, 0)
            speedText?.text = getString(R.string.bpm, Utilities.getBpmString(it.speed, speedIncrement))
        }
    }

//    private fun updateView(state : PlaybackStateCompat, animate : Boolean) {
//        if(state.state == PlaybackStateCompat.STATE_PLAYING){
//            playButton?.changeStatus(PlayButton.STATUS_PLAYING, animate)
//        }
//        else if(state.state == PlaybackStateCompat.STATE_PAUSED){
////            speedIndicator.stopPlay();
//            tickVisualizer?.stop()
//            playButton?.changeStatus(PlayButton.STATUS_PAUSED, animate)
//        }
//
//        // We need this to avoid rare errors that there is no context
//        if(isAdded)
//            speedText?.text = getString(R.string.bpm, Utilities.getBpmString(state.playbackSpeed, speedIncrement))
//
//        if(state.position != PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN){
//            val speed = state.playbackSpeed
//            soundChooser.animateButton(state.position, Utilities.speed2dt(speed))
//
////            speedIndicator.animate((int) state.getPosition(), speed);
//            tickVisualizer?.tick(Utilities.speed2dt(speed))
//        }
//    }
//
//    private fun updateView(metadata : MediaMetadataCompat) {
//        val soundString = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
//        // Log.v("Metronome", "MetronomeFragment : updateView : parsing metadata : " + soundString)
//        val noteList = SoundProperties.parseMetaDataString(soundString)
//        updateSpeedIndicatorMarksAndVolumeSliders()
//    }

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
                    // playerService?.registerMediaControllerCallback(mediaControllerCallback)
                    playerService?.registerStatusChangedListener(playerServiceStatusChangedListener)

                    //updateView(playerService!!.playbackState, false)
                    updateView()
                    updateSpeedIndicatorMarksAndVolumeSliders()

                    if(savedSoundChooserNoteIndex >= 0 && savedSoundChooserNoteIndex < noteList.size) {
                        val note = noteList[savedSoundChooserNoteIndex]
                        val noteBoxes = noteView?.noteBoundingBoxes
                        if (noteBoxes != null) {
                            soundChooser?.activate(note, savedSoundChooserNoteIndex, noteBoxes, 0L,true)
                            noteView?.highlightNote(note, true)
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // Log.v("Metronome", "PlayerService:onServiceDisconnected")
                    //playerService?.unregisterMediaControllerCallback(mediaControllerCallback)
                    playerService?.unregisterStatusChangedListener(playerServiceStatusChangedListener)
                    playerService = null
                    playerContext = null
                }
            }

            val serviceIntent = Intent(context, PlayerService::class.java)
            context.bindService(serviceIntent, playerConnection as ServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

//    private fun setNewSound(noteList: NoteList) {
////        Log.v("Metronome", "MetronomeFragment:setNewSounds")
//        if(playerServiceBound) {
////            Log.v("Metronome", "MetronomeFragment:setNewSounds: Calling playerService.setSounds")
//            playerService.noteList = noteList
//        }
//        updateSpeedIndicatorMarksAndVolumeSliders()
//    }

    /// Apply note list to audio mixer and call noteListChangedListeners
    /**
     * @param force Reapply note list even if note and volume are already equal
     */
    private fun applyNoteList(force: Boolean) {
        if (SoundProperties.noteIdAndVolumeEqual(noteList, noteListCopy) && !force)
            return

        if(noteListCopy.size == noteList.size) {
            for(i in noteList.indices)
                noteListCopy[i].set(noteList[i])
        }
        else {
            noteListCopy.clear()
            for (n in noteList)
                noteListCopy.add(n.clone())
        }

        playerService?.noteList = noteList
        noteView?.setNotes(noteList)
        Log.v("Metronome", "MetronomeFragment.noteList : size at end = ${noteList.size}")
        updateSpeedIndicatorMarksAndVolumeSliders()
    }

    private fun updateSpeedIndicatorMarksAndVolumeSliders() {

        val noteBoxes = noteView?.noteBoundingBoxes
        val noteList = noteView?.noteList
        if (noteBoxes == null || noteBoxes.isEmpty() || noteList == null)
            return

        volumes.clear()

        //val buttonWidth = noteBoxes[0].width()
        for(i in noteList.indices)
            volumes.add(noteList[i].volume)

        //buttonPositions.add(soundChooser.indexToPosX(sounds.size()));
//        speedIndicator.setMarks(buttonPositions);

//        for(int ipos = 0; ipos < soundChooser.numSounds(); ++ipos){
//            buttonPositions.set(ipos, buttonPositions.get(ipos) - buttonWidth / 2.0f + soundChooser.getLeft());
//        }
        volumeSliders?.setTunersAt(noteBoxes, volumes)

    }

}
