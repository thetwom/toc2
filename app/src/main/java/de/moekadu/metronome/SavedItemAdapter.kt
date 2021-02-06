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
        return oldItem === newItem
    }

    override fun areContentsTheSame(oldItem: SavedItem, newItem: SavedItem): Boolean {
        return oldItem == newItem
    }
}

class SavedItemAdapter : ListAdapter<SavedItem, SavedItemAdapter.ViewHolder>(SavedItemDiffCallback()) {

    var onItemClickedListener : OnItemClickedListener? = null

    class ViewHolder(val view : View) : RecyclerView.ViewHolder(view)

    interface OnItemClickedListener {
        fun onItemClicked(item : SavedItem, position : Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.saved_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)//dataBase?.get(position) ?: return

        val titleView = holder.view.findViewById(R.id.saved_item_title) as TextView?
        val dateView = holder.view.findViewById(R.id.saved_item_date) as TextView?
        val speedView = holder.view.findViewById(R.id.saved_item_speed) as TextView?

        titleView?.text = item.title
        dateView?.text = item.date + "\n" + item.time
        speedView?.text = holder.view.context.getString(R.string.bpm, Utilities.getBpmString(item.bpm))
//        Log.v("Metronome", "SavedItemDatabase.onBindViewHolder: item.noteList = ${item.noteList}")
        val noteView = holder.view.findViewById(R.id.saved_item_sounds) as NoteView?
        val noteList = stringToNoteList(item.noteList)
        noteView?.setNoteList(noteList)

        holder.view.setOnClickListener {
            onItemClickedListener?.onItemClicked(getItem(holder.adapterPosition), holder.adapterPosition)
        }
        // Log.v("Metronome", "SavedItemDatabase:onBindViewHolder (position = " + position + ")")
    }
}
