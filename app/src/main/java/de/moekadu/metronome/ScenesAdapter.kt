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
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ScenesDiffCallback : DiffUtil.ItemCallback<Scene>() {
    override fun areItemsTheSame(oldScene: Scene, newScene: Scene): Boolean {
        return oldScene.stableId == newScene.stableId
    }

    override fun areContentsTheSame(oldScene: Scene, newScene: Scene): Boolean {
//        Log.v("Metronome", "SavedItemDiffCallback.areContentsTheSame: $oldScene, $newScene, ${oldScene == newScene}")
        return oldScene == newScene
    }
}

class ScenesAdapter : ListAdapter<Scene, ScenesAdapter.ViewHolder>(ScenesDiffCallback()) {

    var onSceneClickedListener: OnSceneClickedListener? = null
    private var activatedStableId = Scene.NO_STABLE_ID
    private var tickVisualizationType = TickVisualizerSync.VisualizationType.LeftRight

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        var isActivated = false
            set(value) {
                field = value
                if (value) {
                    //view.setBackgroundResource(R.drawable.scene_background_active)
                    selectedView?.visibility = View.VISIBLE
                    tickVisualizer?.visibility = View.VISIBLE
                }
                else {
                    //view.setBackgroundResource(R.drawable.scene_background)
                    selectedView?.visibility = View.INVISIBLE
                    tickVisualizer?.visibility = View.GONE
                    tickVisualizer?.stop()
                }
            }

        var titleView: TextView? = null
        //var dateView: TextView? = null
        var bpmView: TextView? = null
        var bpmDuration: AppCompatImageButton? = null
        var noteView: NoteView? = null
        var tickVisualizer: TickVisualizerSync? = null
        var selectedView: View? = null
    }

    fun interface OnSceneClickedListener {
        fun onSceneClicked(stableId: Long)
    }

    init {
        setHasStableIds(true)
    }

    fun setActiveStableId(stableId: Long, recyclerView: RecyclerView?) {
        activatedStableId = stableId
        if (recyclerView != null) {
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                (recyclerView.getChildViewHolder(child) as ScenesAdapter.ViewHolder).let { viewHolder ->
                    viewHolder.isActivated = (stableId == viewHolder.itemId)
                }
            }
        }
    }

    fun setTickVisualizationType(style: TickVisualizerSync.VisualizationType, recyclerView: RecyclerView?) {
        tickVisualizationType = style
        if (recyclerView != null) {
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                (recyclerView.getChildViewHolder(child) as ScenesAdapter.ViewHolder).let { viewHolder ->
                    viewHolder.tickVisualizer?.visualizationType = style
                }
            }
        }
    }

    fun animateNoteAndTickVisualizer(index: Int, noteDurationInMillis: Long?,
                                     noteStartUptimeMillis: Long, noteCount: Long, recyclerView: RecyclerView?) {
        if (recyclerView == null)
            return
        if (index < 0 && noteDurationInMillis != null)
            return

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            (recyclerView.getChildViewHolder(child) as ScenesAdapter.ViewHolder).let { viewHolder ->
                if (viewHolder.isActivated) {
                    if (index >= 0) {
                        // noteView is now nimated through the tickVisualizer since this has better time syncrhonization
                        //    viewHolder.noteView?.animateNote(index)
                        if (noteDurationInMillis != null)
                            viewHolder.tickVisualizer?.tick(index, noteStartUptimeMillis, noteCount)
                    }
                }
            }
        }
    }

    fun stopAnimation(recyclerView: RecyclerView?) {
        if (recyclerView == null)
            return
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            (recyclerView.getChildViewHolder(child) as ScenesAdapter.ViewHolder).let { viewHolder ->
                viewHolder.tickVisualizer?.stop()
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).stableId
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.scene, parent, false)
        return ViewHolder(view).apply {
            titleView = view.findViewById(R.id.scene_title)
            //dateView = view.findViewById(R.id.scene_date)
            bpmView = view.findViewById(R.id.scene_bpm)
            bpmDuration = view.findViewById(R.id.scene_bpm_duration)
            noteView = view.findViewById(R.id.scene_sounds)
            tickVisualizer = view.findViewById(R.id.scene_ticks_visualizer)
            tickVisualizer?.visualizationType = tickVisualizationType
            tickVisualizer?.noteStartedListener = TickVisualizerSync.NoteStartedListener {
                noteView?.animateNote(it)
            }
            selectedView = view.findViewById(R.id.scene_active)
            view.setOnClickListener {
                activatedStableId = itemId
                onSceneClickedListener?.onSceneClicked(itemId)
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val scene = getItem(position)

        holder.isActivated = (scene.stableId == activatedStableId)
        holder.titleView?.text = scene.title
        //holder.dateView?.text = scene.date + "\n" + scene.time
        holder.bpmView?.text = holder.view.context.getString(R.string.eqbpm, Utilities.getBpmString(scene.bpm.bpm))
        holder.bpmDuration?.setImageResource(
            when (scene.bpm.noteDuration) {
                NoteDuration.Quarter -> R.drawable.ic_note_duration_quarter
                NoteDuration.Eighth -> R.drawable.ic_note_duration_eighth
                NoteDuration.Sixteenth -> R.drawable.ic_note_duration_sixteenth
                else -> throw RuntimeException("Invalid bpm duration")
            }
        )
//        Log.v("Metronome", "SceneDatabase.onBindViewHolder: scene.noteList = ${scene.noteList}, scene.bpm = ${scene.bpm}")

        holder.noteView?.setNoteList(scene.noteList, 0L)
        holder.tickVisualizer?.setNoteList(scene.noteList)
        holder.tickVisualizer?.bpm = scene.bpm
        // Log.v("Metronome", "SceneDatabase:onBindViewHolder (position = " + position + ")")
    }
}
