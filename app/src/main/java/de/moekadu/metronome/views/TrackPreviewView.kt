/*
 * Copyright 2024 Michael Moessner
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
import android.util.AttributeSet
import android.view.View
import de.moekadu.metronome.metronomeproperties.NoteListItem

/** Small preview of the notes of one track, used in the track list. It does not react to touches. */
class TrackPreviewView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var notes: List<NoteListItem> = emptyList()

    /** Color of the note icons. */
    var iconColor: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    fun setNotes(newNotes: List<NoteListItem>) {
        notes = newNotes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (notes.isEmpty())
            return

        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0)
            return

        val space = availableWidth / notes.size.toFloat()
        val iconHeight = availableHeight * 0.9f

        for (i in notes.indices) {
            val note = notes[i]
            val drawable = notePreviewDrawable(context, note.id, note.duration) ?: continue
            val scale = minOf(iconHeight / drawable.intrinsicHeight, space / drawable.intrinsicWidth)
            val centerX = paddingLeft + (0.5f + i) * space
            val left = centerX - 0.5f * drawable.intrinsicWidth * scale
            val top = paddingTop + 0.5f * (iconHeight - drawable.intrinsicHeight * scale)
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            drawable.setTint(iconColor)
            drawable.alpha = (120 + (125 * note.volume).toInt()).coerceIn(0, 255)
            val checkpoint = canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            drawable.draw(canvas)
            canvas.restoreToCount(checkpoint)
        }
    }
}
