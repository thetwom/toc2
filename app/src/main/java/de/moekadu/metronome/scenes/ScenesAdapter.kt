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

package de.moekadu.metronome.scenes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.moekadu.metronome.R
import de.moekadu.metronome.misc.Utilities
import de.moekadu.metronome.metronomeproperties.NoteDuration
import de.moekadu.metronome.metronomeproperties.NoteListItem
import de.moekadu.metronome.views.NoteView
import de.moekadu.metronome.views.TickVisualizerSync

class ScenesDiffCallback : DiffUtil.ItemCallback<Scene>() {
    override fun areItemsTheSame(oldScene: Scene, newScene: Scene): Boolean {
        return oldScene.stableId == newScene.stableId
    }

    override fun areContentsTheSame(oldScene: Scene, newScene: Scene): Boolean {
//        Log.v("Metronome", "SavedItemDiffCallback.areContentsTheSame: $oldScene, $newScene, ${oldScene == newScene}")
        return oldScene.isEqualApartFromStableId(newScene)
    }
}

fun RecyclerView.forEachViewHolder(op: (ScenesAdapter.ViewHolder) -> Unit) {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        (getChildViewHolder(child) as ScenesAdapter.ViewHolder?)?.let { viewHolder ->
            op(viewHolder)
        }
    }
}
class ScenesAdapter : ListAdapter<Scene, ScenesAdapter.ViewHolder>(ScenesDiffCallback()) {

    var onSceneClickedListener: OnSceneClickedListener? = null
    private var activatedStableId = Scene.NO_STABLE_ID
    private var tickVisualizationType = TickVisualizerSync.VisualizationType.LeftRight
    private var visualDelayNanos = 0L
    private var useSimpleMode = false

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        var isActivated = false
            set(value) {
                field = value
                if (value) {
                    //view.setBackgroundResource(R.drawable.scene_background_active)
                    selectedView?.visibility = View.VISIBLE
                    //tickVisualizer?.visibility = View.VISIBLE
                }
                else {
                    //view.setBackgroundResource(R.drawable.scene_background)
                    selectedView?.visibility = View.INVISIBLE
                    //tickVisualizer?.visibility = View.GONE
                    tickVisualizer?.stop()
                }
            }

        var useSimpleMode = false
            set(value) {
                if (field == value)
                    return

                titleView?.visibility = if (value) View.GONE else View.VISIBLE
                bpmView?.visibility = if (value) View.GONE else View.VISIBLE
                bpmDuration?.visibility = if (value) View.GONE else View.VISIBLE
                noteView?.visibility = if (value) View.GONE else View.VISIBLE

                titleViewSimple?.visibility = if (value) View.VISIBLE else View.GONE
                bpmViewSimple?.visibility = if (value) View.VISIBLE else View.GONE
                field = value
            }
        var titleView: TextView? = null
            set(value) {
                field = value
                field?.visibility = if (useSimpleMode) View.GONE else View.VISIBLE
            }
        //var dateView: TextView? = null
        var bpmView: TextView? = null
            set(value) {
                field = value
                field?.visibility = if (useSimpleMode) View.GONE else View.VISIBLE
            }
        var bpmDuration: AppCompatImageButton? = null
            set(value) {
                field = value
                field?.visibility = if (useSimpleMode) View.GONE else View.VISIBLE
            }

        var noteView: NoteView? = null
            set(value) {
                field = value
                field?.visibility = if (useSimpleMode) View.GONE else View.VISIBLE
            }

        var tickVisualizer: TickVisualizerSync? = null
        var selectedView: View? = null

