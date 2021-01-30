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

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import kotlin.math.abs
import kotlin.math.roundToInt

/**

 */
class MetronomeFragment : Fragment() {

    private val viewModel by activityViewModels<MetronomeViewModel> {
        val playerConnection = PlayerServiceConnection.getInstance(
                requireContext(),
                AppPreferences.readMetronomeSpeed(requireActivity()),
                AppPreferences.readMetronomeNoteList(requireActivity())
        )
        //val playerConnection = PlayerServiceConnection(requireContext())
        MetronomeViewModel.Factory(playerConnection)
    }

    private val singleNotePlayer by lazy {
        Log.v("Metronome", "MetronomeFragment: creating singleNotePlayer")
        SingleNotePlayer(requireContext(), this)
    }

    private val vibratingNote by lazy {
        if (vibratingNoteHasHardwareSupport(requireContext()))
            VibratingNote(requireContext())
        else
            null
    }
    private var vibrate = false

    private var speedText: TextView? = null
    private var playButton: PlayButton? = null
    private var noteView: NoteView? = null
    private var plusButton: ImageButton? = null
    private var clearAllButton: ImageButton? = null

    private val noteListChangeListener = object : NoteList.NoteListChangedListener {

        override fun onNoteAdded(note: NoteListItem, index: Int) {}
        override fun onNoteRemoved(note: NoteListItem, index: Int) {}
        override fun onNoteMoved(note: NoteListItem, fromIndex: Int, toIndex: Int) {}

        override fun onVolumeChanged(note: NoteListItem, index: Int) {
            if (viewModel.playerStatus.value != PlayerStatus.Playing && context != null) {
                singleNotePlayer.play(note.id, note.volume)
            }
        }

        override fun onNoteIdChanged(note: NoteListItem, index: Int) {
            if (soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC
                    && viewModel.playerStatus.value != PlayerStatus.Playing
                    && context != null) {
                singleNotePlayer.play(note.id, note.volume)
                if (vibrate)
                    vibratingNote?.vibrate(note.volume, note)
            }
        }

        override fun onDurationChanged(note: NoteListItem, index: Int) {}

        override fun onAllNotesReplaced(noteList: NoteList) {}
    }

    /// Note list, that contains current metronome notes.
    var noteList: NoteList? = null
        set(value) {
            field?.unregisterNoteListChangedListener(noteListChangeListener)
            field = value
            field?.registerNoteListChangedListener(noteListChangeListener)
            noteView?.noteList = value
            volumeSliders?.noteList = value
            soundChooser?.noteList = value
        }

    private val noteListBackup = NoteList()

    private var tickVisualizer: TickVisualizer? = null
    private var soundChooser: SoundChooser? = null
    private var savedSoundChooserNoteIndex = -1
    private var volumeSliders: VolumeSliders? = null
    private var savedVolumeSlidersFolded = true

    private var sharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null
    private var speedIncrement = Utilities.speedIncrements[InitialValues.speedIncrementIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Log.v("Metronome", "MetronomeFragment:onCreateView");
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_metronome, container, false)

        speedText = view.findViewById(R.id.speed_text)
        speedText?.setOnClickListener {
            activity?.let { ctx ->
                val editText = EditText(ctx)
                editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                val pad = Utilities.dp2px(20f).roundToInt()
                editText.setPadding(pad, pad, pad, pad)

                //playerService?.speed?.let {speed ->
                viewModel.speed.value?.let { speed ->
                    editText.setText(Utilities.getBpmString(speed))
                }

                editText.hint = getString(R.string.bpm, "")
                editText.setSelectAllOnFocus(true)

                val builder = AlertDialog.Builder(ctx).apply {
                    setTitle(R.string.set_new_speed)
                    setPositiveButton(R.string.done) { _, _ ->
                        val newSpeedText = editText.text.toString()
                        val newSpeed = newSpeedText.toFloatOrNull()
                        if (newSpeed == null) {
                            Toast.makeText(ctx, "${getString(R.string.invalid_speed)}$newSpeedText",
                                    Toast.LENGTH_LONG).show()
                        } else if (checkSpeedAndShowToastOnFailure(newSpeed)) {
                            //playerService?.speed = newSpeed
                            viewModel.setSpeed(newSpeed)
                        }
                    }
                    setNegativeButton(R.string.abort) { dialog, _ ->
                        dialog?.cancel()
                    }
                    setView(editText)
                }
                val dialog = builder.create()
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                // this seems only necessary on some devices ...
                dialog.setOnDismissListener {
                    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                }
                dialog.show()
                editText.requestFocus()
            }
        }

