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

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import de.moekadu.metronome.*
import de.moekadu.metronome.dialogs.ClearAllSavedScenesDialog
import de.moekadu.metronome.dialogs.ImportScenesDialog
import de.moekadu.metronome.dialogs.ScenesSharingDialog
import de.moekadu.metronome.metronomeproperties.durationInNanos
import de.moekadu.metronome.players.PlayerStatus
import de.moekadu.metronome.preferences.AppPreferences
import de.moekadu.metronome.preferences.SpeedLimiter
import de.moekadu.metronome.scenes.Scene
import de.moekadu.metronome.scenes.SceneArchiving
import de.moekadu.metronome.scenes.SceneDatabase
import de.moekadu.metronome.scenes.ScenesAdapter
import de.moekadu.metronome.services.PlayerServiceConnection
import de.moekadu.metronome.viewmodels.MetronomeViewModel
import de.moekadu.metronome.viewmodels.ScenesViewModel
import de.moekadu.metronome.views.TickVisualizerSync
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt

class ScenesFragment : Fragment() {

    private val viewModel by activityViewModels<ScenesViewModel> {
        ScenesViewModel.Factory(AppPreferences.readScenesDatabase(requireActivity()))
    }
    private val metronomeViewModel by activityViewModels<MetronomeViewModel> {
        val playerConnection = PlayerServiceConnection.getInstance(
            requireContext(),
            AppPreferences.readMetronomeBpm(requireActivity()),
            AppPreferences.readMetronomeNoteList(requireActivity()),
            AppPreferences.readIsMute(requireActivity())
        )
        MetronomeViewModel.Factory(playerConnection)
    }

