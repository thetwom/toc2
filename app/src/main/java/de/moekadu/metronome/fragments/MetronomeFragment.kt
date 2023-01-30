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

package de.moekadu.metronome.fragments

import android.annotation.SuppressLint
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.snackbar.Snackbar
import de.moekadu.metronome.*
import de.moekadu.metronome.dialogs.BpmInputDialog
import de.moekadu.metronome.dialogs.RenameSceneDialog
import de.moekadu.metronome.dialogs.SaveSceneDialog
import de.moekadu.metronome.metronomeproperties.*
import de.moekadu.metronome.misc.InitialValues
import de.moekadu.metronome.misc.TapInEvaluator
import de.moekadu.metronome.misc.Utilities
import de.moekadu.metronome.players.PlayerStatus
import de.moekadu.metronome.players.SingleNotePlayer
import de.moekadu.metronome.players.VibratingNote
import de.moekadu.metronome.players.vibratingNoteHasHardwareSupport
import de.moekadu.metronome.preferences.AppPreferences
import de.moekadu.metronome.preferences.SpeedLimiter
import de.moekadu.metronome.scenes.Scene
import de.moekadu.metronome.services.PlayerServiceConnection
import de.moekadu.metronome.viewmanagers.BeatDurationManager
import de.moekadu.metronome.viewmodels.MetronomeViewModel
import de.moekadu.metronome.viewmodels.ScenesViewModel
import de.moekadu.metronome.views.PlayButton
import de.moekadu.metronome.views.SoundChooser
import de.moekadu.metronome.views.SpeedPanel
import de.moekadu.metronome.views.TickVisualizerSync
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
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

    private var tapInEvaluator = TapInEvaluator(
        5,
        Utilities.bpm2millis(InitialValues.maximumBpm),
        Utilities.bpm2millis(InitialValues.minimumBpm)
    )

    private var constraintLayout: ConstraintLayout? = null
    private var bpmText: AppCompatTextView? = null
    private var playButton: PlayButton? = null
    private var sceneTitle: TextView? = null
    private var swipeToScenesView: ImageButton? = null

