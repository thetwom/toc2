/*
 * Copyright 2021 Michael Moessner
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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class TupletView(context: Context) : androidx.appcompat.widget.AppCompatImageView(context) {

    private val paint = Paint()
    private val rectLeft = Rect()
    private val rectRight = Rect()

    private val minimumSpacing = Utilities.dp2px(2f)

    private var startIndex = -1
    private var endIndex = -1

    var tupletNumber: Int = 1
        set(value) {
            if (field == value)
                return
            field = value
            when (value) {
                3 -> setImageResource(R.drawable.ic_triplet)
                5 -> setImageResource(R.drawable.ic_quintuplet)
            }
        }

    var tupletComplete: Boolean = true
        set (value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    init {
        scaleType = ScaleType.FIT_CENTER
        background = null
        paint.style = Paint.Style.STROKE
        paint.color = Color.BLUE
        paint.isAntiAlias = true
    }

    fun setStartAndEnd(from: Int, to: Int) {
//        Log.v("Metronome", "TupletView.setStartAndEnd: from=$from to=$to")
        if (startIndex == from && endIndex == to)
            return
        startIndex = from
        endIndex = to
        requestLayout()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val strokeWidth = 0.15f * measuredHeight
        paint.strokeWidth = strokeWidth
        paint.color = imageTintList?.defaultColor ?: Color.GREEN
        val centerY = 0.5f * measuredHeight
        val centerX = 0.5f * measuredWidth
        val space = 0.5f * measuredHeight

        // lines right of drawable
        canvas?.drawLine(
            centerX + space, centerY,
            measuredWidth - 0.5f * strokeWidth, centerY,
            paint
        )
        if (tupletComplete) {
            canvas?.drawLine(
                measuredWidth - 0.5f * strokeWidth, centerY - 0.5f * strokeWidth,
                measuredWidth - 0.5f * strokeWidth, 0.9f * measuredHeight,
                paint
            )
        }

        // lines left of drawable
        canvas?.drawLine(
            centerX - space, centerY,
            0.5f * strokeWidth, centerY,
            paint
        )
        if (tupletComplete) {
            canvas?.drawLine(
                0.5f * strokeWidth, centerY - 0.5f * strokeWidth,
                0.5f * strokeWidth, 0.9f * measuredHeight,
                paint
            )
        }
    }

    fun measureOnNoteView(noteViewWithNoPadding: Int,
                          noteViewHeightNoPadding: Int,
                          numNotes: Int) {
        NoteView.computeBoundingBox(startIndex, numNotes, noteViewWithNoPadding, noteViewHeightNoPadding, rectLeft)
        NoteView.computeBoundingBox(endIndex, numNotes, noteViewWithNoPadding, noteViewHeightNoPadding, rectRight)

        val left = max(
            rectLeft.left + minimumSpacing, rectLeft.centerX() - 0.1f * rectLeft.height()
        )
        val right = min(
            rectRight.right - minimumSpacing, rectRight.centerX() + 0.1f * rectRight.height()
        )
//        Log.v("Metronome", "TupletView.measureOnNoteView: minimumSpacing=$minimumSpacing, rectRight.right=${rectRight.right}, rectRight.centerX=${rectRight.centerX()}")
        val height = (0.15f * rectLeft.height()).roundToInt()
        val width = (right - left).roundToInt()
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    fun layoutOnNoteView(noteViewWithNoPadding: Int,
                         noteViewHeightNoPadding: Int,
                         numNotes: Int,
                         paddingLeft: Int,
                         paddingTop: Int) {
        NoteView.computeBoundingBox(startIndex, numNotes, noteViewWithNoPadding, noteViewHeightNoPadding, rectLeft)
        NoteView.computeBoundingBox(endIndex, numNotes, noteViewWithNoPadding, noteViewHeightNoPadding, rectRight)

        val left = max(
            rectLeft.left + minimumSpacing, rectLeft.centerX() - 0.1f * rectLeft.height()
        ).roundToInt()

        layout(left + paddingLeft,
            top + paddingTop,
            left + paddingLeft + measuredWidth,
            top + paddingTop + measuredHeight
        )
    }
}