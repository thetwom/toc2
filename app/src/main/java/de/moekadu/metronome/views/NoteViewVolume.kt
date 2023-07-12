/*
 * Copyright 2020 Michael Moessner
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

package de.moekadu.metronome.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

import de.moekadu.metronome.metronomeproperties.NoteListItem

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

    fun setVolume(index: Int, volume: Float) {
        if (index in volumes.indices && volumes[index] != volume) {
            volumes[index] = volume
            invalidate()
        }
    }

    fun setVolumes(noteList: ArrayList<NoteListItem>) {
        var modified = false
        if (noteList.size != volumes.size) {
            volumes.clear()
            noteList.forEach { volumes.add(it.volume) }
            modified = true
        } else {
            noteList.forEachIndexed {index, noteListItem ->
                if (volumes[index] != noteListItem.volume) {
                    volumes[index] = noteListItem.volume
                    modified = true
                }
            }
        }
        if (modified)
            invalidate()
    }

    override fun onDraw(canvas: Canvas) {
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