//    private var dummyViewGroupWithTransition: DummyViewGroupWithTransition? = null

    private val noteListBackup = ArrayList<NoteListItem>()

    private var tickVisualizer: TickVisualizerSync? = null

    private var tickingCircle = false

    private lateinit var soundChooser: SoundChooser

    private var beatDurationManager: BeatDurationManager? = null

    private var sharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null
    private var bpmIncrement = Utilities.bpmIncrements[InitialValues.bpmIncrementIndex]

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.metronome, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.action_save -> {
                    val dialogFragment = SaveSceneDialog()
                    dialogFragment.show(parentFragmentManager, SaveSceneDialog.REQUEST_KEY)
                    return true
                }
            }
            return false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        Log.v("Metronome", "MetronomeFragment:onCreateView")
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_metronome, container, false)

        activity?.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        parentFragmentManager.setFragmentResultListener(SaveSceneDialog.REQUEST_KEY, viewLifecycleOwner) {
                _, bundle ->
            val title = bundle.getString(SaveSceneDialog.TITLE_KEY, "no title")
            saveCurrentSettingIntoScene(title)
        }

        parentFragmentManager.setFragmentResultListener(RenameSceneDialog.REQUEST_KEY, viewLifecycleOwner) {
                _, bundle ->
            val newName = bundle.getString(RenameSceneDialog.TITLE_KEY, "")
            viewModel.setEditedSceneTitle(newName)
            updateSceneTitleTextAndSwipeView()
        }

        parentFragmentManager.setFragmentResultListener(BpmInputDialog.REQUEST_KEY, viewLifecycleOwner) {
            _, bundle ->
            //Log.v("Metronome", "MetronomeFragment.fragmentResultListener: $requestKey")
            val bpmValue = bundle.getFloat(BpmInputDialog.BPM_KEY, Float.MAX_VALUE)
            if (bpmValue != Float.MAX_VALUE && speedLimiter?.checkNewBpmAndShowToast(bpmValue, requireContext()) == true) {
                viewModel.setBpm(bpmValue)
            }
        }

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
            viewModel.bpm.value?.let {
                val dialogFragment = BpmInputDialog(it)
                dialogFragment.show(parentFragmentManager, BpmInputDialog.REQUEST_KEY)
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

            override fun onTapInPressed(systemNanosAtTap: Long) {
                tapInEvaluator.tap(systemNanosAtTap)
                if (tapInEvaluator.dtNanos != TapInEvaluator.NOT_AVAILABLE)
                    viewModel.setBpm(Utilities.nanos2bpm(tapInEvaluator.dtNanos))
                if (tapInEvaluator.predictedNextTapNanos != TapInEvaluator.NOT_AVAILABLE)
                    viewModel.syncClickWithSystemNanos(tapInEvaluator.predictedNextTapNanos)
            }
//            override fun onAbsoluteSpeedChanged(newBpm: Float, nextClickTimeInMillis: Long) {
//                viewModel.setBpm(newBpm)
//                viewModel.syncClickWithUptimeMillis(nextClickTimeInMillis)
//            }
        }

        tickVisualizer = view.findViewById(R.id.tick_visualizer)
        // tick visualizer controls the note animation since it contains better time synchronization than the soundChooser
        tickVisualizer?.noteStartedListener = TickVisualizerSync.NoteStartedListener { note, startNanos, endNanos, count ->
            soundChooser.animateNote(note.uid)
            if (tickingCircle)
                speedPanel?.tick(note, startNanos, endNanos, count)
        }


        playButton = view.findViewById(R.id.play_button)
        playButton?.buttonClickedListener = object : PlayButton.ButtonClickedListener {
            override fun onPause() {
                // Log.v("Metronome", "playButton:onPause()")
                viewModel.pause()
            }

            override fun onPlay() {
//                Log.v("Metronome", "MetronomeFragment: playButton:onPlay()")
//                Log.v("Metronome", "TIMECHECK: MetronomeFragment.playButton: play pressed")
                viewModel.play()
            }

            override fun onDown() {
                view.parent.requestDisallowInterceptTouchEvent(true)
                super.onDown()
            }
        }

        soundChooser = view.findViewById(R.id.sound_chooser3)
        soundChooser.stateChangedListener = object : SoundChooser.StateChangedListener {
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
                if ((viewModel.noteList.value?.size ?: 0) <= 1)
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
                val dialog = RenameSceneDialog.createInstance(viewModel.editedSceneTitle.value ?: "")
                dialog.show(parentFragmentManager, RenameSceneDialog.REQUEST_KEY)
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
                    val newBpmIncrementIndex = sharedPreferences!!.getInt("speedincrement",
                        InitialValues.bpmIncrementIndex
                    )
                    bpmIncrement = Utilities.bpmIncrements[newBpmIncrementIndex]
                    speedPanel?.bpmIncrement = bpmIncrement
                }
                "minimumspeed" -> {
                    sharedPreferences.getString("minimumspeed", InitialValues.minimumBpm.toString())?.toFloatOrNull()?.let {
                        tapInEvaluator = TapInEvaluator(
                            tapInEvaluator.numHistoryValues,
                            tapInEvaluator.minimumAllowedDtInMillis,
                            Utilities.bpm2millis(it))
                    }
                }
                "maximumspeed" -> {
                    sharedPreferences.getString("maximumspeed", InitialValues.maximumBpm.toString())?.toFloatOrNull()?.let {
                        tapInEvaluator = TapInEvaluator(
                            tapInEvaluator.numHistoryValues,
                            Utilities.bpm2millis(it),
                            tapInEvaluator.maximumAllowedDtInMillis,
                        )
                    }
                }
                "speedsensitivity" -> {
                    val newBpmSensitivity = sharedPreferences!!.getInt("speedsensitivity", (Utilities.sensitivity2percentage(
                        InitialValues.bpmPerCm
                    )).roundToInt()).toFloat()
                    speedPanel?.bpmPerCm = Utilities.percentage2sensitivity(newBpmSensitivity)
                }
                "vibrate" -> {
                    vibrate = sharedPreferences.getBoolean("vibrate", false)
                }
                "vibratestrength" -> {
                    vibratingNote?.strength = sharedPreferences.getInt("vibratestrength", 50)
                }
                "tickvisualization" -> {
                    when (sharedPreferences.getString("tickvisualization", "leftright")) {
                        "leftright" -> tickVisualizer?.visualizationType = TickVisualizerSync.VisualizationType.LeftRight
                        "bounce" -> tickVisualizer?.visualizationType = TickVisualizerSync.VisualizationType.Bounce
                        "fade" -> tickVisualizer?.visualizationType = TickVisualizerSync.VisualizationType.Fade
                    }
                }
                "tickingcircle" -> {
                    tickingCircle = sharedPreferences.getBoolean("tickingcircle", false)
                    if (!tickingCircle)
                        speedPanel?.stopTicking()
                }
            }
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val bpmIncrementIndex = sharedPreferences.getInt("speedincrement",
            InitialValues.bpmIncrementIndex
        )
        bpmIncrement = Utilities.bpmIncrements[bpmIncrementIndex]
        speedPanel?.bpmIncrement = bpmIncrement

        val minimumSpeed = sharedPreferences.getString("minimumspeed", InitialValues.minimumBpm.toString())?.toFloatOrNull()
        val maximumSpeed = sharedPreferences.getString("maximumspeed", InitialValues.maximumBpm.toString())?.toFloatOrNull()
        if (minimumSpeed != null && maximumSpeed != null)
            tapInEvaluator = TapInEvaluator(tapInEvaluator.numHistoryValues, Utilities.bpm2millis(maximumSpeed), Utilities.bpm2millis(minimumSpeed))

        val bpmSensitivity = sharedPreferences.getInt("speedsensitivity", (Utilities.sensitivity2percentage(
            InitialValues.bpmPerCm
        )).roundToInt()).toFloat()
        speedPanel?.bpmPerCm = Utilities.percentage2sensitivity(bpmSensitivity)

        vibrate = sharedPreferences.getBoolean("vibrate", false)
        vibratingNote?.strength = sharedPreferences.getInt("vibratestrength", 50)

        when (sharedPreferences.getString("tickvisualization", "leftright")) {
            "leftright" -> tickVisualizer?.visualizationType = TickVisualizerSync.VisualizationType.LeftRight
            "bounce" -> tickVisualizer?.visualizationType = TickVisualizerSync.VisualizationType.Bounce
            "fade" -> tickVisualizer?.visualizationType = TickVisualizerSync.VisualizationType.Fade
        }
        tickingCircle = sharedPreferences.getBoolean("tickingcircle", false)

        // register all observers
        viewModel.bpm.observe(viewLifecycleOwner) { bpm ->
//            Log.v("Metronome", "MetronomeFragment: viewModel.bpm: $it")
            tickVisualizer?.bpm = bpm
//            tickVisualizer?.waitForInputToTick()
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
                    speedPanel?.stopTicking()
                    playButton?.changeStatus(PlayButton.STATUS_PAUSED, viewModel.isVisible) // animate if visible
                }
            }
        }

        viewModel.isVisibleLiveData.observe(viewLifecycleOwner) { isVisible ->
            if (!isVisible)
                tickVisualizer?.stop()
        }

        viewModel.noteStartedEvent.observe(viewLifecycleOwner) { noteStartTime ->
            // only tick when it is explicitly playing otherwise the ticker could play forever
            if (viewModel.isVisible && viewModel.playerStatus.value == PlayerStatus.Playing && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                //tickVisualizer?.tick(noteStartTime.note.uid, noteStartTime.nanoTime, noteStartTime.noteCount)
                tickVisualizer?.tick(noteStartTime.note, noteStartTime.nanoTime, noteStartTime.noteCount)
            // don't animate note here, since this is controlled by the tickVisualizer.noteStartedListener defined above
            // this is done, since the tickvisualizer contains an extra time synchronazation mechanism
            //soundChooser?.animateNote(noteStartTime.note.uid)
        }

        viewModel.noteList.observe(viewLifecycleOwner) {
            viewModel.noteList.value?.let {
//                Log.v("Metronome", "MetronomeFragment: observing noteList, viewModel.isVisible=${viewModel.isVisible}")
//                tickVisualizer?.setNoteList(it)
                soundChooser.setNoteList(it, if (viewModel.isVisible) 200L else 0L)
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
        tickVisualizer?.stop()
        super.onStop()
    }

    private fun updateSceneTitleTextAndSwipeView() {
        val editingStableId = scenesViewModel.editingStableId.value ?: Scene.NO_STABLE_ID
        val activeStableId = scenesViewModel.activeStableId.value ?: Scene.NO_STABLE_ID

        if (viewModel.isVisible) {// only animate if visible
            constraintLayout?.let {
                TransitionManager.beginDelayedTransition(it,
                    AutoTransition().excludeTarget(soundChooser, true)
                )
            }
        }

        if (editingStableId != Scene.NO_STABLE_ID) {
            sceneTitle?.translationZ = Utilities.dp2px(4f)
            sceneTitle?.isClickable = true
            context?.let {
                sceneTitle?.background = ContextCompat.getDrawable(it,
                    R.drawable.edit_scene_background
                )
            }

            sceneTitle?.text = getString(
                R.string.scene, viewModel.editedSceneTitle.value ?:
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

    private fun saveCurrentSettingIntoScene(title: String) {
        val bpm = viewModel.bpm.value
        val noteList = viewModel.noteList.value

        if (bpm != null && noteList != null) {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy")
            val timeFormat = SimpleDateFormat("HH:mm")
            val calendarDate = Calendar.getInstance().time
            val date = dateFormat.format(calendarDate)
            val time = timeFormat.format(calendarDate)
            val noteListCopy = ArrayList<NoteListItem>(noteList.size)
            deepCopyNoteList(noteList, noteListCopy)
            for (note in noteListCopy)
                note.uid = UId.create()
            val scene = Scene(title, date, time, bpm, noteListCopy, Scene.NO_STABLE_ID)
            scenesViewModel.scenes.value?.add(scene)?.let { stableId ->
                scenesViewModel.setActiveStableId(stableId)
            }
            AppPreferences.writeScenesDatabase(
                scenesViewModel.scenesAsString,
                requireActivity()
            )
            context?.let {
                Toast.makeText(
                    it, it.getString(R.string.saved_scene_message, scene.title),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
