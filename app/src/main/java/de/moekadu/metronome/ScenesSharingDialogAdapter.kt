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
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox

class SceneToBeShared(val scene: Scene, var isShared: Boolean)

class ScenesSharingDialogAdapter(scenes: List<Scene>?) : RecyclerView.Adapter<ScenesSharingDialogAdapter.ViewHolder>() {

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private var titleView = view.findViewById<TextView>(R.id.scene_title)
        private var checkBox = view.findViewById<CheckBox>(R.id.checkbox).apply {
            isClickable = false
        }

        fun setScene(sceneToBeShared: SceneToBeShared, position: Int) {
            val titleViewText = view.context.getString(R.string.decorate_with_hash_number, position+1, sceneToBeShared.scene.title)
            titleView?.text = titleViewText
            checkBox?.isChecked = sceneToBeShared.isShared
        }
        fun setCheckAll(resourceId: Int, isChecked: Boolean) {
            titleView?.setText(resourceId)
            checkBox?.isChecked = isChecked
            titleView?.typeface = Typeface.DEFAULT_BOLD
        }
    }

    private val scenesWhichCanBeShared = Array(scenes?.size ?: 0) {
            SceneToBeShared(scenes!![it], true)
    }

    init {
//        Log.v("Metronome", "ScenesSharingDialogAdapter.init: count = ${scenesWhichCanBeShared.size}")
        setHasStableIds(true)
    }

    fun getScenesToBeShared(): List<Scene> {
        return scenesWhichCanBeShared.filter { it.isShared }.map { it.scene }
    }

    fun getStateOfEachScene(): List<Boolean> {
        return scenesWhichCanBeShared.map { it.isShared }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setStateOfEachScene(states: List<Boolean>) {
        for (i in scenesWhichCanBeShared.indices) {
            if (i in states.indices)
                scenesWhichCanBeShared[i].isShared = states[i]
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
//        Log.v("Metronome", "ScenesSharingDialogAdapter.getItemCount: itemCount=${scenesWhichCanBeShared.size}")
        return scenesWhichCanBeShared.size + 1 // first is checkAll
    }
    override fun getItemId(position: Int): Long {
        return position.toLong() //getItem(position).scene.stableId
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.check_scene, parent, false)
        return ViewHolder(view).apply {
            //Log.v("Metronome", "ScenesSharingDialogAdapter.onCreateViewHolder")
            view.setOnClickListener {
                if (itemId == 0L) {
                    val allChecked = scenesWhichCanBeShared.all { it.isShared }
                    if (allChecked)
                        scenesWhichCanBeShared.forEach { it.isShared = false }
                    else
                        scenesWhichCanBeShared.forEach { it.isShared = true }
                    notifyDataSetChanged()
                } else {
                    val isShared = scenesWhichCanBeShared[itemId.toInt()-1].isShared
                    scenesWhichCanBeShared[itemId.toInt()-1].isShared = !isShared
                    notifyItemChanged(itemId.toInt())
                    notifyItemChanged(0)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == 0) {
            val allChecked = scenesWhichCanBeShared.all { it.isShared }
            holder.setCheckAll(R.string.select_all, allChecked)
        } else {
            holder.setScene(scenesWhichCanBeShared[position - 1], position - 1)
        }
    }
}
