package de.moekadu.metronome

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.roundToInt


open class NoteView(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    private val lineView = ImageView(context).apply {
        setPadding(0, 0, 0, 0)
        setBackgroundResource(R.drawable.ic_notelines)
        backgroundTintList = ContextCompat.getColorStateList(context, R.color.note_view_line)
    }

    var volumeColor : Int = Color.GREEN
        set(value) {
            volumeView.color = value
            field = value
        }
    private val volumeView = NoteViewVolume(context)

    inner class Note (val note : NoteListItem) {

        var highlight : Boolean = false
            set(value) {
                noteImage.isSelected = value
                field = value
            }

        private var drawableID = getNoteDrawableResourceID(note.id)
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
            imageTintList = ContextCompat.getColorStateList(context, R.color.note_view_note)
        }

        val drawable: Drawable
            get() = noteImage.drawable

        init {
            highlight = false
            update()
        }

        /// Check if anything changed in our note since the last call and update the internal parameters
        fun update() {
            drawableID = getNoteDrawableResourceID(note.id)
        }
    }

    private val notes = ArrayList<Note>()
    private val notesMarkedToDelete = ArrayList<Note>()

    private fun computeLargestAspectRatio() : Float {
        var largestAspectRatio = 0.0f
        for(i in 0 until getNumAvailableNotes()) {
            AppCompatResources.getDrawable(context, getNoteDrawableResourceID(i))?.let { drawable ->
                largestAspectRatio = max(largestAspectRatio,
                    drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat())
            }
        }
        require(largestAspectRatio > 0.0f)
        return largestAspectRatio
    }
    private val largestAspectRatio = computeLargestAspectRatio()

    val noteList : NoteList
        get(){
            val noteList = NoteList(0)
            for(n in notes)
                noteList.add(n.note)
            return noteList
        }

    val noteBoundingBoxes
        get() = Array(notes.size) {i ->
            val noteHorizontalSpace = (width - paddingLeft - paddingRight) / notes.size.toFloat()
            val noteHeight = height - paddingTop - paddingBottom
            val r = Rect(0, 0, noteHorizontalSpace.toInt(), noteHeight)
            r.offset(
                (i * noteHorizontalSpace + paddingLeft + left + translationX).roundToInt(),
                paddingTop + top + translationY.roundToInt()
            )
            //Log.v("Notes", "NoteView.noteBoundingBoxes, top = $top, translationY = $translationY")
            r
        }

    interface OnNoteClickListener {
        fun onDown(event: MotionEvent?, note : NoteListItem?, noteIndex : Int) : Boolean
        fun onUp(event: MotionEvent?, note : NoteListItem?, noteIndex : Int) : Boolean
        fun onMove(event: MotionEvent?, note : NoteListItem?, noteIndex : Int) : Boolean
    }

    var onNoteClickListener : OnNoteClickListener? = null

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
            ta.recycle()
        }

        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                addView(volumeView)
                addView(lineView)
            }
        })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        val totalWidth = measuredWidth - paddingLeft - paddingRight
        val totalHeight = measuredHeight - paddingTop - paddingBottom

        val widthSpec = MeasureSpec.makeMeasureSpec(totalWidth, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY)
        volumeView.measure(widthSpec, heightSpec)
        lineView.measure(widthSpec, heightSpec)

        val noteImageWidth = (totalHeight * largestAspectRatio).toInt()
        val noteWidthSpec = MeasureSpec.makeMeasureSpec(noteImageWidth, MeasureSpec.EXACTLY)

        for(n in notes)
            n.noteImage.measure(noteWidthSpec, heightSpec)

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val totalWidth = r - l - paddingLeft - paddingRight

        volumeView.layout(paddingLeft, paddingTop, paddingLeft + volumeView.measuredWidth, paddingTop + volumeView.measuredHeight)
        lineView.layout(paddingLeft, paddingTop, paddingLeft + lineView.measuredWidth, paddingTop + lineView.measuredHeight)

        val noteHorizontalSpace = totalWidth / notes.size.toFloat()

        for(i in notes.indices) {
            val noteView = notes[i].noteImage
            val noteCenter = paddingLeft + (0.5f + i) * noteHorizontalSpace
            val noteImageWidth = noteView.measuredWidth
            val noteImageHeight = noteView.measuredHeight

            noteView.layout(
                (noteCenter - 0.5f * noteImageWidth).toInt(),
                paddingTop,
                (noteCenter - 0.5f * noteImageWidth).toInt() + noteImageWidth,
                paddingTop + noteImageHeight
            )
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        Log.v("Metronome", "NoteView.onInterceptTouchEvent")
        return true
    }
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Log.v("Metronome", "NoteView.onTouchEvent")
        if(event == null)
            return super.onTouchEvent(event)

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
        var overNote : NoteListItem? = null
        if(overNoteIndex >= 0)
            overNote = notes[overNoteIndex].note

        var actionTaken = false

        when(action) {
            MotionEvent.ACTION_DOWN -> {
//                Log.v("Notes", "NoteViw action down: $overNote")
                actionTaken = onNoteClickListener?.onDown(event, overNote, overNoteIndex) ?: true
                if(actionTaken)
                    isPressed = true
            }
            MotionEvent.ACTION_UP -> {
                actionTaken = onNoteClickListener?.onUp(event, overNote, overNoteIndex) ?: true
                if(actionTaken)
                    isPressed = false
            }
            else -> {
                actionTaken = onNoteClickListener?.onMove(event, overNote, overNoteIndex) ?: true
            }
        }

        return actionTaken
    }

    fun highlightNote(i : Int, flag : Boolean) {
        for(j in notes.indices) {
            if(i == j)
                notes[i].highlight = flag
            else
                notes[i].highlight = false
        }
    }

    fun highlightNote(noteListItem : NoteListItem?, flag : Boolean) {
        if(noteListItem == null)
            return
        for(n in notes) {
            if(n.note === noteListItem)
                n.highlight = flag
            else
                n.highlight = false
        }
    }

    fun animateNote(note: NoteListItem?) {
        Log.v("Metronome", "NoteView:animateNote : note.id=${note?.id}")
        for (n in notes)
            if (n.note === note) {
                Log.v("Metronome", "NoteView:animateNote : found note  to animate")
                val drawable = n.drawable as Animatable?
                drawable?.stop()
                drawable?.start()
            }
    }

    fun setNotes(newNotes : NoteList, animationDuration: Long = 300L) {
        var notesChanged = false
        if(newNotes.size == notes.size) {
            for(i in notes.indices) {
                if (!(newNotes[i] === notes[i].note)) {
                    notesChanged = true
                    break
                }
            }
        }
        else {
            notesChanged = true
        }

        if (!notesChanged) {
            for (n in notes)
                n.update()
            volumeView.setNoteList(noteList)
            return
        }

        notesMarkedToDelete.clear()
        notesMarkedToDelete.addAll(notes) // for now add all current notes to this list, some might be removed again later

        this.notes.clear()

        if(animationDuration > 0) {
            val autoTransition = AutoTransition().apply {
                this.duration = duration
            }
            TransitionManager.beginDelayedTransition(this, autoTransition)
        }

        for (i in newNotes.indices) {
            val noteI = newNotes[i]
            var noteToAdd: Note? = null
            for (n in notesMarkedToDelete) {
                if (noteI === n.note) {
                    noteToAdd = n
                    break
                }
            }

            if (noteToAdd == null) {
                val newNote = Note(noteI)
                notes.add(newNote)
                addView(newNote.noteImage)
            } else {
                notesMarkedToDelete.remove(noteToAdd)
                noteToAdd.update()
                notes.add(noteToAdd)
//                if(notes.size >= 2)
//                    Log.v("Notes", "NoteView:setNotes : notes[$i]note.id=${noteToAdd.note.id}")
            }
        }
//        Log.v("Notes", "NoteView:setNotes : numNotes to dissolve=${temporaryNotes.size}")
        for(n in notesMarkedToDelete) {
            removeView(n.noteImage)
        }

        volumeView.setNoteList(noteList)
        requestLayout()
    }
}