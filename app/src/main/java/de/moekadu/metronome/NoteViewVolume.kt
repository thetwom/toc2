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
        override fun onNoteAdded(note: NoteListItem) {
            noteList?.let { notes ->
                volumes.add(notes.indexOf(note), note.volume)
                invalidate()
            }
        }

        override fun onNoteRemoved(note: NoteListItem) {
            noteList?.let { notes ->
                volumes.clear()
                for (n in notes)
                    volumes.add(n.volume)
                invalidate()
            }
        }

        override fun onNoteMoved(note: NoteListItem) {
            noteList?.let { notes ->
                require(notes.size == volumes.size)
                for (i in notes.indices)
                    volumes[i] = notes[i].volume
                invalidate()
            }
        }

        override fun onVolumeChanged(note: NoteListItem) {
            noteList?.let { notes ->
                require(notes.size == volumes.size)
                volumes[notes.indexOf(note)] = note.volume
                invalidate()
            }
        }

        override fun onNoteIdChanged(note: NoteListItem) { }
        override fun onDurationChanged(note: NoteListItem) { }
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


//    fun setNoteList(noteList : NoteList) {
//
//        var volumeChanged = false
//
//        if (noteList.size == volumes.size) {
//            for (i in noteList.indices) {
//                if (noteList[i].volume != volumes[i])
//                    volumeChanged = true
//                volumes[i] = noteList[i].volume
//            }
//        } else {
//            volumes.clear()
//            for (n in noteList)
//                volumes.add(n.volume)
//            volumeChanged = true
//        }
//        if (volumeChanged)
//            invalidate()
//    }

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