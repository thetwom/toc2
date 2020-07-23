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
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import de.moekadu.metronome.SavedItemDatabase.SavedItem
import kotlin.math.roundToInt


class SaveDataFragment : Fragment() {

    private var savedItems: RecyclerView? = null
    private var savedItemsManager: RecyclerView.LayoutManager? = null
    private val savedItemsAdapter = SavedItemDatabase()
    private val backgroundArea = Rect()

    private var lastRemovedItemIndex = -1
    private var lastRemovedItem: SavedItem? = null

    private var noSavedItemsMessage: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        savedItemsManager = LinearLayoutManager(activity)
        activity?.let { savedItemsAdapter.loadData(it) }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
//        super.onPrepareOptionsMenu(menu);
        val settingsItem = menu.findItem(R.id.action_properties)
        settingsItem?.isVisible = false

        val loadDataItem = menu.findItem(R.id.action_load)
        loadDataItem?.isVisible = false

        val saveDataItem = menu.findItem(R.id.action_save)
        saveDataItem.isVisible = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_save_data, container, false)

//        deleteTextSize = Utilities.sp_to_px(18);

        noSavedItemsMessage = view.findViewById(R.id.noSavedItemsMessage)
        updateNoSavedItemsMessage()

        savedItems = view.findViewById(R.id.savedItems)
        savedItems?.setHasFixedSize(true)
        savedItems?.layoutManager = savedItemsManager
        savedItems?.adapter = savedItemsAdapter

        val savedItemAttributes = view.findViewById(R.id.saved_item_attributes) as SavedItemAttributes

        val simpleTouchHelper = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            val background = ColorDrawable(savedItemAttributes.deleteColor)
            val deleteIcon = activity?.let { ContextCompat.getDrawable(it, R.drawable.ic_delete_on_error) }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Log.v("Metronome", "SaveDataFragment:onSwiped " + viewHolder.getAdapterPosition())

                lastRemovedItemIndex = viewHolder.adapterPosition
                lastRemovedItem = savedItemsAdapter.remove(lastRemovedItemIndex)
                updateNoSavedItemsMessage()

                (getView() as CoordinatorLayout?)?.let { coLayout ->
                    activity?.let { act ->
                        lastRemovedItem?.let { removedItem ->
                            Snackbar.make(coLayout, getString(R.string.item_deleted), Snackbar.LENGTH_LONG)
                                    .setAction(R.string.undo) {
                                        if (lastRemovedItem != null) {
                                            savedItemsAdapter.addItem(act, removedItem, lastRemovedItemIndex)
                                            lastRemovedItem = null
                                            lastRemovedItemIndex = -1
                                            updateNoSavedItemsMessage()
                                        }
                                    }.show()
                        }
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

                backgroundArea.set(itemView.left, itemView.top, itemView.right, itemView.bottom)
                background.bounds = backgroundArea
                background.draw(c)

                val iconHeight = (0.7f * (itemView.height - itemView.paddingTop - itemView.paddingBottom)).roundToInt()
                deleteIcon?.setBounds(itemView.right -iconHeight-itemView.paddingRight,
                        (itemView.top +itemView.bottom - iconHeight)/2,
                        itemView.right -itemView.paddingRight,
                        (itemView.top +itemView.bottom +iconHeight)/2)
                deleteIcon?.draw(c)
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val touchHelper = ItemTouchHelper(simpleTouchHelper)
        touchHelper.attachToRecyclerView(savedItems)

        return view
    }

    override fun onDetach() {
        activity?.let { savedItemsAdapter.saveData(it) }
        super.onDetach()
    }

    fun saveItem(activity : FragmentActivity, item : SavedItem)
    {
        savedItemsAdapter.addItem(activity, item)
        updateNoSavedItemsMessage()
    }


    var onItemClickedListener: SavedItemDatabase.OnItemClickedListener?
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
