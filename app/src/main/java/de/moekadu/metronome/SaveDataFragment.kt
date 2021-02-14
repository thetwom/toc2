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

import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StableIdKeyProvider
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt


class SaveDataFragment : Fragment() {

    private val viewModel by activityViewModels<SaveDataViewModel> {
        SaveDataViewModel.Factory(AppPreferences.readSavedItemsDatabase(requireActivity()))
    }
    private val metronomeViewModel by activityViewModels<MetronomeViewModel> {
        val playerConnection = PlayerServiceConnection.getInstance(
                requireContext(),
                AppPreferences.readMetronomeSpeed(requireActivity()),
                AppPreferences.readMetronomeNoteList(requireActivity())
        )
        MetronomeViewModel.Factory(playerConnection)
    }

    private var savedItemRecyclerView: RecyclerView? = null
    private val savedItemsAdapter = SavedItemAdapter().apply {
        onItemClickedListener = SavedItemAdapter.OnItemClickedListener { stableId ->
            viewModel.setActiveStableId(stableId)
        }
    }

    private var lastRemovedItemIndex = -1
    private var lastRemovedItem: SavedItem? = null

    private var noSavedItemsMessage: TextView? = null

    private var playFab: FloatingActionButton? = null
    private var playFabStatus = PlayerStatus.Paused

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
//        super.onPrepareOptionsMenu(menu);
        val settingsItem = menu.findItem(R.id.action_properties)
        settingsItem?.isVisible = false

        val loadDataItem = menu.findItem(R.id.action_load)
        loadDataItem?.isVisible = false

        val saveDataItem = menu.findItem(R.id.action_save)
        saveDataItem.isVisible = false

        val archive = menu.findItem(R.id.action_archive)
        archive?.isVisible = true

        val unarchive = menu.findItem(R.id.action_unarchive)
        unarchive?.isVisible = true

        val clearAll = menu.findItem(R.id.action_clear_all)
        clearAll?.isVisible = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_save_data, container, false)

        noSavedItemsMessage = view.findViewById(R.id.noSavedItemsMessage)

        playFab = view.findViewById(R.id.play_fab)

        playFab?.setOnClickListener {
            if (metronomeViewModel.playerStatus.value == PlayerStatus.Playing)
                metronomeViewModel.pause()
            else
                metronomeViewModel.play()
        }

        savedItemRecyclerView = view.findViewById(R.id.savedItems)
        savedItemRecyclerView?.setHasFixedSize(true)
        savedItemRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        savedItemRecyclerView?.adapter = savedItemsAdapter
//        setSelectionTracker()

        val simpleTouchHelper = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {

            val background = activity?.let { ContextCompat.getDrawable(it, R.drawable.saved_item_below_background) }
            val deleteIcon = activity?.let { ContextCompat.getDrawable(it, R.drawable.saved_item_delete) }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.absoluteAdapterPosition
                val toPos = target.absoluteAdapterPosition
                if(fromPos != toPos) {
                    viewModel.savedItems.value?.move(fromPos, toPos)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Log.v("Metronome", "SaveDataFragment:onSwiped " + viewHolder.getAdapterPosition())

                lastRemovedItemIndex = viewHolder.absoluteAdapterPosition
                lastRemovedItem = viewModel.savedItems.value?.remove(lastRemovedItemIndex)

                (getView() as CoordinatorLayout?)?.let { coLayout ->
                    lastRemovedItem?.let { removedItem ->
                        Snackbar.make(coLayout, getString(R.string.item_deleted), Snackbar.LENGTH_LONG)
                                .setAction(R.string.undo) {
                                    if (lastRemovedItem != null) {
                                        viewModel.savedItems.value?.add(lastRemovedItemIndex, removedItem)
                                        // savedItemsAdapter.addItem(act, removedItem, lastRemovedItemIndex)
                                        lastRemovedItem = null
                                        lastRemovedItemIndex = -1
                                        // updateNoSavedItemsMessage()
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
        touchHelper.attachToRecyclerView(savedItemRecyclerView)

        metronomeViewModel.noteList.observe(viewLifecycleOwner) {noteList ->
            // all this complicated code just checks is the notelist of the active stable id
            // is equal to the notelist in the metronome and if it is not equal, we make sure
            // that the active saved items are unselected
            var areNoteListsEqual = false
            viewModel.activeStableId.value?.let { stableId ->
                viewModel.savedItems.value?.savedItems?.firstOrNull { it.stableId == stableId }?.noteList?.let { activeNoteListString ->
                    val activeNoteList = stringToNoteList(activeNoteListString)
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
            if (!areNoteListsEqual)
                viewModel.setActiveStableId(SavedItem.NO_STABLE_ID)
        }

        metronomeViewModel.speed.observe(viewLifecycleOwner) { speed ->
            // unselect active item if the speed doesn't match the metronome speed
            viewModel.activeStableId.value?.let { stableId ->
                viewModel.savedItems.value?.savedItems?.firstOrNull { it.stableId == stableId }?.bpm?.let { activeSpeed ->
                    if (activeSpeed != speed)
                        viewModel.setActiveStableId(SavedItem.NO_STABLE_ID)
                }
            }
        }

        metronomeViewModel.noteStartedEvent.observe(viewLifecycleOwner) { noteListItem ->
            metronomeViewModel.noteList.value?.let { noteList ->
                val index = noteList.indexOfFirst { it.uid == noteListItem.uid }
                if (index >= 0)
                    savedItemsAdapter.animateNote(index, savedItemRecyclerView)
            }
        }

        viewModel.savedItems.observe(viewLifecycleOwner) { database ->
//            Log.v("Metronome", "SaveDataFragment: submitting new data base list to adapter: size: ${it.savedItems.size}")
            savedItemsAdapter.submitList(ArrayList(database.savedItems))
            activity?.let{AppPreferences.writeSavedItemsDatabase(viewModel.savedItemsAsString, it)}

            if(database.size == 0)
                noSavedItemsMessage?.visibility = View.VISIBLE
            else
                noSavedItemsMessage?.visibility = View.GONE
        }

        viewModel.activeStableId.observe(viewLifecycleOwner) { stableId ->
            viewModel.savedItems.value?.getItem(stableId)?.let { item ->
                val newNoteList = stringToNoteList(item.noteList)
                if (newNoteList.size > 0)
                    metronomeViewModel.setNoteList(newNoteList)
                // TODO: Add speed checks (see loadSettings from MainActivity)
                metronomeViewModel.setSpeed(item.bpm)
                metronomeViewModel.setNextNoteIndex(0)
            }

            savedItemsAdapter.setActiveStableId(stableId, savedItemRecyclerView)
        }

        if (metronomeViewModel.playerStatus.value == PlayerStatus.Playing)
            playFab?.setImageResource(R.drawable.ic_play_to_pause)
        else
            playFab?.setImageResource(R.drawable.ic_pause_to_play)
        playFabStatus = metronomeViewModel.playerStatus.value ?: PlayerStatus.Paused

        metronomeViewModel.playerStatus.observe(viewLifecycleOwner) { playerStatus ->
            if (playerStatus != playFabStatus) {
                if (playerStatus == PlayerStatus.Playing)
                    playFab?.setImageResource(R.drawable.ic_pause_to_play)
                else
                    playFab?.setImageResource(R.drawable.ic_play_to_pause)
                playFabStatus = playerStatus
                val drawable = playFab?.drawable as Animatable?
                drawable?.start()
            }
        }
        return view
    }

}
