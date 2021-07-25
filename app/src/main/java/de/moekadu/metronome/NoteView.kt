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
package de.moekadu.metronome

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
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
            scaleType = ImageView.ScaleType.CENTER_INSIDE
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

    private fun computeLargestAspectRatio() : Float {
        var largestAspectRatio = 0.0f
        for(duration in NoteDuration.values()) {
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
        val totalHeightExcludingTuplet = (heightScaling * totalHeight).roundToInt()

        val widthSpec = MeasureSpec.makeMeasureSpec(totalWidth, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(totalHeightExcludingTuplet, MeasureSpec.EXACTLY)
        volumeView.measure(widthSpec, heightSpec)
        lineView.measure(widthSpec, heightSpec)

        val noteImageWidth = (totalHeight * largestAspectRatio).toInt()
        val noteWidthSpec = MeasureSpec.makeMeasureSpec(noteImageWidth, MeasureSpec.EXACTLY)

        for(n in notes)
            n.noteImage.measure(noteWidthSpec, heightSpec)

        val textHeightSpec = MeasureSpec.makeMeasureSpec((0.2f * totalHeightExcludingTuplet).toInt(), MeasureSpec.EXACTLY)
        for(n in numbering)
            n.measure(textHeightSpec, textHeightSpec)

        for (tuplet in tuplets)
            tuplet.measureOnNoteView(totalWidth, totalHeight, notes.size)

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
//        Log.v("Metronome", "NoteView.onLayout: changed: $changed")
        val totalWidth = r - l - paddingLeft - paddingRight
        val totalHeight = b - t - paddingTop - paddingBottom
        val heightScaling = if (showTuplets) NOTE_IMAGE_HEIGHT_SCALING else 1.0f
        val totalHeightExcludingTuplet = (heightScaling * totalHeight).roundToInt()
        val excludingTupletTop = paddingTop + totalHeight - totalHeightExcludingTuplet

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
                val textViewLeft = (noteCenter - 0.5f * textView.measuredWidth).toInt()
                val textViewRight = textViewLeft + textView.measuredWidth
                val textViewBottom = b - t - paddingBottom
                val textViewTop = textViewBottom - textView.measuredHeight
//                Log.v("Metronome", "NoteView.onLayout, $textViewLeft, $textViewTop, $textViewRight, $textViewBottom")
                textView.layout(textViewLeft, textViewTop, textViewRight, textViewBottom)
            }
        }

        for (tuplet in tuplets)
            tuplet.layoutOnNoteView(totalWidth, totalHeight, notes.size, paddingLeft, paddingTop)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
//        return (onNoteClickListener != null && ev != null)
//        Log.v("Metronome", "NoteView.onInterceptTouchEvent")
        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if(onNoteClickListener == null || event == null)
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
            val map = notes.map {it.uid to it}.toMap().toMutableMap()
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
            numbering.removeLast()
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
