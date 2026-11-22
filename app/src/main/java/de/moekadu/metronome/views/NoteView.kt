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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import de.moekadu.metronome.*
import de.moekadu.metronome.metronomeproperties.*
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt

open class NoteView(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    companion object {
        const val NOTE_IMAGE_HEIGHT_SCALING = 0.85f

        /// Compute bounding box for a note with a given index.
        /**
         * @param index Index of note in note list.
         * @param numNotes Total number of notes in note list.
         * @param noteViewWidth Width of NoteView (padding must be already subtracted).
         * @param noteViewHeight Height of NoteView (padding must be already subtracted).
         * @param result Bounding box of note in question stored here (relative to the
         *    top of the rectangle defined by noteViewWidth and noteViewHeight).
         */
        fun computeBoundingBox(index: Int, numNotes: Int, noteViewWidth: Int, noteViewHeight: Int, result: Rect) {
            val noteHorizontalSpace = noteViewWidth.toFloat() / numNotes.toFloat()
            val noteCenter = (0.5f + index) * noteHorizontalSpace

            result.left = (noteCenter - 0.5f * noteHorizontalSpace).toInt()
            result.right = result.left + noteHorizontalSpace.toInt()
            result.top = 0
            result.bottom = noteViewHeight
        }
    }

    private val lineView = ImageView(context).apply {
        setPadding(0, 0, 0, 0)
        setBackgroundResource(R.drawable.ic_notelines)
        backgroundTintList = ContextCompat.getColorStateList(context, R.color.note_view_line)
    }

    val size get() = notes.size

    /** Notes of all tracks, only used to draw and to detect the track under a touch position. */
    private val trackSnapshots = ArrayList<ArrayList<NoteListItem>>()

    /** Index of the track which is currently edited (its notes are the layouted child views). */
    var activeTrack = 0
        private set

    /** Vertical scroll offset in pixels, for showing more tracks than fit into the view. */
    private var scrollOffset = 0f

    /** Listener for track gestures (tap to select, long press to open the track menu). */
    interface OnTrackGestureListener {
        fun onTrackClicked(trackIndex: Int)
        fun onTrackLongClicked(trackIndex: Int)
    }
    var onTrackGestureListener: OnTrackGestureListener? = null

    private var trackTouchDownY = 0f
    private var trackTouchLastY = 0f
    private var trackTouchDownRow = -1
    private var trackScrolling = false
    private var trackLongPressTriggered = false
    private val trackTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val trackLongPressRunnable = Runnable {
        if (!trackScrolling && trackTouchDownRow >= 0) {
            trackLongPressTriggered = true
            onTrackGestureListener?.onTrackLongClicked(trackTouchDownRow)
        }
    }

    /** Number of track rows which are visible at the same time. */
    private fun numVisibleRows() = if (trackSnapshots.size <= 1) 1 else 2

    /** Height of one track row in pixels (padding already subtracted). */
    private fun rowHeight(): Int {
        val totalHeight = max(0, height - paddingTop - paddingBottom)
        return max(1, totalHeight / numVisibleRows())
    }

    /** Top position of a track row inside this view (including padding and scroll offset). */
    private fun rowTop(row: Int): Int = paddingTop + row * rowHeight() - scrollOffset.roundToInt()

    /** Scroll the tracks vertically so that all tracks can be reached. */
    fun scrollTracks(delta: Float) {
        if (trackSnapshots.size <= 1)
            return
        val totalHeight = max(0, height - paddingTop - paddingBottom)
        val contentHeight = rowHeight() * trackSnapshots.size
        val maxOffset = max(0, contentHeight - totalHeight).toFloat()
        val newOffset = (scrollOffset + delta).coerceIn(0f, maxOffset)
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset
            requestLayout()
            invalidate()
        }
    }

    /** Leave vertical scrolling in a sane state after the number of tracks changed. */
    private fun clampScrollOffset() {
        val totalHeight = max(0, height - paddingTop - paddingBottom)
        val contentHeight = rowHeight() * trackSnapshots.size
        val maxOffset = max(0, contentHeight - totalHeight).toFloat()
        scrollOffset = scrollOffset.coerceIn(0f, maxOffset)
    }

    /** Set all tracks. The notes of the active track are the child views which can be edited. */
    fun setTracks(trackList: ArrayList<Track>, activeIndex: Int, animationDuration: Long) {
        trackSnapshots.clear()
        for (track in trackList)
            trackSnapshots.add(track.notes)
        activeTrack = activeIndex.coerceIn(0, max(0, trackList.size - 1))
        val activeNotes = trackList.getOrNull(activeTrack)?.notes ?: ArrayList()
        setNoteList(activeNotes, animationDuration)
        clampScrollOffset()
        requestLayout()
        invalidate()
    }

    var volumeColor: Int = Color.GREEN
        set(value) {
            volumeView.color = value
            field = value
        }

    var noteColor: ColorStateList? = null
        set(value){
            if (value != null) {
                for (i in notes.indices) {
                    if (!notes[i].highlight) {
                        notes[i].noteImage.imageTintList = value
                        if (i < numbering.size)
                            numbering[i].setTextColor(value)
                    }
                    if (i in advanceMarkers.indices) {
                        advanceMarkers[i].setColorFilter(value.defaultColor)
                    }
                }
                for (t in tuplets) {
                    t.imageTintList = value
                }
            }
            field = value
        }

    var noteHighlightColor: ColorStateList? = null
        set(value){
            if (value != null) {
                for (i in notes.indices) {
                    if (notes[i].highlight) {
                        notes[i].noteImage.imageTintList = value
                        if (i <= numbering.size)
                            numbering[i].setTextColor(value)
                    }
                }
            }
            field = value
        }

    private val volumeView = NoteViewVolume(context)

    private val tuplets = ArrayList<TupletView>()
    var showTuplets = true
        set(value) {
            if (field == value)
                return
            field = value
            tuplets.forEach { it.visibility = if (value) VISIBLE else GONE }
            requestLayout()
        }

    inner class Note (noteListItem: NoteListItem) {

        private val noteListItem = noteListItem.clone()
        val uid get() = noteListItem.uid

        var highlight : Boolean = false
            set(value) {
                if (value)
                    noteHighlightColor?.let {noteImage.imageTintList = it}
                else
                    noteColor?.let{noteImage.imageTintList = it}
                //noteImage.isSelected = value
                field = value
            }

        private var drawableID = getNoteDrawableResourceID(noteListItem.id, noteListItem.duration)
            set(value) {
                if (field != value)
                    noteImage.setImageResource(value)
                field = value
            }

        val noteImage = ImageView(context).apply {
            setImageResource(drawableID)
            setPadding(0, 0, 0, 0)
            background = null
            //setBackgroundColor(Color.RED)
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (noteColor != null && !highlight)
                imageTintList = noteColor
            if (noteHighlightColor != null && highlight)
                imageTintList = noteHighlightColor
            // imageTintList = ContextCompat.getColorStateList(context, R.color.note_view_note)
        }

        val drawable: Drawable
            get() = noteImage.drawable

        init {
            highlight = false
            drawableID = getNoteDrawableResourceID(noteListItem.id, noteListItem.duration)
        }

        fun set(newNoteListItem: NoteListItem) {
            require(noteListItem.uid == newNoteListItem.uid)
            if (newNoteListItem.id != noteListItem.id || newNoteListItem.duration != noteListItem.duration)
                drawableID = getNoteDrawableResourceID(newNoteListItem.id, newNoteListItem.duration)
            noteListItem.set(newNoteListItem)
        }

        /// Recalculate the icon, e.g. after the sound set changed.
        fun refreshDrawable() {
            drawableID = getNoteDrawableResourceID(noteListItem.id, noteListItem.duration)
        }

        var noteId: Int
            set(value) {
                if (value != noteListItem.id) {
                    drawableID = getNoteDrawableResourceID(value, noteListItem.duration)
                    noteListItem.id = value
                }
            }
            get() {
                return noteListItem.id
            }

        var volume: Float
            set(value) {
                noteListItem.volume = value
            }
            get() {
                return noteListItem.volume
            }

        var duration: NoteDuration
            set(value) {
                if (value != noteListItem.duration) {
                    drawableID = getNoteDrawableResourceID(noteListItem.id, value)
                    noteListItem.duration = value
                }
            }
            get() {
                return noteListItem.duration
            }
    }

    private val notes = ArrayList<Note>()

    private val numbering = ArrayList<AppCompatTextView>()

    private val advanceMarkers = ArrayList<ImageView>()
    private var activeAdvanceMarkerIndex = -1

    private fun computeLargestAspectRatio() : Float {
        var largestAspectRatio = 0.0f
        for(duration in NoteDuration.entries) {
            for (note in 0 until getNumAvailableNotes()) {
                AppCompatResources.getDrawable(context, getNoteDrawableResourceID(note, duration))
                    ?.let { drawable ->
                        largestAspectRatio = max(
                            largestAspectRatio,
                            drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
                        )
                    }
            }
        }
        require(largestAspectRatio > 0.0f)
        return largestAspectRatio
    }
    private val largestAspectRatio = computeLargestAspectRatio()

    private val transition = AutoTransition().apply { duration = 150L }

    interface OnNoteClickListener {
        fun onDown(event: MotionEvent?, uid: UId?, noteIndex: Int) : Boolean
        fun onUp(event: MotionEvent?, uid: UId?, noteIndex: Int) : Boolean
        fun onMove(event: MotionEvent?, uid: UId?, noteIndex: Int) : Boolean
    }

    var onNoteClickListener : OnNoteClickListener? = null

    var showNumbers = false
        set(value) {
            field = value
            makeSureWeHaveCorrectNumberOfNumberingViews()
        }

    var numberOffset = 0
        @SuppressLint("SetTextI18n")
        set(value) {
            if (value != field) {
                field = value
                for (i in numbering.indices)
                    numbering[i].text = "${i + 1 + value}"
            }
        }

    constructor(context: Context, attrs : AttributeSet? = null)
            : this(context, attrs, R.attr.noteViewStyle)

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(
                attrs,
                R.styleable.NoteView,
                defStyleAttr,
                R.style.Widget_AppTheme_NoteViewStyle
            )

            volumeColor = ta.getColor(R.styleable.NoteView_volumeColor, volumeColor)
            noteColor = ta.getColorStateList(R.styleable.NoteView_noteColor)
            noteHighlightColor = ta.getColorStateList(R.styleable.NoteView_noteHighlightColor)
            showNumbers = ta.getBoolean(R.styleable.NoteView_showNumbers, showNumbers)
            ta.recycle()
        }

        addView(volumeView)
        addView(lineView)
    }

    final override fun addView(child: View?) {
        super.addView(child)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        val heightScaling = if (showTuplets) NOTE_IMAGE_HEIGHT_SCALING else 1.0f

        val totalWidth = measuredWidth - paddingLeft - paddingRight
        val totalHeight = measuredHeight - paddingTop - paddingBottom
        val singleRowHeight = max(1, totalHeight / (if (trackSnapshots.size <= 1) 1 else 2))
        val totalHeightExcludingTuplet = (heightScaling * singleRowHeight).roundToInt()

        val widthSpec = MeasureSpec.makeMeasureSpec(totalWidth, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(totalHeightExcludingTuplet, MeasureSpec.EXACTLY)
        volumeView.measure(widthSpec, heightSpec)
        lineView.measure(widthSpec, heightSpec)

        val noteImageWidth = (singleRowHeight * largestAspectRatio).toInt()
        val noteWidthSpec = MeasureSpec.makeMeasureSpec(noteImageWidth, MeasureSpec.EXACTLY)

        for(n in notes)
            n.noteImage.measure(noteWidthSpec, heightSpec)

        val textHeightSpec = MeasureSpec.makeMeasureSpec((0.2f * totalHeightExcludingTuplet).toInt(), MeasureSpec.EXACTLY)
        numbering.forEach {
            it.measure(textHeightSpec, textHeightSpec)
        }

        tuplets.forEach {
            it.measureOnNoteView(totalWidth, singleRowHeight, notes.size)
        }

        val advanceMarkerHeight = (0.1f * totalHeightExcludingTuplet).roundToInt()
        val advanceMarkerAspect = 2f
        val advanceMarkerWidth = (advanceMarkerAspect * advanceMarkerHeight).roundToInt()
        val advanceMarkerWidthSpec = MeasureSpec.makeMeasureSpec(advanceMarkerWidth, MeasureSpec.EXACTLY)
        val advanceMarkerHeightSpec = MeasureSpec.makeMeasureSpec(advanceMarkerWidth, MeasureSpec.EXACTLY)
        advanceMarkers.forEach {
            it.measure(advanceMarkerWidthSpec, advanceMarkerHeightSpec)
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
//        Log.v("Metronome", "NoteView.onLayout: changed: $changed")
        val totalWidth = r - l - paddingLeft - paddingRight
        val totalHeight = b - t - paddingTop - paddingBottom
        val singleRowHeight = max(1, totalHeight / (if (trackSnapshots.size <= 1) 1 else 2))
        val rowTopPosition = paddingTop + activeTrack * singleRowHeight - scrollOffset.roundToInt()
        val rowBottom = rowTopPosition + singleRowHeight
        val heightScaling = if (showTuplets) NOTE_IMAGE_HEIGHT_SCALING else 1.0f
        val totalHeightExcludingTuplet = (heightScaling * singleRowHeight).roundToInt()
        val excludingTupletTop = rowTopPosition + singleRowHeight - totalHeightExcludingTuplet

        if (changed) {
            volumeView.layout(
                paddingLeft,
                excludingTupletTop,
                paddingLeft + volumeView.measuredWidth,
                excludingTupletTop + volumeView.measuredHeight
            )
            lineView.layout(
                paddingLeft,
                excludingTupletTop,
                paddingLeft + lineView.measuredWidth,
                excludingTupletTop + lineView.measuredHeight
            )
        }

        val noteHorizontalSpace = totalWidth / notes.size.toFloat()

        for(i in notes.indices) {
            val noteView = notes[i].noteImage
            val noteCenter = paddingLeft + (0.5f + i) * noteHorizontalSpace
            val noteImageWidth = noteView.measuredWidth
            val noteImageHeight = noteView.measuredHeight

            val noteLeft = (noteCenter - 0.5f * noteImageWidth).toInt()
            val noteTop = excludingTupletTop
            val noteRight = noteLeft + noteImageWidth
            val noteBottom = excludingTupletTop + noteImageHeight
            noteView.layout(noteLeft, noteTop, noteRight, noteBottom)

            if (i < numbering.size) {
                val textView = numbering[i]
                val textViewLeft = (noteCenter - 0.5f * textView.measuredWidth).roundToInt()
                val textViewRight = textViewLeft + textView.measuredWidth
                val textViewBottom = rowBottom
                val textViewTop = textViewBottom - textView.measuredHeight
//                Log.v("Metronome", "NoteView.onLayout, $textViewLeft, $textViewTop, $textViewRight, $textViewBottom")
                textView.layout(textViewLeft, textViewTop, textViewRight, textViewBottom)
            }

            if (i < advanceMarkers.size) {
                val marker = advanceMarkers[i]
                val markerLeft = (noteCenter - 0.5f * marker.measuredWidth).roundToInt()
                val markerRight = markerLeft + marker.measuredWidth
                val markerBottom = rowBottom
                val markerTop = markerBottom - marker.measuredHeight
                marker.layout(markerLeft, markerTop, markerRight, markerBottom)
            }
        }

        for (tuplet in tuplets)
            tuplet.layoutOnNoteView(totalWidth, singleRowHeight, notes.size, paddingLeft, rowTopPosition)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
//        return (onNoteClickListener != null && ev != null)
//        Log.v("Metronome", "NoteView.onInterceptTouchEvent")
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        drawInactiveTracks(canvas)
        super.dispatchDraw(canvas)
    }

    /** Draw the notes of all tracks which are not edited (the edited track is drawn by child views). */
    private fun drawInactiveTracks(canvas: Canvas) {
        if (trackSnapshots.size <= 1)
            return
        val totalWidth = width - paddingLeft - paddingRight
        val totalHeight = height - paddingTop - paddingBottom
        if (totalWidth <= 0 || totalHeight <= 0)
            return

        val rh = rowHeight()
        val color = noteColor?.defaultColor ?: Color.BLACK
        val paint = Paint().apply { isAntiAlias = true }

        for (row in trackSnapshots.indices) {
            if (row == activeTrack)
                continue
            val top = rowTop(row)
            if (top + rh <= 0 || top >= height)
                continue

            paint.color = Color.argb(70, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawRect(paddingLeft.toFloat(), (top + 1).toFloat(), width.toFloat(), (top + 2).toFloat(), paint)

            val trackNotes = trackSnapshots[row]
            if (trackNotes.isEmpty())
                continue

            val space = totalWidth / trackNotes.size.toFloat()
            val iconHeight = max(1, (rh * NOTE_IMAGE_HEIGHT_SCALING * 0.6f).toInt())
            for (i in trackNotes.indices) {
                val note = trackNotes[i]
                val drawable = AppCompatResources.getDrawable(
                    context, getNoteDrawableResourceID(note.id, note.duration)
                ) ?: continue
                val aspect = if (drawable.intrinsicHeight > 0)
                    drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight else 1f
                val h = iconHeight
                val w = max(1, (h * aspect).toInt())
                val centerX = paddingLeft + (0.5f + i) * space
                val left = (centerX - 0.5f * w).toInt()
                val iconTop = top + (rh - h) / 2
                drawable.setBounds(left, iconTop, left + w, iconTop + h)
                drawable.setTint(color)
                drawable.alpha = (120 + (125 * note.volume).toInt()).coerceIn(0, 255)
                drawable.draw(canvas)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null)
            return super.onTouchEvent(event)

        // gestures on the track area: tap selects a track, long press opens the track menu,
        // vertical dragging scrolls through the tracks
        if (trackSnapshots.size > 1) {
            val rh = rowHeight()
            val row = ((event.y - paddingTop + scrollOffset) / rh).toInt()
                .coerceIn(0, trackSnapshots.size - 1)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    trackTouchDownY = event.y
                    trackTouchLastY = event.y
                    trackTouchDownRow = row
                    trackScrolling = false
                    trackLongPressTriggered = false
                    removeCallbacks(trackLongPressRunnable)
                    postDelayed(trackLongPressRunnable, 400L)
                    if (row != activeTrack) {
                        isPressed = true
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - trackTouchLastY
                    trackTouchLastY = event.y
                    if (row != activeTrack || (event.y - trackTouchDownY).absoluteValue > trackTouchSlop) {
                        trackScrolling = true
                        removeCallbacks(trackLongPressRunnable)
                        scrollTracks(-dy)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(trackLongPressRunnable)
                    isPressed = false
                    val wasScrolling = trackScrolling
                    val wasLongPress = trackLongPressTriggered
                    trackScrolling = false
                    trackLongPressTriggered = false
                    if (wasScrolling || wasLongPress)
                        return true
                    if (row != activeTrack) {
                        onTrackGestureListener?.onTrackClicked(row)
                        return true
                    }
                }
            }
        }

        if(onNoteClickListener == null)
            return super.onTouchEvent(event)
//        Log.v("Metronome", "NoteView.onTouchEvent")
//        if(event == null)
//            return super.onTouchEvent(event)
        super.onTouchEvent(event)

        val action = event.actionMasked
        val x = event.x
        // val y = event.y

        var overNoteIndex = -1
        val noteHorizontalSpace = (width - paddingLeft - paddingRight) / notes.size.toFloat()
        var horizontalPositionLeft = paddingLeft.toFloat()

        for (i in notes.indices) {
            val horizontalPositionRight = horizontalPositionLeft + noteHorizontalSpace
            //Log.v("Notes", "NoteView:onTouchEvent: noteLeft($i) = $noteLeft, noteWidth = $noteWidth, x=$x, numNotes=$numNotes")
            if(x >= horizontalPositionLeft && x < horizontalPositionRight) {
                overNoteIndex = i
                break
            }
            horizontalPositionLeft = horizontalPositionRight
        }
        //Log.v("Notes", "NoteView:onTouchEvent: overNoteIndex=$overNoteIndex")
        var overNoteUid: UId? = null
        if(overNoteIndex >= 0)
            overNoteUid = notes[overNoteIndex].uid

        return when(action) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true // this is needed to work with ripple drawables
//                Log.v("Notes", "NoteViw action down: $overNote")
                onNoteClickListener?.onDown(event, overNoteUid, overNoteIndex) ?: false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressed = false // this is needed to work with ripple drawables
                onNoteClickListener?.onUp(event, overNoteUid, overNoteIndex) ?: false
            }
            else -> {
                onNoteClickListener?.onMove(event, overNoteUid, overNoteIndex) ?: false
            }
        }
    }

    fun highlightNote(index: Int, flag: Boolean) {
        if (index in 0 until notes.size)
            notes[index].highlight = flag
        highlightNumber(index, flag)
    }

    fun setNoteAlpha(uid: UId?, alpha: Float) {
        if (uid == null)
            return
        notes.filter { it.uid == uid }.forEach { it.noteImage.alpha = alpha }
    }

    fun highlightNote(uid: UId?, flag : Boolean) {
        if(uid == null)
            return
        notes.filter { it.uid == uid }.forEach { it.highlight = flag }

        val index = notes.indexOfFirst { uid == it.uid }
        if (index >= 0)
            highlightNumber(index, flag)
    }

    private fun highlightNumber(index: Int, flag: Boolean) {
        if (index in 0 until numbering.size)
            numbering[index].isSelected = flag
    }

    fun setNoteList(noteList: ArrayList<NoteListItem>, animationDuration: Long) {
        volumeView.setVolumes(noteList)

        val uidEqual = if (noteList.size == notes.size) {
            var flag = true
            for (i in notes.indices) {
                flag = (notes[i].uid == noteList[i].uid)
//                Log.v("Metronome", "NoteView.setNoteList: uids $i: ${notes[i].uid}, ${noteList[i].uid}")

                if (!flag)
                    break
            }
            flag
        } else {
            false
        }
//        Log.v("Metronome", "NoteView.setNoteList: uidEqual = $uidEqual, ${noteList.size}, ${notes.size}, ")
        if (uidEqual) {
            noteList.zip(notes) { source, target -> target.set(source) }
        } else {
            // currently disable animation since this is triggered already by the MetronomeFragment:updateSceneTitleAndSwipeView
            if (animationDuration > 0L) {
                transition.duration = animationDuration
                TransitionManager.beginDelayedTransition(this@NoteView, transition)
            }
            val map = notes.associateBy { it.uid }.toMutableMap()
            notes.clear()
            for (note in noteList) {
                val n = map.remove(note.uid)
                if (n != null) {
                    n.set(note)
                    notes.add(n)
                } else {
                    notes.add(Note(note))
                    addView(notes.last().noteImage)
                }
            }
            map.forEach { removeView(it.value.noteImage)}

            makeSureWeHaveCorrectNumberOfNumberingViews()
            makeSureWeHaveCorrectNumberOfAdvanceMarkers()

            notes.forEachIndexed { index, note ->
                if (note.highlight)
                    highlightNumber(index, true)
            }
            requestLayout()
        }
        detectAllTuplets()
    }

    fun setNoteId(index: Int, id: Int) {
        notes.getOrNull(index)?.noteId = id
    }

    /// Recalculate all note icons, e.g. after the sound set changed.
    fun refreshNoteIcons() {
        for (note in notes)
            note.refreshDrawable()
        invalidate()
    }

    fun setVolume(index: Int, volume: Float) {
        notes.getOrNull(index)?.volume = volume
        volumeView.setVolume(index, volume)
    }

    fun setDuration(index: Int, duration: NoteDuration) {
        notes.getOrNull(index)?.duration = duration
        detectAllTuplets()
    }

    fun animateNote(index: Int) {
        val drawable = notes.getOrNull(index)?.drawable as Animatable?
        drawable?.stop()
        drawable?.start()
    }

    fun animateNote(uid: UId?) {
//        Log.v("Metronome", "NoteView:animateNote : note.id=${note?.id}")
        for (n in notes)
            if (n.uid == uid) {
//                Log.v("Metronome", "NoteView:animateNote : found note  to animate")
                val drawable = n.drawable as Animatable?
                drawable?.stop()
                drawable?.start()
            }
    }

    fun setAdvanceMarker(uid: UId?) {
        val index = notes.indexOfFirst { it.uid == uid }
        activeAdvanceMarkerIndex = index
        advanceMarkers.forEachIndexed { i, marker ->
            marker.visibility = if (i == index) View.VISIBLE else View.INVISIBLE
        }
        if (showNumbers) {
            numbering.forEachIndexed { i, number ->
                number.visibility = if (i == index) View.INVISIBLE else View.VISIBLE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun makeSureWeHaveCorrectNumberOfNumberingViews() {
        val numNumbers = if (showNumbers) notes.size else 0

        while (numbering.size < numNumbers) {
            numbering.add(
                    AppCompatTextView(context).apply {
                        gravity = Gravity.CENTER
                        text = "${numbering.size + 1 + numberOffset}"
                        //setTextColor(ContextCompat.getColorStateList(context, R.color.note_view_note))
                        if (noteColor != null)
                            setTextColor(noteColor)
                        background = null
                        setPadding(0, 0, 0, 0)
                    }
            )
            //TextViewCompat.setAutoSizeTextTypeWithDefaults(numbering.last(), TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(numbering.last(), 6, 112, 1, TypedValue.COMPLEX_UNIT_SP)
            addView(numbering.last())
        }

        while (numbering.size > numNumbers) {
            removeView(numbering.last())
            numbering.removeAt(numbering.lastIndex)
        }

        numbering.forEachIndexed { index, number ->
            number.visibility = if (index == activeAdvanceMarkerIndex) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun makeSureWeHaveCorrectNumberOfAdvanceMarkers() {
        while (advanceMarkers.size < notes.size) {
            advanceMarkers.add(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_advance_marker)
                    setPadding(0, 0, 0, 0)
                    //background = null
                    //setBackgroundColor(Color.GREEN)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    visibility = View.INVISIBLE
                    noteColor?.let { setColorFilter(it.defaultColor) }
                }
            )
            addView(advanceMarkers.last())
        }

        while (advanceMarkers.size > notes.size) {
            removeView(advanceMarkers.last())
            advanceMarkers.removeAt(advanceMarkers.lastIndex)
        }
    }

    private fun detectAllTuplets() {
        var numTuplets = detectTuplets(0, 3)
        numTuplets = detectTuplets(numTuplets, 5)
        for (i in numTuplets until tuplets.size)
            removeView(tuplets[i])
        tuplets.subList(numTuplets, tuplets.size).clear()
    }
    private fun detectTuplets(firstTupletIndex: Int, numTupletNotes: Int): Int {

        var numTupletsTotal = firstTupletIndex

        var quarterCounts = 0
        var eighthCounts = 0
        var sixteenthCounts = 0

        var tupletStartIndex = -1

        for (index in 0 .. notes.size) {
            val note = if (index < notes.size) notes[index] else null
            val duration = note?.duration

            val isTuplet = if (duration == null) {
                false
            } else {
                (numTupletNotes == 3 && duration.isTriplet()) || (numTupletNotes == 5 && duration.isQuintuplet())
            }
            var endTuplet = false
            var tupletComplete = false

            if (isTuplet && duration != null){
                when {
                    duration.hasBaseTypeQuarter() -> quarterCounts += 1
                    duration.hasBaseTypeEighth()  -> eighthCounts += 1
                    duration.hasBaseTypeSixteenth() -> sixteenthCounts += 1
                    else -> throw RuntimeException("Unknown duration base type")
                }
            }

//            Log.v("Metronome", "NoteView.detectTuplets: isTuplet=$isTuplet, numQuarter: quarterCounts= $quarterCounts, eighthCounts=$eighthCounts, sixteenthCounts=$sixteenthCounts, endTuplet=$endTuplet, tupletStartIndex=$tupletStartIndex")
            // start collecting for a new tuplet
            if (isTuplet && tupletStartIndex == -1) {
                tupletStartIndex = index
            }
            else if (isTuplet && tupletStartIndex >= 0) {
                if (sixteenthCounts > 0) {
                    if (quarterCounts > 0) {
                        endTuplet = true
                        tupletComplete = false
                    } else {
                        val numSixteenth = sixteenthCounts + 2 * eighthCounts
                        if (numSixteenth == numTupletNotes) {
                            endTuplet = true
                            tupletComplete = true
                        } else if (numSixteenth > numTupletNotes) {
                            endTuplet = true
                            tupletComplete = false
                        }
                    }
                } else if (eighthCounts > 0) {
                    val numEighth = eighthCounts + 2 * quarterCounts
                    if (numEighth == numTupletNotes) {
                        endTuplet = true
                        tupletComplete = true
                    } else if (numEighth > numTupletNotes) {
                        endTuplet = true
                        tupletComplete = false
                    }
                } else if (quarterCounts > 0) {
                    if (quarterCounts == numTupletNotes) {
                        endTuplet = true
                        tupletComplete = true
                    }
                    else if (quarterCounts > numTupletNotes) {
                        throw RuntimeException("There should not be more quarter notes than tuplet notes")
                    }
                }
            } else if (!isTuplet) {
                endTuplet = true
            }

            if (endTuplet && tupletStartIndex >= 0) {

                val tupletEndIndex = if (tupletComplete) index else index - 1

                val tuplet = if (numTupletsTotal >= tuplets.size) {
                    val newTuplet = TupletView(context).apply {
                        visibility = if (showTuplets) VISIBLE else GONE
                    }

                    addView(newTuplet)
                    tuplets.add(newTuplet)
                    newTuplet
                } else {
                    tuplets[numTupletsTotal]
                }
                tuplet.imageTintList = noteColor
                tuplet.tupletNumber = numTupletNotes
                tuplet.setStartAndEnd(tupletStartIndex, tupletEndIndex)
                tuplet.tupletComplete = tupletComplete
                ++numTupletsTotal
//                Log.v("Metronome", "NoteView.detectTuplets: ENDING TUPLET")

                // reset values
                sixteenthCounts = 0
                eighthCounts = 0
                quarterCounts = 0

                // store tupletStart - tupletEnd
                // count current note to contribute to tuplet
                if (!tupletComplete && isTuplet && duration != null) {
                    when {
                        duration.hasBaseTypeQuarter() -> quarterCounts = 1
                        duration.hasBaseTypeEighth() -> eighthCounts = 1
                        duration.hasBaseTypeSixteenth() -> sixteenthCounts = 1
                        else -> throw RuntimeException("Unknown duration base type")
                    }
                    tupletStartIndex = index
                }
                else {
                    tupletStartIndex = -1
                }
            }
        }
        return numTupletsTotal
    }
}