        val speedPanel = view.findViewById(R.id.speed_panel) as SpeedPanel?
        speedPanel?.speedChangedListener = object : SpeedPanel.SpeedChangedListener {
            override fun onSpeedChanged(dSpeed: Float) {
                //playerService?.addValueToSpeed(dSpeed)
                viewModel.speed.value?.let { currentSpeed ->
                    viewModel.setSpeed(currentSpeed + dSpeed)
                }
            }

            override fun onAbsoluteSpeedChanged(newSpeed: Float, nextClickTimeInMillis: Long) {
                //playerService?.speed = newSpeed
                viewModel.setSpeed(newSpeed)
                viewModel.syncClickWithUptimeMillis(nextClickTimeInMillis)
//                playerService?.syncClickWithUptimeMillis(nextClickTimeInMillis)
            }
        }

        volumeSliders = view.findViewById(R.id.volume_sliders)
        tickVisualizer = view.findViewById(R.id.tick_visualizer)

        playButton = view.findViewById(R.id.play_button)
        playButton?.buttonClickedListener = object : PlayButton.ButtonClickedListener {
            override fun onPause() {
                // Log.v("Metronome", "playButton:onPause()")
                //playerService?.stopPlay()
                viewModel.pause()
            }

            override fun onPlay() {
                // Log.v("Metronome", "playButton:onPause()")
//                playerService?.startPlay()
                viewModel.play()
            }
        }

        noteView = view.findViewById(R.id.note_view)
        noteView?.onNoteClickListener = object : NoteView.OnNoteClickListener {
            override fun onDown(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {
//                Log.v("Metronome", "MetronomeFragment.noteView.onClickListener.onDown: noteIndex=$noteIndex")
                if (note != null) {
                    soundChooser?.setActiveNote(note)
                    noteView?.highlightNote(note, true)
                }
                return false
            }

            override fun onUp(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {
                return true
            }

            override fun onMove(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {
                return true
            }
        }

        noteView?.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
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
//                Log.v("Metronome", "MetronomeFragment.noteView.onLayoutChangedListener: height = ${bottom - top}, ${v.height}, ${noteView?.height}")
            }
        }

        plusButton = view.findViewById(R.id.plus_button)
        plusButton?.setOnClickListener {
            if (noteList?.size == 0) {
                noteList?.add(NoteListItem(defaultNote, 1.0f, -1.0f))
            } else {
                noteList?.let {
                    val n = it.last().clone()
                    it.add(n)
                }
            }

            if (soundChooser?.choiceStatus == SoundChooser.CHOICE_STATIC) {
                noteList?.let { notes ->
                    val noteIndex = notes.size - 1
                    val note = notes[noteIndex]
                    soundChooser?.setActiveNote(note)
                    noteView?.highlightNote(note, true)
                }
            }
        }

        clearAllButton = view.findViewById(R.id.clear_all_button)
        clearAllButton?.setOnClickListener {

            noteList?.let { notes ->
                noteListBackup.set(notes)
                val newNoteList = NoteList()
                newNoteList.add(NoteListItem(defaultNote), 0)
                notes.set(newNoteList)

                getView()?.let { view ->
                    Snackbar.make(view, getString(R.string.all_notes_deleted), Snackbar.LENGTH_LONG)
                            .setAction(R.string.undo) {
                                noteList?.set(noteListBackup)
                            }.show()
                }
            }
        }

        soundChooser = view.findViewById(R.id.sound_chooser)
        soundChooser?.stateChangedListener = object : SoundChooser.StateChangedListener {
            override fun onSoundChooserDeactivated(note: NoteListItem?) {
                if (note != null)
                    noteView?.highlightNote(note, false)
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
                "vibrate" -> {
                    vibrate = sharedPreferences.getBoolean("vibrate", false)
                }
                "vibratestrength" -> {
                    vibratingNote?.strength = sharedPreferences.getInt("vibratestrength", 50)
                }
            }
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val speedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex)
        speedIncrement = Utilities.speedIncrements[speedIncrementIndex]
        speedPanel?.speedIncrement = speedIncrement

        val speedSensitivity = sharedPreferences.getInt("speedsensitivity", (Utilities.sensitivity2percentage(InitialValues.speedSensitivity)).roundToInt()).toFloat()
        speedPanel?.sensitivity = Utilities.percentage2sensitivity(speedSensitivity)

        vibrate = sharedPreferences.getBoolean("vibrate", false)
        vibratingNote?.strength = sharedPreferences.getInt("vibratestrength", 50)

        savedSoundChooserNoteIndex = savedInstanceState?.getInt("soundChooserNoteIndex", -1) ?: -1
        savedVolumeSlidersFolded = savedInstanceState?.getBoolean("volumeSlidersFolded", true)
                ?: true

        // register all observers
        viewModel.speed.observe(viewLifecycleOwner) {
            Log.v("Metronome", "MetronomeFragment: viewModel.speed: $it")
            speedText?.text = getString(R.string.bpm, Utilities.getBpmString(it, speedIncrement))
        }

        viewModel.playerStatus.observe(viewLifecycleOwner) {
            Log.v("Metronome", "MetronomeFragment: viewModel.playerStatus: $it")
            when (it) {
                PlayerStatus.Playing -> {
                    playButton?.changeStatus(PlayButton.STATUS_PLAYING, true)
                }
                PlayerStatus.Paused, null -> {
                    tickVisualizer?.stop()
                    playButton?.changeStatus(PlayButton.STATUS_PAUSED, true)
                }
            }
        }

        viewModel.noteStartedEvent.observe(viewLifecycleOwner) {
            viewModel.speed.value?.let { speed -> tickVisualizer?.tick(Utilities.speed2dt(speed)) }
            noteView?.animateNote(it)
            soundChooser?.animateNote(it)
        }

        viewModel.noteList.observe(viewLifecycleOwner) {
            viewModel.noteList.value?.let {
                noteList = it
            }
        }

        if (!savedVolumeSlidersFolded || savedSoundChooserNoteIndex >= 0) {
            view.post {
                noteList?.let { notes ->
                    if (savedSoundChooserNoteIndex >= 0 && savedSoundChooserNoteIndex < notes.size) {
                        val note = notes[savedSoundChooserNoteIndex]
                        soundChooser?.setActiveNote(note)
                        noteView?.highlightNote(note, true)
                        soundChooser?.activateStaticChoices(0L)
                    }
                }

                if (!savedVolumeSlidersFolded)
                    volumeSliders?.unfold(0L)
            }
        }
        return view
    }