        var titleViewSimple: TextView? = null
            set(value) {
                field = value
                field?.visibility = if (useSimpleMode) View.VISIBLE else View.GONE
            }
        var bpmViewSimple: TextView? = null
            set(value) {
                field = value
                field?.visibility = if (useSimpleMode) View.VISIBLE else View.GONE
            }
    }

    fun interface OnSceneClickedListener {
        fun onSceneClicked(stableId: Long)
    }

    init {
        setHasStableIds(true)
    }

    fun setActiveStableId(stableId: Long, recyclerView: RecyclerView?) {
//        Log.v("Metronome", "ScenesAdapter.setActiveStableId: activatedStableId=$activatedStableId, stableId=$stableId")
        recyclerView?.forEachViewHolder { viewHolder ->
            viewHolder.isActivated = (stableId == viewHolder.itemId)
        }
        activatedStableId = stableId
    }

    fun setTickVisualizationType(style: TickVisualizerSync.VisualizationType, recyclerView: RecyclerView?) {
        tickVisualizationType = style
        recyclerView?.forEachViewHolder { viewHolder ->
            viewHolder.tickVisualizer?.visualizationType = style
        }
    }

    fun setVisualDelay(delayNanos: Long, recyclerView: RecyclerView?) {
        visualDelayNanos = delayNanos
        recyclerView?.forEachViewHolder { viewHolder ->
            viewHolder.tickVisualizer?.delayNanos = delayNanos
        }
    }

    fun setSimpleMode(useSimpleMode: Boolean, recyclerView: RecyclerView?) {
        this.useSimpleMode = useSimpleMode
        recyclerView?.forEachViewHolder { viewHolder ->
            viewHolder.useSimpleMode = useSimpleMode
        }
    }

    fun animateNoteAndTickVisualizer(
        noteListItem: NoteListItem, noteStartNanos: Long, noteCount: Long,
        recyclerView: RecyclerView?
    ) {
        if (recyclerView == null)
            return

        recyclerView.forEachViewHolder { viewHolder ->
            if (viewHolder.isActivated) {
                // noteView is now animated through the tickVisualizer since this has better time synchronization
                //    viewHolder.noteView?.animateNote(index)
                viewHolder.tickVisualizer?.tick(noteListItem, noteStartNanos, noteCount)
            }
        }
    }

    fun stopAnimation(recyclerView: RecyclerView?) {
        if (recyclerView == null)
            return
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            (recyclerView.getChildViewHolder(child) as ViewHolder).let { viewHolder ->
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
            tickVisualizer?.delayNanos = visualDelayNanos
            tickVisualizer?.noteStartedListener = TickVisualizerSync.NoteStartedListener { note, _, _, _ ->
                noteView?.animateNote(note.uid)
            }
            selectedView = view.findViewById(R.id.scene_active)
            view.setOnClickListener {
                activatedStableId = itemId
                onSceneClickedListener?.onSceneClicked(itemId)
            }
//            view.setOnLongClickListener {
//                Log.v("Metronome", "ScenesAdapter: onLongClick: $itemId")
//                true
//            }
            titleViewSimple = view.findViewById(R.id.scene_title_simple)
            bpmViewSimple = view.findViewById(R.id.scene_bpm_simple)
            useSimpleMode = this@ScenesAdapter.useSimpleMode
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val scene = getItem(position)

//        holder.isActivated = (scene.stableId == activatedStableId) // this is called by onViewAttachedToWindow
//        Log.v("Metronome", "ScenesAdapter.onBindViewHolder: position=$position, stableId=${scene.stableId}, activatedStableId=$activatedStableId, isActivated=${holder.isActivated}")
        //holder.titleView?.text = holder.view.context.getString(R.string.decorate_with_hash_number, position, scene.title) //"#${position + 1}: ${scene.title}"
        //holder.dateView?.text = scene.date + "\n" + scene.time
        holder.bpmView?.text = holder.view.context.getString(
            R.string.eqbpm,
            Utilities.getBpmString(scene.bpm.bpm)
        )
        holder.bpmViewSimple?.text = holder.view.context.getString(
            R.string.bpm,
            Utilities.getBpmString(scene.bpm.bpm)
        )
        val bpmDurationResource = when (scene.bpm.noteDuration) {
            NoteDuration.Quarter -> R.drawable.ic_note_duration_quarter
            NoteDuration.Eighth -> R.drawable.ic_note_duration_eighth
            NoteDuration.Sixteenth -> R.drawable.ic_note_duration_sixteenth
            else -> throw RuntimeException("Invalid bpm duration")
        }
        holder.bpmDuration?.setImageResource(bpmDurationResource)
//        Log.v("Metronome", "SceneDatabase.onBindViewHolder: scene.noteList = ${scene.noteList}, scene.bpm = ${scene.bpm}")

        holder.noteView?.setNoteList(scene.noteList, 0L)
//        holder.tickVisualizer?.setNoteList(scene.noteList)
        holder.tickVisualizer?.bpm = scene.bpm
        // Log.v("Metronome", "SceneDatabase:onBindViewHolder (position = " + position + ")")
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        holder.isActivated = (holder.itemId == activatedStableId)
        val position = holder.absoluteAdapterPosition
        val scene = getItem(position)
        val titleViewText = holder.view.context.getString(R.string.decorate_with_hash_number, position+1, scene.title) //"#${position + 1}: ${scene.title}"
        holder.titleView?.text = titleViewText
        holder.titleViewSimple?.text = titleViewText
        holder.useSimpleMode = useSimpleMode
        holder.tickVisualizer?.visualizationType = tickVisualizationType
        holder.tickVisualizer?.delayNanos = visualDelayNanos
        //holder.titleView?.text = "#${position + 1}: ${scene.title}"
//        Log.v("Metronome", "ScenesAdapter.onViewAttachedToWindow: activatedStableId=$activatedStableId, isActivated=${holder.isActivated}")
        super.onViewAttachedToWindow(holder)
    }
//    override fun onViewRecycled(holder: ViewHolder) {
//        Log.v("Metronome", "ScenesAdapter.onViewRecycled: activatedStableId=$activatedStableId, isActivated=${holder.isActivated}")
//        super.onViewRecycled(holder)
//    }
    fun setPositionNumbers(recyclerView: RecyclerView?) {
        recyclerView?.forEachViewHolder { holder ->
            val position = holder.bindingAdapterPosition
            if (position >= 0) {
                val scene = getItem(position)
                val titleViewText =  holder.view.context.getString(R.string.decorate_with_hash_number, position + 1, scene.title)
                holder.titleView?.text = titleViewText
                holder.titleViewSimple?.text = titleViewText
                //holder.titleView?.text = "#${position + 1}: ${scene.title}"
            }
        }
    }
}
