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
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
// import de.moekadu.metronome.SavedItemAdapter.SavedItem
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt


class SaveDataFragment : Fragment() {

    private val viewModel by activityViewModels<SaveDataViewModel> {
        val act = activity ?: throw RuntimeException("Activity not available")
        SaveDataViewModel.Factory(act)
    }

    private var savedItemRecyclerView: RecyclerView? = null
    private val savedItemsAdapter = SavedItemAdapter()

    private var lastRemovedItemIndex = -1
    private var lastRemovedItem: SavedItem? = null

    private var noSavedItemsMessage: TextView? = null

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

//        deleteTextSize = Utilities.sp_to_px(18);

        noSavedItemsMessage = view.findViewById(R.id.noSavedItemsMessage)
        updateNoSavedItemsMessage()

        savedItemRecyclerView = view.findViewById(R.id.savedItems)
        savedItemRecyclerView?.setHasFixedSize(true)
        savedItemRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        savedItemRecyclerView?.adapter = savedItemsAdapter

        val simpleTouchHelper = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {

            val background = activity?.let { ContextCompat.getDrawable(it, R.drawable.saved_item_below_background) }
            val deleteIcon = activity?.let { ContextCompat.getDrawable(it, R.drawable.saved_item_delete) }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if(fromPos != toPos) {
                    viewModel.savedItems.value?.move(fromPos, toPos)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Log.v("Metronome", "SaveDataFragment:onSwiped " + viewHolder.getAdapterPosition())

                lastRemovedItemIndex = viewHolder.adapterPosition
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
                if (viewHolder.adapterPosition == -1) {
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

        viewModel.savedItems.observe(viewLifecycleOwner) {
            Log.v("Metronome", "SaveDataFragment: submitting new data base list to adapter: size: ${it.savedItems.size}")
            savedItemsAdapter.submitList(ArrayList(it.savedItems))
            activity?.let{viewModel.saveDatabaseInAppPreferences(it)}
            updateNoSavedItemsMessage()
        }
        return view
    }

    var onItemClickedListener: SavedItemAdapter.OnItemClickedListener?
        set(value) {
            savedItemsAdapter.onItemClickedListener = value
        }
        get() {
            return savedItemsAdapter.onItemClickedListener
        }


    private fun updateNoSavedItemsMessage() {
        if(savedItemsAdapter.itemCount == 0){
            noSavedItemsMessage?.visibility = View.VISIBLE
        }
        else{
            noSavedItemsMessage?.visibility = View.GONE
        }
    }
}
