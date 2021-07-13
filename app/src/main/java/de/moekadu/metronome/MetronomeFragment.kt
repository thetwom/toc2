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
import androidx.appcompat.widget.AppCompatTextView
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
            AppPreferences.readMetronomeNoteList(requireActivity()),
            AppPreferences.readIsMute(requireActivity())
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
    private var bpmText: AppCompatTextView? = null
    private var playButton: PlayButton? = null
    private var sceneTitle: TextView? = null
    private var swipeToScenesView: ImageButton? = null

//    private var dummyViewGroupWithTransition: DummyViewGroupWithTransition? = null

    private val noteListBackup = ArrayList<NoteListItem>()

    private var tickVisualizer: TickVisualizerSync? = null

    private var soundChooser: SoundChooser? = null

    private var beatDurationManager: BeatDurationManager? = null

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

//        dummyViewGroupWithTransition = view.findViewById(R.id.dummy_view_group)

        speedLimiter = SpeedLimiter(PreferenceManager.getDefaultSharedPreferences(requireContext()), viewLifecycleOwner)

        bpmText = view.findViewById(R.id.bpm_text)
        bpmText?.setOnClickListener {
            activity?.let { ctx ->
                val editText = EditText(ctx)
                editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                val pad = Utilities.dp2px(20f).roundToInt()
                editText.setPadding(pad, pad, pad, pad)

                viewModel.bpm.value?.let { bpm ->
                    editText.setText(Utilities.getBpmString(bpm.bpm, false))
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
//                    Log.v("Metronome", "MetronomeFragment->speedPanel: currentBpm=$currentBpm, bpmDiff=$bpmDiff")
                    viewModel.setBpm(currentBpm + bpmDiff)
                }
            }

            override fun onAbsoluteSpeedChanged(newBpm: Float, nextClickTimeInMillis: Long) {
                viewModel.setBpm(newBpm)
                viewModel.syncClickWithUptimeMillis(nextClickTimeInMillis)
            }
        }

        tickVisualizer = view.findViewById(R.id.tick_visualizer)
        // tick visualizer controls the note animation since it contains better time synchronization than the soundChooser
        tickVisualizer?.noteStartedListener = TickVisualizerSync.NoteStartedListener {
            soundChooser?.animateNote(it)
        }


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

        soundChooser = view.findViewById(R.id.sound_chooser3)
        soundChooser?.stateChangedListener = object : SoundChooser.StateChangedListener {
            override fun changeNoteId(uid: UId, noteId: Int, status: SoundChooser.Status) {
                viewModel.setNoteListId(uid, noteId)
                if (viewModel.playerStatus.value != PlayerStatus.Playing && status == SoundChooser.Status.Static && context != null) {
                    viewModel.noteList.value?.firstOrNull { it.uid == uid }?.let { noteListItem ->
                        singleNotePlayer.play(noteListItem.id, noteListItem.volume)
                        if (vibrate) {
                            viewModel.bpm.value?.bpmQuarter?.let{ bpmQuarter ->
                                vibratingNote?.vibrate(noteListItem.volume, noteListItem, bpmQuarter)
                            }
                        }
                    }
                }
            }

            override fun changeNoteDuration(uid: UId, duration: NoteDuration) {
                viewModel.setNoteListDuration(uid, duration)
            }

            override fun changeVolume(uid: UId, volume: Float) {
                viewModel.setNoteListVolume(uid, volume)
                if (viewModel.playerStatus.value != PlayerStatus.Playing && context != null) {
                    viewModel.noteList.value?.firstOrNull { it.uid == uid }?.let { noteListItem ->
                        singleNotePlayer.play(noteListItem.id, noteListItem.volume)
                    }
                }
            }

            override fun changeVolume(index: Int, volume: Float) {
                viewModel.setNoteListVolume(index, volume)
                if (viewModel.playerStatus.value != PlayerStatus.Playing && context != null) {
                    viewModel.noteList.value?.getOrNull(index)?.id?.let { id ->
                        singleNotePlayer.play(id, volume)
                    }
                }
            }

            override fun addNote(note: NoteListItem) {
                viewModel.addNote(note)
            }

            override fun removeNote(uid: UId) {
                if (viewModel.noteList.value?.size ?: 0 <= 1)
                    Toast.makeText(context, context?.getString(R.string.cannot_delete_last_note), Toast.LENGTH_LONG).show()
                else
                    viewModel.removeNote(uid)
            }

            override fun removeAllNotes() {
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

            override fun moveNote(uid: UId, toIndex: Int) {
                viewModel.moveNote(uid, toIndex)
            }
        }

        beatDurationManager = BeatDurationManager(view)
        beatDurationManager?.beatDurationChangedListener = BeatDurationManager.BeatDurationChangedListener {
            viewModel.setBpm(it)
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

        // register all observers
        viewModel.bpm.observe(viewLifecycleOwner) { bpm ->
//            Log.v("Metronome", "MetronomeFragment: viewModel.bpm: $it")
            bpmText?.text = getString(R.string.eqbpm, Utilities.getBpmString(bpm.bpm, bpmIncrement))
            beatDurationManager?.setBeatDuration(bpm.noteDuration)
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
                    playButton?.changeStatus(PlayButton.STATUS_PLAYING, viewModel.isVisible) // animate if visible
                }
                PlayerStatus.Paused, null -> {
                    tickVisualizer?.stop()
                    playButton?.changeStatus(PlayButton.STATUS_PAUSED, viewModel.isVisible) // animate if visible
                }
            }
        }

        viewModel.noteStartedEvent.observe(viewLifecycleOwner) { noteStartTime ->
            if (viewModel.isVisible)
                tickVisualizer?.tick(noteStartTime.note.uid, noteStartTime.uptimeMillis, noteStartTime.noteCount)
            // don't animate note here, since this is controlled by the tickVisualizer.noteStartedListener defined above
            // this is done, since the tickvisualizer contains an extra time synchronazation mechanism
            //soundChooser?.animateNote(noteStartTime.note.uid)
        }

        viewModel.bpm.observe(viewLifecycleOwner) {
            tickVisualizer?.bpm = it
        }

        viewModel.noteList.observe(viewLifecycleOwner) {
            viewModel.noteList.value?.let {
//                Log.v("Metronome", "MetronomeFragment: observing noteList, viewModel.isVisible=${viewModel.isVisible}")
                tickVisualizer?.setNoteList(it)
                soundChooser?.setNoteList(it, if (viewModel.isVisible) 200L else 0L)
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
        return view
    }

    override fun onStart() {
//        Log.v("Metronome", "MetronomeFragment.onStart()")
        super.onStart()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    }

//    override fun onResume() {
//        Log.v("Metronome", "MetronomeFragment.onResume()")
//        super.onResume()
        // we need this transition, since after viewpager activity, the first transition is skipped
        // so we do this dummy transition, which is allowed to be skipped
//        dummyViewGroupWithTransition?.dummyTransition()
//    }

    override fun onStop() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        super.onStop()
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

    private fun updateSceneTitleTextAndSwipeView() {
        val editingStableId = scenesViewModel.editingStableId.value ?: Scene.NO_STABLE_ID
        val activeStableId = scenesViewModel.activeStableId.value ?: Scene.NO_STABLE_ID

        if (viewModel.isVisible) // only animate if visible
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
