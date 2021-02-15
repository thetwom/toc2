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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class SavedItemDiffCallback : DiffUtil.ItemCallback<SavedItem>() {
    override fun areItemsTheSame(oldItem: SavedItem, newItem: SavedItem): Boolean {
        return oldItem.stableId == newItem.stableId
    }

    override fun areContentsTheSame(oldItem: SavedItem, newItem: SavedItem): Boolean {
        return oldItem == newItem
    }
}

class SavedItemAdapter : ListAdapter<SavedItem, SavedItemAdapter.ViewHolder>(SavedItemDiffCallback()) {

    var onItemClickedListener: OnItemClickedListener? = null
    private var activatedStableId = SavedItem.NO_STABLE_ID

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        var isActivated = false
            set(value) {
                field = value
                if (value) {
                    view.setBackgroundResource(R.drawable.choice_button_background_active)
//                    titleView?.setTextColor(Color.RED)
                }
                else {
                    view.setBackgroundResource(R.drawable.choice_button_background)
//                    titleView?.setTextColor(Color.GREEN)
                }
            }

        var titleView: TextView? = null
        var dateView: TextView? = null
        var speedView: TextView? = null
        var noteView: NoteView? = null
    }

    fun interface OnItemClickedListener {
        //fun onItemClicked(item: SavedItem, position: Int)
        fun onItemClicked(stableId: Long)
    }

    init {
        setHasStableIds(true)
    }

    fun setActiveStableId(stableId: Long, recyclerView: RecyclerView?) {
        activatedStableId = stableId
        if (recyclerView != null) {
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                (recyclerView.getChildViewHolder(child) as SavedItemAdapter.ViewHolder).let { viewHolder ->
                    viewHolder.isActivated = (stableId == viewHolder.itemId)
                }
            }
        }
    }

    fun animateNote(index: Int, recyclerView: RecyclerView?) {
        if (recyclerView == null)
            return

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            (recyclerView.getChildViewHolder(child) as SavedItemAdapter.ViewHolder).let { viewHolder ->
                if (viewHolder.isActivated)
                    viewHolder.noteView?.animateNote(index)
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).stableId
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.saved_item, parent, false)
        return ViewHolder(view).apply {
           titleView = view.findViewById(R.id.saved_item_title) as TextView?
           dateView = view.findViewById(R.id.saved_item_date) as TextView?
           speedView = view.findViewById(R.id.saved_item_speed) as TextView?
           noteView = view.findViewById(R.id.saved_item_sounds) as NoteView?
           view.setOnClickListener {
               activatedStableId = itemId
               onItemClickedListener?.onItemClicked(itemId)
           }
       }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)


        holder.isActivated = (item.stableId == activatedStableId)
        holder.titleView?.text = item.title
        holder.dateView?.text = item.date + "\n" + item.time
        holder.speedView?.text = holder.view.context.getString(R.string.bpm, Utilities.getBpmString(item.bpm))
//        Log.v("Metronome", "SavedItemDatabase.onBindViewHolder: item.noteList = ${item.noteList}")

        val noteList = stringToNoteList(item.noteList)
        holder.noteView?.setNoteList(noteList)
        // Log.v("Metronome", "SavedItemDatabase:onBindViewHolder (position = " + position + ")")
    }
}
