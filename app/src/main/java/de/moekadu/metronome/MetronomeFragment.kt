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

    private var speedLimiter: SpeedLimiter? = null

    private var speedText: TextView? = null
    private var playButton: PlayButton? = null
    private var noteView: NoteView? = null
    private var plusButton: ImageButton? = null
    private var clearAllButton: ImageButton? = null

    private val noteListBackup = ArrayList<NoteListItem>()

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
        Log.v("Metronome", "MetronomeFragment:onCreateView");
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_metronome, container, false)

        speedLimiter = SpeedLimiter(PreferenceManager.getDefaultSharedPreferences(requireContext()), viewLifecycleOwner)

//        val constraintLayout = view.findViewById<ConstraintLayout>(R.id.metronome_layout)
//        constraintLayout.setOnTouchListener(object : View.OnTouchListener {
//            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
//                val action = event?.actionMasked
//                when(action) {
//                    MotionEvent.ACTION_DOWN -> {
//                        Log.v("Metronome", "MetronomeFragment.ConstraintLayout: onDown")
//                        return true
//                    }
//                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                        Log.v("Metronome", "MetronomeFragment.ConstraintLayout: onUp")
//                        return true
//                    }
//                }
//                return false
//            }
//        })

        speedText = view.findViewById(R.id.speed_text)
        speedText?.setOnClickListener {
            activity?.let { ctx ->
                val editText = EditText(ctx)
                editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                val pad = Utilities.dp2px(20f).roundToInt()
                editText.setPadding(pad, pad, pad, pad)

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
                        } else if (speedLimiter?.checkNewSpeedAndShowToast(newSpeed, ctx) == true) {
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
                viewModel.speed.value?.let { currentSpeed ->
                    viewModel.setSpeed(currentSpeed + dSpeed)
                }
            }

            override fun onAbsoluteSpeedChanged(newSpeed: Float, nextClickTimeInMillis: Long) {
                viewModel.setSpeed(newSpeed)
                viewModel.syncClickWithUptimeMillis(nextClickTimeInMillis)
            }

            override fun onDown() {
                viewModel.setDisableViewPagerUserInput(true)
            }

            override fun onUp() {
                viewModel.setDisableViewPagerUserInput(false)
            }
        }

        volumeSliders = view.findViewById(R.id.volume_sliders)
        volumeSliders?.volumeChangedListener = object : VolumeSliders.VolumeChangedListener {
            override fun onVolumeChanged(index: Int, volume: Float) {
                viewModel.setNoteListVolume(index, volume)
                if (viewModel.playerStatus.value != PlayerStatus.Playing && context != null) {
                    viewModel.noteList.value?.get(index)?.let { noteListItem ->
                        singleNotePlayer.play(noteListItem.id, noteListItem.volume)
                    }
                }
            }

            override fun onDown(index: Int) {
                viewModel.setDisableViewPagerUserInput(true)
            }
            override fun onUp(index: Int, volume: Float) {
                viewModel.setDisableViewPagerUserInput(false)
            }
        }
        tickVisualizer = view.findViewById(R.id.tick_visualizer)

        playButton = view.findViewById(R.id.play_button)
        playButton?.buttonClickedListener = object : PlayButton.ButtonClickedListener {
            override fun onPause() {
                // Log.v("Metronome", "playButton:onPause()")
                viewModel.pause()
            }

            override fun onPlay() {
                // Log.v("Metronome", "playButton:onPause()")
                viewModel.play()
            }
        }

        noteView = view.findViewById(R.id.note_view)
        noteView?.onNoteClickListener = object : NoteView.OnNoteClickListener {
            override fun onDown(event: MotionEvent?, uid: UId?, noteIndex: Int): Boolean {
//                Log.v("Metronome", "MetronomeFragment.noteView.onClickListener.onDown: noteIndex=$noteIndex")
                if (uid != null) {
                    soundChooser?.setActiveControlButton(uid)
                    noteView?.highlightNote(uid, true)
                }
                return false
            }

            override fun onUp(event: MotionEvent?, uid: UId?, noteIndex: Int): Boolean {
                return true
            }

            override fun onMove(event: MotionEvent?, uid: UId?, noteIndex: Int): Boolean {
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
            val newNote = viewModel.noteList.value?.lastOrNull()?.clone()?.apply { uid = UId.create() }
                    ?: NoteListItem(defaultNote, 1.0f, 1.0f)
            viewModel.addNote(newNote)

            if (soundChooser?.choiceStatus == SoundChooser.Status.Static) {
                noteView?.highlightNote(newNote.uid, true)
            }
        }

        clearAllButton = view.findViewById(R.id.clear_all_button)
        clearAllButton?.setOnClickListener {

            viewModel.noteList.value?.let { notes ->
                deepCopyNoteList(notes, noteListBackup)
                val newNoteList = ArrayList<NoteListItem>()
                newNoteList.add(NoteListItem(defaultNote))
                viewModel.setNoteList(newNoteList)

                getView()?.let { view ->
                    Snackbar.make(view, getString(R.string.all_notes_deleted), Snackbar.LENGTH_LONG)
                            .setAction(R.string.undo) {
                                viewModel.setNoteList(noteListBackup)
                                //noteList?.set(noteListBackup)
                            }.show()
                }
            }
        }

        soundChooser = view.findViewById(R.id.sound_chooser)
        soundChooser?.stateChangedListener = object : SoundChooser.StateChangedListener {
            override fun onSoundChooserDeactivated(uid: UId?) {
                if (uid != null)
                    noteView?.highlightNote(uid, false)
            }
            override fun onNoteIdChanged(uid: UId, noteId: Int, status: SoundChooser.Status) {
                viewModel.setNoteListId(uid, noteId)
                if (viewModel.playerStatus.value != PlayerStatus.Playing && status == SoundChooser.Status.Static && context != null) {
                    viewModel.noteList.value?.firstOrNull { it.uid == uid }?.let { noteListItem ->
                        singleNotePlayer.play(noteListItem.id, noteListItem.volume)
                        if (vibrate)
                            vibratingNote?.vibrate(noteListItem.volume, noteListItem)
                    }
                }
            }

            override fun onVolumeChanged(uid: UId, volume: Float, status: SoundChooser.Status) {
                viewModel.setNoteListVolume(uid, volume)
                if (viewModel.playerStatus.value != PlayerStatus.Playing && context != null) {
                    viewModel.noteList.value?.firstOrNull { it.uid == uid }?.let { noteListItem ->
                        singleNotePlayer.play(noteListItem.id, noteListItem.volume)
                    }
                }
            }

            override fun onNoteRemoved(uid: UId) {
                viewModel.removeNote(uid)
            }

            override fun onNoteMoved(uid: UId, toIndex: Int) {
                viewModel.moveNote(uid, toIndex)
            }

            override fun onStatusChanged(status: SoundChooser.Status) {
                when (status) {
                    SoundChooser.Status.Off -> viewModel.setDisableViewPagerUserInput(false)
                    else -> viewModel.setDisableViewPagerUserInput(true)
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
//            Log.v("Metronome", "MetronomeFragment: viewModel.speed: $it")
            speedText?.text = getString(R.string.bpm, Utilities.getBpmString(it, speedIncrement))
        }

        // set status without animation on load ...
        if (viewModel.playerStatus.value == PlayerStatus.Playing)
            playButton?.changeStatus(PlayButton.STATUS_PLAYING, false)
        else
            playButton?.changeStatus(PlayButton.STATUS_PAUSED, false)

        // then start observing ...
        viewModel.playerStatus.observe(viewLifecycleOwner) {
//            Log.v("Metronome", "MetronomeFragment: viewModel.playerStatus: $it")
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
            noteView?.animateNote(it.uid)
            soundChooser?.animateNote(it.uid)
        }

        viewModel.noteList.observe(viewLifecycleOwner) {
            viewModel.noteList.value?.let {
                noteView?.setNoteList(it)
                soundChooser?.setNoteList(it)
                volumeSliders?.setNoteList(it)
            }
        }

        if (!savedVolumeSlidersFolded || savedSoundChooserNoteIndex >= 0) {
            view.post {
                viewModel.noteList.value?.let { notes ->
                    if (savedSoundChooserNoteIndex >= 0 && savedSoundChooserNoteIndex < notes.size) {
                        val note = notes[savedSoundChooserNoteIndex]
                        soundChooser?.setActiveControlButton(note.uid)
                        noteView?.highlightNote(note.uid, true)
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
        Log.v("Metronome", "MetronomeFragment.onStart()")
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
        if(soundChooser?.choiceStatus == SoundChooser.Status.Static) {
            viewModel.noteList.value?.let { notes ->
                val noteIndex = notes.indexOfFirst { it.uid == soundChooser?.activeNoteUid }
                if (noteIndex >= 0)
                    outState.putInt("soundChooserNoteIndex", noteIndex)
            }
        }
        outState.putBoolean("volumeSlidersFolded", volumeSliders?.folded ?: true)

        super.onSaveInstanceState(outState)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        Log.v("Metronome", "MetronomeFragment.onPrepareOptionsMenu")
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

        val editItem = menu.findItem(R.id.action_edit)
        editItem?.isVisible = false
    }
}
