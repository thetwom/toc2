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

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class TickVisualizerSync(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : View(context, attrs, defStyleAttr) {

    fun interface NoteStartedListener {
        fun onNoteStarted(uid: UId)
    }

    var noteStartedListener: NoteStartedListener? = null

    private data class UidDuration(var uid: UId, var duration: NoteDuration)

    private val paint = Paint().apply {
        color = Color.RED
    }

    var bpm = Bpm(120f, NoteDuration.Quarter)
    private val noteDurations = ArrayList<UidDuration>()

    private var currentNoteIndex = 0
    private var currentNoteUid: UId? = null
    private var currentTickStartTime = -1L
    private var currentTickEndTime = -1L
    private var tickCount = 0L

    var visualizationType = VisualizationType.Bounce

    val fraction: Float
        get() {
            var result = (
                    (SystemClock.uptimeMillis() - currentTickStartTime).toFloat()
                            / (currentTickEndTime - currentTickStartTime)
                    )
            result = max(0f, result)
            result = min(1f, result)
            return result
        }

    private val animator = TimeAnimator().apply {
        setTimeListener { _, _ ,_ ->
            val time = SystemClock.uptimeMillis()
//            Log.v("Metronome", "TickVisualizerSync.timeAnimationListener: time=$time, currentTickEndTime=$currentTickEndTime")
            if (time > currentTickEndTime) {
                currentTickStartTime = currentTickEndTime
                currentNoteIndex = if (currentNoteIndex + 1 >= noteDurations.size) 0 else currentNoteIndex + 1
                currentNoteUid = noteDurations[currentNoteIndex].uid
                currentNoteUid?.let {uid -> noteStartedListener?.onNoteStarted(uid)}
                currentTickEndTime = currentTickStartTime + noteDurations[currentNoteIndex].duration.durationInMillis(bpm.bpmQuarter)
                ++tickCount
            }
            invalidate()
        }
    }

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, R.attr.tickVisualizerSyncStyle)

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.TickVisualizerSync, defStyleAttr, R.style.Widget_AppTheme_TickVisualizerStyle)
            paint.color = ta.getColor(R.styleable.TickVisualizerSync_color, paint.color)
            ta.recycle()
        }
    }

    fun tick(index: Int, startTime: Long, noteCount: Long) {
        if (!animator.isRunning) {
            tickCount = -1L
            currentNoteIndex = 0
        }

        if (index in noteDurations.indices) {
            val endTime = startTime + noteDurations[index].duration.durationInMillis(bpm.bpmQuarter)
            // if "play" was called with too much delay, we rely on the automatic ticking of this class
            if (SystemClock.uptimeMillis() <= endTime) {
                currentTickStartTime = startTime
                currentTickEndTime = endTime
                if (tickCount != noteCount)
                    noteStartedListener?.onNoteStarted(noteDurations[index].uid)
                tickCount = noteCount
//                Log.v("Metronome", "TickVisualizer.tick: index=$index, currentNoteIndex=$currentNoteIndex")
                currentNoteIndex = index
                currentNoteUid = noteDurations[index].uid
            }
        }

        if (!animator.isRunning)
            animator.start()
    }

    fun tick(uid: UId, startTime: Long, noteCount: Long) {
        val index = noteDurations.indexOfFirst { it.uid == uid }
        tick(index, startTime, noteCount)
    }

    fun stop() {
        animator.end()
        invalidate()
    }

    fun setNoteList(noteList: ArrayList<NoteListItem>) {
        // delete excess entries
        if (noteDurations.size > noteList.size)
            noteDurations.subList(noteList.size, noteDurations.size).clear()
        // set values in available entries
        for (i in noteDurations.indices) {
            noteDurations[i].uid = noteList[i].uid
            noteDurations[i].duration = noteList[i].duration
        }
        // add missing entries
        for (i in noteDurations.size until noteList.size)
            noteDurations.add(UidDuration(noteList[i].uid, noteList[i].duration))
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (animator.isRunning) {
            //val uptimeMillis = SystemClock.uptimeMillis()
            //val fraction = (uptimeMillis - currentTickStartTime).toFloat() / ( currentTickEndTime - currentTickStartTime)
            //paint.alpha = (255 * (1 - fraction)).toInt()
            //Log.v("Metronome", "TickVisualizerSync.onDraw: fraction=$fraction")

            when (visualizationType) {
                VisualizationType.LeftRight -> {
                    paint.alpha = 255
                    if (tickCount % 2L == 0L)
                        canvas?.drawRect(0f, 0f, 0.5f * width, height.toFloat(), paint)
                    else
                        canvas?.drawRect(0.5f * width, 0f, width.toFloat(), height.toFloat(), paint)
                }
                VisualizationType.Fade -> {
                    paint.alpha = (255 * (1 - fraction)).toInt()
                    canvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                }
                VisualizationType.Bounce -> {
                    val duration250 = Utilities.bpm2millis(200f)
                    var amp = Utilities.dp2px(20f) * max(currentTickEndTime - currentTickStartTime, duration250) / duration250
                    val ampMax = 0.5f * width * (1 - 0.2f)
                    amp = min(amp, ampMax)
                    val center = 0.5f * width
                    val blockWidth = 0.5f * width - amp
                    val shift = amp  * sin(PI.toFloat() * fraction)

                    if (tickCount % 2L == 0L) {
                        paint.alpha = 120
                        canvas?.drawRect(center - blockWidth, 0f,
                            center, height.toFloat(), paint)
                        paint.alpha = 255
                        canvas?.drawRect(center + shift, 0f,
                            center + shift + blockWidth, height.toFloat(), paint)
                    } else {
                        paint.alpha = 255
                        canvas?.drawRect(center - shift - blockWidth, 0f,
                            center - shift, height.toFloat(), paint)
                        paint.alpha = 120
                        canvas?.drawRect(center, 0f,
                            center + blockWidth, height.toFloat(), paint)
                    }
                }
            }
        }
    }

    enum class VisualizationType {LeftRight, Fade, Bounce}
}