    override fun onStart() {
        super.onStart()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    override fun onStop() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        super.onStop()
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

    override fun onPrepareOptionsMenu(menu: Menu) {
//        super.onPrepareOptionsMenu(menu);
        val settingsItem = menu.findItem(R.id.action_properties)
        settingsItem?.isVisible = true

        val loadDataItem = menu.findItem(R.id.action_load)
        loadDataItem?.isVisible = true

        val saveDataItem = menu.findItem(R.id.action_save)
        saveDataItem?.isVisible = true

        val archive = menu.findItem(R.id.action_archive)
        archive?.isVisible = false

        val unarchive = menu.findItem(R.id.action_unarchive)
        unarchive?.isVisible = false

        val clearAll = menu.findItem(R.id.action_clear_all)
        clearAll?.isVisible = false
    }

//    @SuppressLint("SwitchIntDef")
//    private fun updateView() {
////        Log.v("Metronome", "MetronomeFragment.updateView")
//        //playerService?.playbackState?.let { playbackState ->
//        when(viewModel.playerStatus.value) {
//            PlayerStatus.Playing -> {
//                playButton?.changeStatus(PlayButton.STATUS_PLAYING, false)
//            }
//            PlayerStatus.Paused, null -> {
//                tickVisualizer?.stop()
//                playButton?.changeStatus(PlayButton.STATUS_PAUSED, false)
//            }
//        }
//
//        viewModel.speed.value?.let {
//            speedText?.text = getString(R.string.bpm, Utilities.getBpmString(it, speedIncrement))
//        }
//    }

    private fun checkSpeedAndShowToastOnFailure(speed: Float): Boolean {

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val minimumSpeedString = sharedPreferences.getString("minimumspeed", InitialValues.minimumSpeed.toString()) ?: InitialValues.minimumSpeed.toString()
        val minimumSpeed = minimumSpeedString.toFloat()
        val maximumSpeedString = sharedPreferences.getString("maximumspeed", InitialValues.maximumSpeed.toString()) ?: InitialValues.maximumSpeed.toString()
        val maximumSpeed = maximumSpeedString.toFloat()
        val speedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex)
        val speedIncrement = Utilities.speedIncrements[speedIncrementIndex]
        val tolerance = 1.0e-6f
        var message: String? = null
        if(speed < minimumSpeed - tolerance)
            message = getString(R.string.speed_too_small, Utilities.getBpmString(speed), Utilities.getBpmString(minimumSpeed))
        if(speed > maximumSpeed + tolerance)
            message = getString(R.string.speed_too_large, Utilities.getBpmString(speed), Utilities.getBpmString(maximumSpeed))
        if(abs(speed / speedIncrement - (speed / speedIncrement).roundToInt()) > tolerance)
            message = getString(R.string.inconsistent_increment, Utilities.getBpmString(speed), Utilities.getBpmString(speedIncrement))
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }

        return message == null
    }
}
