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

import android.annotation.SuppressLint
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.transition.TransitionManager
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt

/**

 */
class MetronomeFragment : Fragment() {

    private val viewModel by activityViewModels<MetronomeViewModel> {
        val playerConnection = PlayerServiceConnection.getInstance(
                requireContext(),
                AppPreferences.readMetronomeBpm(requireActivity()),
                AppPreferences.readMetronomeNoteList(requireActivity())
        )
        MetronomeViewModel.Factory(playerConnection)
    }

    private val scenesViewModel by activityViewModels<ScenesViewModel> {
        ScenesViewModel.Factory(AppPreferences.readScenesDatabase(requireActivity()))
    }

    private val singleNotePlayer by lazy {
        // Log.v("Metronome", "MetronomeFragment: creating singleNotePlayer")
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

    private var constraintLayout: ConstraintLayout? = null
    private var bpmText: TextView? = null
    private var playButton: PlayButton? = null
    private var noteView: NoteView? = null
    private var plusButton: ImageButton? = null
    private var clearAllButton: ImageButton? = null
    private var sceneTitle: TextView? = null
    private var swipeToScenesView: ImageButton? = null

    private var dummyViewGroupWithTransition: DummyViewGroupWithTransition? = null

    private val noteListBackup = ArrayList<NoteListItem>()

    private var tickVisualizer: TickVisualizer? = null
    private var soundChooser: SoundChooser? = null
    private var savedSoundChooserNoteIndex = -1
    private var volumeSliders: VolumeSliders? = null
    private var savedVolumeSlidersFolded = true

    private var sharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null
    private var bpmIncrement = Utilities.bpmIncrements[InitialValues.bpmIncrementIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        Log.v("Metronome", "MetronomeFragment:onCreateView")
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_metronome, container, false)

        constraintLayout = view.findViewById(R.id.metronome_layout)
        constraintLayout?.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                view.parent.requestDisallowInterceptTouchEvent(false)
            true
        }

        dummyViewGroupWithTransition = view.findViewById(R.id.dummy_view_group)

        speedLimiter = SpeedLimiter(PreferenceManager.getDefaultSharedPreferences(requireContext()), viewLifecycleOwner)

