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

package de.moekadu.metronome.views

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import de.moekadu.metronome.*
import de.moekadu.metronome.metronomeproperties.*
import de.moekadu.metronome.misc.Utilities
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class TickVisualizerSync(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : View(context, attrs, defStyleAttr) {

    /** Callback interface when a note visualization starts. */
    fun interface NoteStartedListener {
        /** Called when the note visualization starts.
         * @param note Note, which is started.
         * @param noteStartTimeNanos Time, when note is started.
         * @param noteEndTimeNanos End time, i.e. the time, when follow-up note will start.
         * @param noteCount Note counter of played notes since pressing play.
         */
        fun onNoteStarted(note: NoteListItem, noteStartTimeNanos: Long, noteEndTimeNanos: Long, noteCount: Long)
    }

    /** Available tick visualization types. */
    enum class VisualizationType {LeftRight, Fade, Bounce}

    /** Info about note which is in the que for being started.
     * @param noteListItem Note to be started
     * @param startTimeNanos Time as given by System.nanoTime(), when the note starts playing.
     * @param noteCount A counter of notes which were played since the last player start.
     */
    private data class QueuedNote(val noteListItem: NoteListItem, val startTimeNanos: Long, val noteCount: Long)

    /** Callback when a note ist started. */
    var noteStartedListener: NoteStartedListener? = null

    /** Paint of the visualization. */
    private val paint = Paint().apply {
        color = Color.RED
    }

    /** Current metronome speed. */
    var bpm = Bpm(120f, NoteDuration.Quarter)

    /** List of notes in the queue for being started. */
    private val queuedNotes = ArrayList<QueuedNote>()
    /** Start time of visualization of the currently played note. */
    private var currentTickStartTimeNanos = -1L
    /** End time of visualization of the currently played note. */
    private var currentTickEndTimeNanos = -1L
    /** Note counter since player start of the currenlty played note. */
    private var tickCount = 0L
    /** Currently used tick visualization strategy. */
    var visualizationType = VisualizationType.Bounce

    /** Compute current position of the played visualization. (0 -- 1) */
    val fraction: Float
        get() {
            var result = (
                    (System.nanoTime() - currentTickStartTimeNanos).toFloat()
                            / (currentTickEndTimeNanos - currentTickStartTimeNanos)
                    )
            result = max(0f, result)
            result = min(1f, result)
            return result
        }

    /** Time animator which is responsible for the regular visualization update. */
    private val animator = TimeAnimator().apply {
        setTimeListener { _, _ ,_ ->
            val nanoTime = System.nanoTime()
            var index = -1
            for (q in queuedNotes) {
                if (q.startTimeNanos > nanoTime)
                    break
                else
                    ++index
            }
            val note = if (index in queuedNotes.indices) queuedNotes[index] else null

            if (note != null) {
                currentTickStartTimeNanos = note.startTimeNanos
                currentTickEndTimeNanos = currentTickStartTimeNanos + note.noteListItem.duration.durationInNanos(bpm.bpmQuarter)
                tickCount = note.noteCount
                noteStartedListener?.onNoteStarted(note.noteListItem, currentTickStartTimeNanos, currentTickEndTimeNanos, tickCount)
                queuedNotes.subList(0, index + 1).clear()
            }
            invalidate()
        }
    }

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs,
        R.attr.tickVisualizerSyncStyle
    )

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs,
                R.styleable.TickVisualizerSync, defStyleAttr,
                R.style.Widget_AppTheme_TickVisualizerStyle
            )
            paint.color = ta.getColor(R.styleable.TickVisualizerSync_color, paint.color)
            ta.recycle()
        }
    }

    /** Register new note which will be played.
     * This will also start the visualization animator, if it is not started.
     * @param noteListItem Note to be played.
     * @param startTimeNanos Time as given by System.nanoTime(), when the note starts playing.
     * @param noteCount Total counter of notes since pressing play.
     */
    fun tick(noteListItem: NoteListItem, startTimeNanos: Long, noteCount: Long) {
        queuedNotes.add(QueuedNote(noteListItem, startTimeNanos, noteCount))
        if (!animator.isRunning)
            animator.start()
    }

    /** The player stopped, so stop playing animations. */
    fun stop() {
        queuedNotes.clear()
        animator.end()
        invalidate()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (animator.isRunning) {
            //val uptimeMillis = SystemClock.uptimeMillis() // use System.nanoTime() if you enable this again
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
                    val fadeDurationNanos = min(currentTickEndTimeNanos - currentTickStartTimeNanos, 150 * 1000_000L)
                    val positionSinceStartNanos = System.nanoTime() - currentTickStartTimeNanos
                    val reducedFraction = (positionSinceStartNanos.coerceIn(0L, fadeDurationNanos).toFloat()
                            / fadeDurationNanos)
                    paint.alpha = (255 * (1 - reducedFraction)).toInt()
                    canvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                }
                VisualizationType.Bounce -> {
                    val durationRef = Utilities.bpm2nanos(200f)
                    var amp = Utilities.dp2px(20f) * max(currentTickEndTimeNanos - currentTickStartTimeNanos, durationRef) / durationRef
//                    Log.v("Metronome", "TickVisualizerSync.onDraw: amp=$amp, delta=${currentTickEndTimeNanos - currentTickStartTimeNanos}, duration250Nanos=$duration250Nanos")
                    val ampMax = 0.5f * width * (1 - 0.2f)
                    amp = min(amp, ampMax)
                    val center = 0.5f * width
                    val blockWidth = 0.5f * width - amp
                    val shift = amp  * sin(PI.toFloat() * fraction)
                    //Log.v("Metronome", "TickVisualizerSync.onDraw: amp=$amp, ampMax=$ampMax, blockWidth = $blockWidth, shift=$shift")
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
}