package de.moekadu.metronome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class NoteViewVolume(context : Context) : View(context) {

    private val paint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.GREEN
    }

    var color = Color.GREEN
        set(value) {
            paint.color = value
            field = value
        }

    private val path = Path()

    private val volumes = ArrayList<Float>(0)

    private val noteListChangedListener = object: NoteList.NoteListChangedListener {
        override fun onNoteAdded(note: NoteListItem, index: Int) {
            volumes.add(index, note.volume)
            invalidate()
        }

        override fun onNoteRemoved(note: NoteListItem, index: Int) {
            volumes.removeAt(index)
            invalidate()
        }

        override fun onNoteMoved(note: NoteListItem, fromIndex: Int, toIndex: Int) {
            noteList?.let { notes ->
                require(notes.size == volumes.size)
                for (i in notes.indices)
                    volumes[i] = notes[i].volume
                invalidate()
            }
        }

        override fun onVolumeChanged(note: NoteListItem, index: Int) {
            volumes[index] = note.volume
            invalidate()
        }

        override fun onNoteIdChanged(note: NoteListItem, index: Int) { }
        override fun onDurationChanged(note: NoteListItem, index: Int) { }
    }

    var noteList : NoteList? = null
        set(value) {
            field?.unregisterNoteListChangedListener(noteListChangedListener)
            field = value
            field?.registerNoteListChangedListener(noteListChangedListener)
            field?.let { notes ->
                volumes.clear()
                for (n in notes)
                    volumes.add(n.volume)
            }
            invalidate()
        }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if(volumes.size == 0)
            return
        val volumeMax = 0.19f * height
        val volumeMin = volumeMax + 0.62f * height
        val noteWidth = width.toFloat() / volumes.size.toFloat()
        path.rewind()
        path.moveTo(0f, volumeMin)
        for(i in volumes.indices) {
            val volume = volumes[i]
            val volumeNow = volume * volumeMax + (1.0f - volume) * volumeMin
            path.lineTo(i * noteWidth, volumeNow)
            path.lineTo((i + 1) * noteWidth, volumeNow)
        }
        path.lineTo(volumes.size * noteWidth, volumeMin)
        path.close()

        canvas?.drawPath(path, paint)
    }
}