    private var speedLimiter: SpeedLimiter? = null
    private var sharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private var scenesRecyclerView: RecyclerView? = null
    private val scenesAdapter = ScenesAdapter().apply {
        onSceneClickedListener = ScenesAdapter.OnSceneClickedListener { stableId ->
            // this will lead to loading the clicked item
            viewModel.setActiveStableId(stableId)
        }

        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
//                Log.v("Metronome", "ScenesFragment: onItemRangeRemoved")
                val currentStableId = viewModel.activeStableId.value ?: Scene.NO_STABLE_ID
                if (currentStableId != Scene.NO_STABLE_ID) {
                    if (viewModel.scenes.value?.getScene(currentStableId) == null)
                        viewModel.setActiveStableId(Scene.NO_STABLE_ID)
                }
                setPositionNumbers(scenesRecyclerView)
                super.onItemRangeRemoved(positionStart, itemCount)
            }

//            override fun onChanged() {
////                Log.v("Metronome", "ScenesFragment: onChanged")
//                super.onChanged()
//            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
//                Log.v("Metronome", "ScenesFragment: onItemRangeChanged")
                setPositionNumbers(scenesRecyclerView)
                super.onItemRangeChanged(positionStart, itemCount)
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
//                Log.v("Metronome", "ScenesFragment: onItemRangeChanged2")
                setPositionNumbers(scenesRecyclerView)
                super.onItemRangeChanged(positionStart, itemCount, payload)
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
//                Log.v("Metronome", "ScenesFragment: onItemRangeInserted")
                setPositionNumbers(scenesRecyclerView)
                super.onItemRangeInserted(positionStart, itemCount)
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
//                Log.v("Metronome", "ScenesFragment: onItemRangeMoved")
                setPositionNumbers(scenesRecyclerView)
                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
            }

            override fun onStateRestorationPolicyChanged() {
//                Log.v("Metronome", "ScenesFragment: onStateRestorationPolicyChanged")
                setPositionNumbers(scenesRecyclerView)
                super.onStateRestorationPolicyChanged()
            }
        })
    }

    private var lastRemovedItemIndex = -1
    private var lastRemovedItem: Scene? = null

    private var noScenesMessage: TextView? = null

    private var playFab: AppCompatImageButton? = null
    private var playFabStatus = PlayerStatus.Paused

    private var previousSceneButton: AppCompatImageButton? = null
    private var nextSceneButton: AppCompatImageButton? = null

    private val sceneArchiving = SceneArchiving(this)

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.scenes, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)
            val loadDataItem = menu.findItem(R.id.action_load)
            loadDataItem?.isVisible = false

            val editItem = menu.findItem(R.id.action_edit)
            editItem?.isVisible = viewModel.activeStableId.value != Scene.NO_STABLE_ID
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.action_archive -> {
                    if ((viewModel.scenes.value?.size ?: 0) == 0) {
                        Toast.makeText(requireContext(), R.string.database_empty, Toast.LENGTH_LONG).show()
                    } else {
                        sceneArchiving.archiveScenes(viewModel.scenes.value)
                    }
                    return true
                }
                R.id.action_unarchive -> {
                    sceneArchiving.unarchiveScenes()
                    return true
                }
                R.id.action_clear_all -> {
                    clearAllSavedScenes()
                    return true
                }
                R.id.action_share -> {
                    val scenes = viewModel.scenes.value
                    if ((scenes?.size ?: 0) == 0) {
                        Toast.makeText(requireContext(), R.string.no_scenes_for_sharing, Toast.LENGTH_LONG).show()
                    } else if (scenes != null) {
                        val dialogFragment = ScenesSharingDialog(scenes.scenes)
                        dialogFragment.show(parentFragmentManager, "tag")
                    }
                    return true
                }
            }
            return false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        Log.v("Metronome", "ScenesFragment:onCreateView")
        val view = inflater.inflate(R.layout.fragment_scenes, container, false)

        activity?.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        parentFragmentManager.setFragmentResultListener(ClearAllSavedScenesDialog.REQUEST_KEY, viewLifecycleOwner) {
                _, bundle ->
            val clearScenes = bundle.getBoolean(ClearAllSavedScenesDialog.CLEAR_ALL_KEY, false)
            if (clearScenes) {
                viewModel.scenes.value?.clear()
                AppPreferences.writeScenesDatabase(viewModel.scenesAsString, requireActivity())
            }
        }

        parentFragmentManager.setFragmentResultListener(ImportScenesDialog.REQUEST_KEY, viewLifecycleOwner) {
            _, bundle ->
            val scenesString = bundle.getString(ImportScenesDialog.SCENES_KEY, "")
            val scenes = SceneDatabase.stringToScenes(scenesString).scenes
            val taskString = bundle.getString(ImportScenesDialog.INSERT_MODE_KEY, SceneDatabase.InsertMode.Append.toString())
            val task = SceneDatabase.InsertMode.valueOf(taskString)
            loadScenes(scenes, task)
        }

        noScenesMessage = view.findViewById(R.id.noScenesMessage)

        playFab = view.findViewById(R.id.player_controls_play)

        playFab?.setOnClickListener {
            if (metronomeViewModel.playerStatus.value == PlayerStatus.Playing) {
                metronomeViewModel.pause()
            } else {
//                Log.v("Metronome", "TIMECHECK: ScenesFragment.playButton: play pressed")
                metronomeViewModel.play()
            }
        }

        previousSceneButton = view.findViewById(R.id.player_controls_back)
        previousSceneButton?.setOnClickListener {
            viewModel.activeStableId.value?.let { currentStableId ->
                getPreviousSceneStableId(currentStableId)?.let { previousStableId ->
                    viewModel.setActiveStableId(previousStableId)
//                    getDatabaseIndex(previousStableId)?.let { index ->
//                        // TODO: this does not work reliably
//                        scenesRecyclerView?.scrollToPosition(index)
//                    }
                }
            }
        }

        nextSceneButton = view.findViewById(R.id.player_controls_forward)
        nextSceneButton?.setOnClickListener {
            viewModel.activeStableId.value?.let { currentStableId ->
                getNextSceneStableId(currentStableId)?.let { nextStableId ->
                    viewModel.setActiveStableId(nextStableId)
//                    getDatabaseIndex(nextStableId)?.let { index ->
//                        // TODO: this does not work reliably
//                        scenesRecyclerView?.scrollToPosition(index)
//                    }
                }
            }
        }

        scenesRecyclerView = view.findViewById(R.id.scenes)
        scenesRecyclerView?.setHasFixedSize(true)
        scenesRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        scenesRecyclerView?.adapter = scenesAdapter

        val simpleTouchHelper = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {

            val background = activity?.let { ContextCompat.getDrawable(it,
                R.drawable.scene_below_background
            ) }
            val deleteIcon = activity?.let { ContextCompat.getDrawable(it, R.drawable.scene_delete) }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.absoluteAdapterPosition
                val toPos = target.absoluteAdapterPosition
                if(fromPos != toPos) {
                    viewModel.scenes.value?.move(fromPos, toPos)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Log.v("Metronome", "ScenesFragment:onSwiped " + viewHolder.getAdapterPosition())

                lastRemovedItemIndex = viewHolder.absoluteAdapterPosition
                lastRemovedItem = viewModel.scenes.value?.remove(lastRemovedItemIndex)

                (getView() as ConstraintLayout?)?.let { coLayout ->
                    lastRemovedItem?.let { removedItem ->
                        Snackbar.make(coLayout, getString(R.string.scene_deleted), Snackbar.LENGTH_LONG)
                                .setAction(R.string.undo) {
                                    if (lastRemovedItem != null) {
                                        viewModel.scenes.value?.add(lastRemovedItemIndex, removedItem)
                                        lastRemovedItem = null
                                        lastRemovedItemIndex = -1
                                    }
                                }.show()
                    }

                }
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {

                val itemView = viewHolder.itemView

                // not sure why, but this method get's called for viewholder that are already swiped away
                if (viewHolder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
                    // not interested in those
                    return
                }

                background?.setBounds(itemView.left, itemView.top, itemView.right, itemView.bottom)
                background?.draw(c)

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    deleteIcon?.alpha = min(255, (255 * 3 * dX.absoluteValue / itemView.width).toInt())
                    val iconHeight = (0.4f * (itemView.height - itemView.paddingTop - itemView.paddingBottom)).roundToInt()
                    val deleteIconLeft = itemView.right - iconHeight - itemView.paddingRight //itemView.right + iconHeight + itemView.paddingRight + dX.roundToInt()
                    deleteIcon?.setBounds(deleteIconLeft,
                            (itemView.top + itemView.bottom - iconHeight) / 2,
                            deleteIconLeft + iconHeight,
                            (itemView.top + itemView.bottom + iconHeight) / 2)
                    deleteIcon?.draw(c)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val touchHelper = ItemTouchHelper(simpleTouchHelper)
        touchHelper.attachToRecyclerView(scenesRecyclerView)

        metronomeViewModel.noteList.observe(viewLifecycleOwner) {noteList ->
            // all this complicated code just checks is the notelist of the active stable id
            // is equal to the note list in the metronome and if it is not equal, we make sure
            // that the active saved items are unselected
            var areNoteListsEqual = false
            viewModel.activeStableId.value?.let { stableId ->
                viewModel.scenes.value?.scenes?.firstOrNull { it.stableId == stableId }?.noteList?.let { activeNoteList ->
                    areNoteListsEqual = true
                    if (activeNoteList.size != noteList.size) {
                        areNoteListsEqual = false
                    } else {
                      noteList.zip(activeNoteList) {a, b ->
                          if (a.id != b.id || a.volume != b.volume)
                              areNoteListsEqual = false
                      }
                    }
                }
            }

//            Log.v("Metronome", "ScenesFragment.observeNoteList: areNoteListsEqual=$areNoteListsEqual")
            if (!areNoteListsEqual) {
                viewModel.setActiveStableId(Scene.NO_STABLE_ID)
            }
        }

        metronomeViewModel.bpm.observe(viewLifecycleOwner) { bpm ->
            // unselect active item if the bpm doesn't match the metronome bpm
            viewModel.activeStableId.value?.let { stableId ->
                viewModel.scenes.value?.scenes?.firstOrNull { it.stableId == stableId }?.bpm?.let { activeBpm ->
                    if (activeBpm != bpm) {
                        viewModel.setActiveStableId(Scene.NO_STABLE_ID)
                    }
                }
            }
        }

        metronomeViewModel.noteStartedEvent.observe(viewLifecycleOwner) { noteStart ->
            if (viewModel.isVisible && metronomeViewModel.playerStatus.value == PlayerStatus.Playing && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                scenesAdapter.animateNoteAndTickVisualizer(
                    noteStart.note,
                    noteStart.nanoTime,
                    noteStart.noteCount,
                    scenesRecyclerView
                )
            }
        }

        viewModel.scenes.observe(viewLifecycleOwner) { database ->
//            Log.v("Metronome", "ScenesFragment: submitting new data base list to adapter: size: ${database.scenes.size}")
            val databaseCopy = ArrayList<Scene>(database.scenes.size)
            database.scenes.forEach { databaseCopy.add(it.clone()) }
            scenesAdapter.submitList(databaseCopy)
            activity?.let{ AppPreferences.writeScenesDatabase(viewModel.scenesAsString, it) }

            if(database.size == 0)
                noScenesMessage?.visibility = View.VISIBLE
            else
                noScenesMessage?.visibility = View.GONE
            setVisibilityOfBackAndNextButton()
        }

        viewModel.activeStableId.observe(viewLifecycleOwner) { stableId ->
//            Log.v("Metronome", "ScenesFragment: observing stable id: $stableId")
            viewModel.scenes.value?.getScene(stableId)?.let { scene ->
                if (scene.noteList.size > 0)
                    metronomeViewModel.setNoteList(scene.noteList)
                speedLimiter?.let {
                    it.checkSavedItemBpmAndAlert(scene.bpm.bpm, requireContext(), parentFragmentManager)
                    metronomeViewModel.setBpm(it.limit(scene.bpm))
                }
                //metronomeViewModel.setNextNoteIndex(0)
                metronomeViewModel.restartPlayingNoteList() // this will only restart if it is playing

                // we don't show this since it is rather obvious and it would also be shown when fragment is loaded
                //Toast.makeText(requireContext(), getString(R.string.loaded_message, item.title), Toast.LENGTH_SHORT).show()
                activity?.invalidateOptionsMenu()
            }

            scenesAdapter.setActiveStableId(stableId, scenesRecyclerView)
            getDatabaseIndex(stableId)?.let { index ->
                //scenesRecyclerView?.scrollToPosition(index)
                scenesRecyclerView?.smoothScrollToPosition(index)//scrollToPosition(index)
            }
            setVisibilityOfBackAndNextButton()
        }

        viewModel.uri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                sceneArchiving.loadScenes(uri)
                viewModel.loadingFileComplete(ScenesViewModel.FragmentTypes.Scenes)
            }
        }

        playFabStatus = metronomeViewModel.playerStatus.value ?: PlayerStatus.Paused

        metronomeViewModel.playerStatus.observe(viewLifecycleOwner) { playerStatus ->
//            Log.v("Metronome", "ScenesFragment: observing playerStatus: $playerStatus")
            if (playerStatus != playFabStatus) {
                if (playerStatus == PlayerStatus.Playing)
                    playFab?.setImageResource(R.drawable.ic_pause_to_play)
                else
                    playFab?.setImageResource(R.drawable.ic_play_to_pause)
                playFabStatus = playerStatus
                val drawable = playFab?.drawable as Animatable?
                drawable?.start()
            }

            if (playerStatus != PlayerStatus.Playing)
                scenesAdapter.stopAnimation(scenesRecyclerView)
        }

        viewModel.isVisibleLiveData.observe(viewLifecycleOwner) { isVisible ->
            if (!isVisible)
                scenesAdapter.stopAnimation(scenesRecyclerView)
        }


        speedLimiter = SpeedLimiter(PreferenceManager.getDefaultSharedPreferences(requireContext()), viewLifecycleOwner)

        sharedPreferenceChangeListener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                when (key) {
                    "tickvisualization" -> {
                        val type = when (sharedPreferences.getString("tickvisualization", "leftright")) {
                            "leftright" -> TickVisualizerSync.VisualizationType.LeftRight
                            "bounce" -> TickVisualizerSync.VisualizationType.Bounce
                            "fade" -> TickVisualizerSync.VisualizationType.Fade
                            else -> TickVisualizerSync.VisualizationType.LeftRight
                        }
                        scenesAdapter.setTickVisualizationType(type, scenesRecyclerView)
                    }
                    "compact_scenes_layout" -> {
                        val useSimpleMode = sharedPreferences.getBoolean("compact_scenes_layout", false)
                        scenesAdapter.setSimpleMode(useSimpleMode, scenesRecyclerView)
                    }
                }
            }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val type = when (sharedPreferences.getString("tickvisualization", "leftright")) {
            "leftright" -> TickVisualizerSync.VisualizationType.LeftRight
            "bounce" -> TickVisualizerSync.VisualizationType.Bounce
            "fade" -> TickVisualizerSync.VisualizationType.Fade
            else -> TickVisualizerSync.VisualizationType.LeftRight
        }
        scenesAdapter.setTickVisualizationType(type, scenesRecyclerView)

        val useSimpleMode = sharedPreferences.getBoolean("compact_scenes_layout", false)
        scenesAdapter.setSimpleMode(useSimpleMode, scenesRecyclerView)

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
        scenesAdapter.stopAnimation(scenesRecyclerView)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (metronomeViewModel.playerStatus.value == PlayerStatus.Playing)
            playFab?.setImageResource(R.drawable.ic_play_to_pause)
        else
            playFab?.setImageResource(R.drawable.ic_pause_to_play)

    }

    private fun clearAllSavedScenes() {
        val dialog = ClearAllSavedScenesDialog()
        dialog.show(parentFragmentManager, ClearAllSavedScenesDialog.REQUEST_KEY)
    }

    fun getDatabaseString() : String {
        return viewModel.scenesAsString
    }

    fun loadScenes(scenes: List<Scene>, task: SceneDatabase.InsertMode) {
        activity?.let { act ->
            viewModel.scenes.value?.loadScenes(scenes, task)
            AppPreferences.writeScenesDatabase(viewModel.scenesAsString, act)
        }
    }

    fun numScenes() : Int {
        return viewModel.scenes.value?.size ?: 0
    }

    private fun getDatabaseIndex(stableId: Long) : Int? {
        viewModel.scenes.value?.scenes?.let { scenes ->
            val index = scenes.indexOfFirst { it.stableId == stableId }
            if (index >= 0)
                return index
        }
        return null
    }

    private fun getNextSceneStableId(stableId: Long) : Long? {
        viewModel.scenes.value?.scenes?.let { scenes ->
            val index = scenes.indexOfFirst { it.stableId == stableId }
            if (index >= 0 && index < scenes.size - 1) {
                return scenes[index + 1].stableId
            }
        }
        return null
    }

    private fun getPreviousSceneStableId(stableId: Long) : Long? {
        viewModel.scenes.value?.scenes?.let { scenes ->
            val index = scenes.indexOfFirst { it.stableId == stableId }
            if (index > 0) {
                return scenes[index - 1].stableId
            }
        }
        return null
    }

    private fun setVisibilityOfBackAndNextButton() {
        val activeStableId = viewModel.activeStableId.value ?: Scene.NO_STABLE_ID
        val scenes = viewModel.scenes.value?.scenes
//        Log.v("Metronome", "ScenesFragment.setVisibilityOfBackAndNextButton: activeStableId = $activeStableId")
        if (activeStableId == Scene.NO_STABLE_ID || scenes == null) {
            previousSceneButton?.alpha = 0.1f
            nextSceneButton?.alpha = 0.1f
        } else {
            val index = scenes.indexOfFirst { it.stableId == activeStableId }
            previousSceneButton?.alpha = if (index == 0) 0.1f else 1f
            nextSceneButton?.alpha = if (index == scenes.size - 1) 0.1f else 1f
        }
    }
}
