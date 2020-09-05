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

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView

import java.util.ArrayList
import java.util.Locale
import kotlin.math.min

class SavedItemDatabase : RecyclerView.Adapter<SavedItemDatabase.ViewHolder>() {

    data class SavedItem(var title : String = "", var date : String = "",
                         var time : String = "", var bpm : Float = 0f, var noteList : String = "")

    private var dataBase : ArrayList<SavedItem>? = null

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
        val item = dataBase?.get(position) ?: return

        val titleView = holder.view.findViewById(R.id.saved_item_title) as TextView?
        val dateView = holder.view.findViewById(R.id.saved_item_date) as TextView?
        val speedView = holder.view.findViewById(R.id.saved_item_speed) as TextView?

        titleView?.text = item.title
        dateView?.text = item.date + "\n" + item.time
        speedView?.text = holder.view.context.getString(R.string.bpm, Utilities.getBpmString(item.bpm))

        val noteView = holder.view.findViewById(R.id.saved_item_sounds) as NoteView?
        val noteList = NoteList().apply { fromString(item.noteList) }//SoundProperties.parseMetaDataString(item.noteList)
        noteView?.noteList = noteList

        holder.view.setOnClickListener {
            // Log.v("Metronome", "SavedItemDatabase:onClickListener " + holder.getAdapterPosition())
            dataBase?.let {
                onItemClickedListener?.onItemClicked(it[holder.adapterPosition], holder.adapterPosition)
            }
        }
        // Log.v("Metronome", "SavedItemDatabase:onBindViewHolder (position = " + position + ")")
    }

    fun remove(position : Int, activity: FragmentActivity?) : SavedItem? {
        if(BuildConfig.DEBUG && position >= dataBase?.size ?: 0)
            throw RuntimeException("Invalid position")

        val item = dataBase?.get(position) ?: return null
        dataBase?.removeAt(position)
        notifyItemRemoved(position)
        activity?.let { saveData(it) }
        return item
    }

    fun moveItem(fromIndex: Int, toIndex: Int, activity: FragmentActivity?) {
        if (fromIndex == toIndex || fromIndex >= dataBase?.size ?: 0)
            return

        dataBase?.get(fromIndex)?.let { item ->
            dataBase?.removeAt(fromIndex)
            val tI = min(toIndex, dataBase?.size ?: 0)
            dataBase?.add(tI, item)
            notifyItemMoved(fromIndex, tI)
            activity?.let { saveData(it) }
        }
    }

    override fun getItemCount(): Int {
        return dataBase?.size ?: 0
    }

    fun addItem(activity : FragmentActivity, item : SavedItem) {
        if(dataBase == null)
            loadData(activity)
        dataBase?.let {
            it.add(item)
            notifyItemRangeInserted(it.size - 1, it.size)
            saveData(activity)
        }
        // Log.v("Metronome", "SavedItemDatabase:addItem: Number of items: " + dataBase.size)
    }

    fun addItem(activity : FragmentActivity, item : SavedItem, position : Int) {
        if(dataBase == null)
            loadData(activity)
        if(BuildConfig.DEBUG && position > (dataBase?.size ?: 0))
            throw RuntimeException("Invalid position")

        dataBase?.let {
            val p = min(position, it.size)
            it.add(p, item)
            notifyItemRangeInserted(p, 1)
            saveData(activity)
        }
//        // Log.v("Metronome", "SavedItemDatabase:addItem: Number of items: " + dataBase.size());
    }

    fun getSaveDataString(activity: FragmentActivity) : String {
        dataBase?.let {
            val stringBuilder = StringBuilder()

            stringBuilder.append(String.format(Locale.ENGLISH, "%50s", activity.getString(R.string.version)))

            for (si in it) {
                stringBuilder.append(String.format(Locale.ENGLISH, "%200s%10s%5s%12.5f%sEND", si.title, si.date, si.time, si.bpm, si.noteList))
            }
            return stringBuilder.toString()
        }
        return ""
    }

    fun saveData(activity : FragmentActivity) {
        val s = getSaveDataString(activity)
        if (s != "") {
            val preferences = activity.getPreferences(Context.MODE_PRIVATE)
            val editor = preferences.edit()
            editor.putString("savedDatabase", s)
            editor.apply()
        }
    }

    fun loadDataFromString(dataString: String, append: Boolean = false) {
        val newDataBase = if (dataBase == null || !append) {
            ArrayList()
        }
        else {
            dataBase
        }
        require(newDataBase != null)
        val oldSize = newDataBase.size

        // Log.v("Metronome", "SavedItemFragment:loadData: " + dataString);
        if(dataString == "")
            return
        if(dataString.length < 50)
            return
//        val version = dataString.substring(0, 50).trim()

        var pos = 50
        while(pos < dataString.length)
        {
            val si = SavedItem()
            if(pos+50 >= dataString.length)
                return

            if(pos+200 >= dataString.length)
                return
            si.title = dataString.substring(pos, pos+200).trim()
            pos += 200
            if(pos+10 >= dataString.length)
                return
            si.date = dataString.substring(pos, pos+10)
            pos += 10
            if(pos+5 >= dataString.length)
                return
            si.time = dataString.substring(pos, pos+5)
            pos += 5
            if(pos+6 >= dataString.length)
                return
            si.bpm = (dataString.substring(pos, pos+12).trim()).toFloat()
            pos += 12
            val noteListEnd = dataString.indexOf("END", pos)
            if(noteListEnd == -1)
                return
            si.noteList = dataString.substring(pos, noteListEnd)
            pos = noteListEnd+3

            newDataBase.add(si)
        }
        dataBase = newDataBase
        if (append) {
            notifyItemRangeInserted(oldSize, newDataBase.size - oldSize)
        }
        else {
            notifyDataSetChanged()
        }
    }

    fun loadData(activity : FragmentActivity) {
        if(dataBase != null)
            return

        val preferences = activity.getPreferences(Context.MODE_PRIVATE)
        val dataString = preferences.getString("savedDatabase", "") ?: ""
        loadDataFromString(dataString, false)
    }
}