        bpmText = view.findViewById(R.id.bpm_text)
        bpmText?.setOnClickListener {
            activity?.let { ctx ->
                val editText = EditText(ctx)
                editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                val pad = Utilities.dp2px(20f).roundToInt()
                editText.setPadding(pad, pad, pad, pad)

                viewModel.bpm.value?.let { bpm ->
                    editText.setText(Utilities.getBpmString(bpm))
                }

                editText.hint = getString(R.string.bpm, "")
                editText.setSelectAllOnFocus(true)

                val builder = AlertDialog.Builder(ctx).apply {
                    setTitle(R.string.set_new_speed)
                    setPositiveButton(R.string.done) { _, _ ->
                        val newBpmText = editText.text.toString()
                        val newBpm = newBpmText.toFloatOrNull()
                        if (newBpm == null) {
                            Toast.makeText(ctx, "${getString(R.string.invalid_speed)}$newBpmText",
                                    Toast.LENGTH_LONG).show()
                        } else if (speedLimiter?.checkNewBpmAndShowToast(newBpm, ctx) == true) {
                            viewModel.setBpm(newBpm)
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
        bpmText?.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                view.parent.requestDisallowInterceptTouchEvent(false)
            false
        }


        val speedPanel = view.findViewById(R.id.speed_panel) as SpeedPanel?
        speedPanel?.speedChangedListener = object : SpeedPanel.SpeedChangedListener {
            override fun onSpeedChanged(bpmDiff: Float) {
                viewModel.bpm.value?.let { currentBpm ->
                    viewModel.setBpm(currentBpm + bpmDiff)
                }
            }

            override fun onAbsoluteSpeedChanged(newBpm: Float, nextClickTimeInMillis: Long) {
                viewModel.setBpm(newBpm)
                viewModel.syncClickWithUptimeMillis(nextClickTimeInMillis)
            }
        }

        volumeSliders = view.findViewById(R.id.volume_sliders)
        volumeSliders?.volumeChangedListener = VolumeSliders.VolumeChangedListener { index, volume ->
            viewModel.setNoteListVolume(index, volume)
            if (viewModel.playerStatus.value != PlayerStatus.Playing && context != null) {
                viewModel.noteList.value?.get(index)?.let { noteListItem ->
                    singleNotePlayer.play(noteListItem.id, noteListItem.volume)
                }
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

            override fun onDown() {
                view.parent.requestDisallowInterceptTouchEvent(true)
                super.onDown()
            }
        }

        noteView = view.findViewById(R.id.note_view)
        noteView?.onNoteClickListener = object : NoteView.OnNoteClickListener {
            override fun onDown(event: MotionEvent?, uid: UId?, noteIndex: Int): Boolean {
//                Log.v("Metronome", "MetronomeFragment.noteView.onClickListener.onDown: noteIndex=$noteIndex, uid=$uid")
                // we want to make sure that the click is either captured by the sound chooser or be this noteview
                // - when the soundchooser is currently active it won't caputure the click, so we do it
                // - when the soundchooser is inactive, we can't capture the click, since the sound chooser
                //    needs to capture it.
                val captureClick = soundChooser?.choiceStatus != SoundChooser.Status.Off
                if (uid != null) {
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    soundChooser?.setActiveControlButton(uid)
                    noteView?.highlightNote(uid, true)
                }
                return captureClick
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
        plusButton?.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                view.parent.requestDisallowInterceptTouchEvent(true)
            false
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
        clearAllButton?.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                view.parent.requestDisallowInterceptTouchEvent(true)
            false
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

            override fun onStatusChanged(status: SoundChooser.Status) { }
        }

        sceneTitle = view.findViewById(R.id.scene_title_active)
        sceneTitle?.setOnClickListener {
            if (scenesViewModel.editingStableId.value != Scene.NO_STABLE_ID) {
                val editText = EditText(requireContext()).apply {
                    setHint(R.string.save_name)
                    inputType = InputType.TYPE_CLASS_TEXT
                    setText(viewModel.editedSceneTitle.value)
                }
                val dialogBuilder = AlertDialog.Builder(requireContext()).apply {
                    setTitle(R.string.rename_scene)
                    setView(editText)
                    setNegativeButton(R.string.dismiss) { dialog, _ -> dialog.cancel() }
                    setPositiveButton(R.string.done) { _, _ ->
                        var newName = editText.text.toString()
                        if (newName.length > 200) {
                            newName = newName.substring(0, 200)
                            Toast.makeText(requireContext(), getString(R.string.max_allowed_characters, 200), Toast.LENGTH_SHORT).show()
                        }
                        viewModel.setEditedSceneTitle(newName)
                        updateSceneTitleTextAndSwipeView()
                    }
                }
                dialogBuilder.show()
            }
        }

        swipeToScenesView = view.findViewById(R.id.scenes_button)

        swipeToScenesView?.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                view.parent.requestDisallowInterceptTouchEvent(false)
            false
        }

        sharedPreferenceChangeListener = OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                "speedincrement" -> {
                    val newBpmIncrementIndex = sharedPreferences!!.getInt("speedincrement", InitialValues.bpmIncrementIndex)
                    bpmIncrement = Utilities.bpmIncrements[newBpmIncrementIndex]
                    speedPanel?.bpmIncrement = bpmIncrement
                }
                "speedsensitivity" -> {
                    val newBpmSensitivity = sharedPreferences!!.getInt("speedsensitivity", (Utilities.sensitivity2percentage(InitialValues.bpmPerCm)).roundToInt()).toFloat()
                    speedPanel?.bpmPerCm = Utilities.percentage2sensitivity(newBpmSensitivity)
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
        val bpmIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.bpmIncrementIndex)
        bpmIncrement = Utilities.bpmIncrements[bpmIncrementIndex]
        speedPanel?.bpmIncrement = bpmIncrement

        val bpmSensitivity = sharedPreferences.getInt("speedsensitivity", (Utilities.sensitivity2percentage(InitialValues.bpmPerCm)).roundToInt()).toFloat()
        speedPanel?.bpmPerCm = Utilities.percentage2sensitivity(bpmSensitivity)

        vibrate = sharedPreferences.getBoolean("vibrate", false)
        vibratingNote?.strength = sharedPreferences.getInt("vibratestrength", 50)

        savedSoundChooserNoteIndex = savedInstanceState?.getInt("soundChooserNoteIndex", -1) ?: -1
        savedVolumeSlidersFolded = savedInstanceState?.getBoolean("volumeSlidersFolded", true)
                ?: true

        // register all observers
        viewModel.bpm.observe(viewLifecycleOwner) {
//            Log.v("Metronome", "MetronomeFragment: viewModel.bpm: $it")
            bpmText?.text = getString(R.string.bpm, Utilities.getBpmString(it, bpmIncrement))
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
            viewModel.bpm.value?.let { bpm -> tickVisualizer?.tick(Utilities.bpm2ms(bpm)) }
            noteView?.animateNote(it.uid)
            soundChooser?.animateNote(it.uid)
        }

        viewModel.noteList.observe(viewLifecycleOwner) {
            viewModel.noteList.value?.let {
//                Log.v("Metronome", "MetronomeFragment: observing noteList" )
                noteView?.setNoteList(it)
                soundChooser?.setNoteList(it)
                volumeSliders?.setNoteList(it)
            }
        }

        viewModel.isParentViewPagerSwiping.observe(viewLifecycleOwner) {
            swipeToScenesView?.isHovered = it
        }

        scenesViewModel.activeStableId.observe(viewLifecycleOwner) {
            updateSceneTitleTextAndSwipeView()
        }

        scenesViewModel.editingStableId.observe(viewLifecycleOwner) {
            updateSceneTitleTextAndSwipeView()
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
//        Log.v("Metronome", "MetronomeFragment.onStart()")
        super.onStart()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    override fun onResume() {
//        Log.v("Metronome", "MetronomeFragment.onResume()")
        super.onResume()
        // we need this transition, since after viewpager activity, the first transition is skipped
        // so we do this dummy transition, which is allowed to be skipped
        dummyViewGroupWithTransition?.dummyTransition()
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.metronome, menu)
        // super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item : MenuItem) : Boolean {
        when (item.itemId) {
            R.id.action_save -> {
                SaveSceneDialog.save(requireContext(), viewModel.bpm.value, viewModel.noteList.value) { scene ->
                    scenesViewModel.scenes.value?.add(scene)?.let { stableId ->
                        scenesViewModel.setActiveStableId(stableId)
                    }
                    AppPreferences.writeScenesDatabase(scenesViewModel.scenesAsString, requireActivity())
                    true
                }
                return true
            }
        }
        return false
    }

    private fun updateSceneTitleTextAndSwipeView(animate: Boolean = true) {
        val editingStableId = scenesViewModel.editingStableId.value ?: Scene.NO_STABLE_ID
        val activeStableId = scenesViewModel.activeStableId.value ?: Scene.NO_STABLE_ID

        if (animate && soundChooser?.choiceStatus == SoundChooser.Status.Off) // dont animate since otherwise animations will clash
            constraintLayout?.let { TransitionManager.beginDelayedTransition(it)}

        if (editingStableId != Scene.NO_STABLE_ID) {
            sceneTitle?.translationZ = Utilities.dp2px(8f)
            sceneTitle?.isClickable = true
            context?.let {
                sceneTitle?.background = ContextCompat.getDrawable(it, R.drawable.edit_scene_background)
            }

            sceneTitle?.text = getString(R.string.scene, viewModel.editedSceneTitle.value ?:
                    "")
            sceneTitle?.visibility = View.VISIBLE

            swipeToScenesView?.visibility = View.GONE
        }
        else {
            sceneTitle?.translationZ = 0f
            sceneTitle?.isClickable = false
            sceneTitle?.background = null
            swipeToScenesView?.visibility = View.VISIBLE

            if (activeStableId != Scene.NO_STABLE_ID) {
                sceneTitle?.text = getString(R.string.scene, scenesViewModel.scenes.value?.getScene(activeStableId)?.title ?: "")
                sceneTitle?.visibility = View.VISIBLE
            }
            else {
                sceneTitle?.visibility = View.GONE
            }
        }
    }
}
