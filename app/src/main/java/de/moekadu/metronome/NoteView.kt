package de.moekadu.metronome

import android.animation.Animator
import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


open class NoteView(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : View(context, attrs, defStyleAttr) {

    var lineColor : Int = Color.BLACK
        set(value) {
            field = value
            lineDrawable?.setTint(value)
        }
    var noteColor : Int = Color.BLACK
        set(value) {
            field = value
            for(note in notes)
                note.drawable?.setTint(value)
        }
    var noteHighlightColor = Color.BLACK

    private val volumePaint = Paint()
    var volumePaintStrokeWidth = 10f
        set(value) {
            field = value
            volumePaint.strokeWidth = value
        }
    var volumePaintColor = Color.BLACK
        set(value) {
            field = value
            volumePaint.color = value
        }

    private val lineDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_notelines)?.apply { mutate() }

    private val lastDrawingRect = Rect()
    private val currentDrawingRect = Rect()

    inner class Note (val note : NoteListItem) {
        private val startBounds = Rect()
        private val targetBounds = Rect()
        private val currentBounds = Rect()

        private val animator = ValueAnimator.ofFloat(0f, 1f)
        private val rectEvaluator = RectEvaluator(currentBounds)

        var highlight : Boolean = false
            set(value) {
                if(value)
                    drawable?.setTint(noteHighlightColor)
                else
                    drawable?.setTint(noteColor)
                field = value
            }

        private var drawableID = getNoteDrawableResourceID(note.id)
            set(value) {
                if (field != value) {
                    drawable = AppCompatResources.getDrawable(context, value)?.apply { mutate() }
                    highlight = highlight // reset highlight color on drawable /
                }
                field = value
            }

        var drawable = AppCompatResources.getDrawable(context, getNoteDrawableResourceID(note.id))?.apply { mutate() }
            private set

        init {

            highlight = false

            animator.addUpdateListener {
//                Log.v("Notes", "value = " + (it.animatedValue as Float))
                drawable?.bounds = rectEvaluator.evaluate(it.animatedValue as Float, startBounds, targetBounds)
//                if(numNotes == 0) {
//                    Log.v("Notes", "NoteView.Note.updateListener: drawable.bounds = ${drawable.bounds}")
//                    Log.v("Notes", "NoteView.Note.updateListener: target.bounds = ${targetBounds}")
//                }
                invalidate()
            }

            // set internal variables, which can be changed if note properties change
            update()
        }

        /// Check if anything changed in our note since the last call and update the internal parameters
        fun update() {
            drawableID = getNoteDrawableResourceID(note.id)
        }

        fun draw(canvas : Canvas) {
            val currentBounds = drawable?.bounds ?: return
            //            val volumePosX = min(currentBounds.centerX() + 0.2f * currentBounds.height() - 0.5f * volumePaintStrokeWidth,
//            currentBounds.centerX() + 0.5f * (noteWidth - volumePaintStrokeWidth))
            val volumeMax = currentBounds.top + 0.19f * currentBounds.height()
            val volumeMin = volumeMax + 0.62f * currentBounds.height()
            val volumeNow = note.volume * volumeMax + (1.0f - note.volume) * volumeMin
            // canvas.drawLine(volumePosX, volumeMin, volumePosX, volumeNow, volumePaint)

            val noteTotalWidth = (width - paddingLeft - paddingRight) / max(numNotes.toFloat(), 1.0f)
            val noteTotalLeft = currentBounds.centerX() - 0.5f * noteTotalWidth
            val noteTotalRight = currentBounds.centerX() + 0.5f * noteTotalWidth
            canvas.drawRect(noteTotalLeft, volumeNow, noteTotalRight, volumeMin, volumePaint)
//            Log.v("Metronome", "NoteView.Note.draw(), volumeMin=$volumeMin, volumeNow=$volumeNow, noteTotalLeft=$noteTotalLeft, noteTotalRight=$noteTotalRight")
//            Log.v("Metronome", "NoteView.Note.draw(), note.id=${note.id}, bounds=$currentBounds")
            drawable?.draw(canvas)

        }

        fun emerge(duration : Long) {
            animator.end()
//            if(notes.size == 1) {
//                Log.v("Notes", "Note.emerge: height=$height")
//            }
            computeTargetBounds()
            startBounds.left = (0.5f * (targetBounds.left + targetBounds.right)).roundToInt()
            startBounds.right = startBounds.left
            startBounds.top = (0.5f * (targetBounds.top + targetBounds.bottom)).roundToInt()
            startBounds.bottom = startBounds.top
            animator.duration = duration
            animator.start()
        }

        fun dissolve(duration : Long) {
            animator.end()
            drawable?.let {startBounds.set(it.bounds)}
            targetBounds.left = (0.5f * (startBounds.left + startBounds.right)).roundToInt()
            targetBounds.right = targetBounds.left
            targetBounds.top = (0.5f * (startBounds.top + startBounds.bottom)).roundToInt()
            targetBounds.bottom = targetBounds.top
//            Log.v("Notes", "NoteView.Note.dissolve: startBounds.left=${startBounds.left}, targetBounds.left=${targetBounds.left}")
//            Log.v("Notes", "NoteView.Note.dissolve: startBounds.right=${startBounds.right}, targetBounds.right=${targetBounds.right}")
//            Log.v("Notes", "NoteView.Note.dissolve: startBounds.top=${startBounds.top}, targetBounds.top=${targetBounds.top}")
//            Log.v("Notes", "NoteView.Note.dissolve: startBounds.bottom=${startBounds.bottom}, targetBounds.bottom=${targetBounds.bottom}")
            animator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    temporaryNotes.remove(this@Note)
                }
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            })
            animator.duration = duration
            animator.start()
        }

        fun animateToTarget(duration : Long) {
            animator.end()
            computeTargetBounds()

            if (duration == 0L)
            {
                drawable?.bounds = targetBounds
                invalidate()
            }
            else {
                animator.duration = duration
                drawable?.let {startBounds.set(it.bounds)}
                animator.start()
            }
        }

        val animationIsRunning
            get() = animator.isRunning

        fun computeTargetBounds() {
//            if(notes.size > 1)
//                Log.v("Notes", "NoteView.computeTargetBounds")
            val noteIndex = notes.indexOf(this)
            var aspectRatio = 1.0f
            drawable?.let {
                aspectRatio = it.intrinsicWidth.toFloat() / it.intrinsicHeight.toFloat()
            }
            val drawableHeight = noteHeight
            val drawableWidth = (drawableHeight * aspectRatio).roundToInt()

            targetBounds.left =
                (getNoteLeft(noteIndex) + 0.5f * (noteWidth - drawableWidth)).roundToInt()
            targetBounds.top = noteTop
            targetBounds.right = targetBounds.left + drawableWidth
            targetBounds.bottom = targetBounds.top + drawableHeight
//            if(notes.size == 1)
//                Log.v("Notes","NoteView.computeTargetBounds: noteTop=$noteTop, drawableHeight=$drawableHeight, height=$height")

        }
    }

    /// Number of notes in @notes, the value might be different while construction the notes-array
    private var numNotes = 0
    private val notes = ArrayList<Note>()
    private val temporaryNotes = ArrayList<Note>()

    val noteList : NoteList
        get(){
            val noteList = NoteList(0)
            for(n in notes)
                noteList.add(n.note)
            return noteList
        }

    private val noteTop
        get() = paddingTop

    private fun getNoteLeft(i : Int) : Int {
        val spacing = (width - paddingLeft - paddingRight) / max(numNotes.toFloat(), 1.0f)
        return paddingLeft + (i * spacing + 0.5f * (spacing - noteWidth)).roundToInt()
    }
    private val noteWidth : Int
        get() {
            val effectiveWidth = width - paddingLeft - paddingRight
            return min(noteHeight, (effectiveWidth / max(numNotes.toFloat(),1f)).roundToInt())
        }

    val noteHeight : Int
        get() {
            return height - paddingBottom - paddingTop
        }

    val noteBoundingBoxes
        get() = Array(numNotes) {i ->
            val r = Rect(getNoteLeft(i), noteTop, getNoteLeft(i) + noteWidth , noteTop + noteHeight)
            r.offset(left + translationX.roundToInt(), top + translationY.roundToInt())
            //Log.v("Notes", "NoteView.noteBoundingBoxes, top = $top, translationY = $translationY")
            r
        }

    interface OnClickListener {
        fun onDown(event: MotionEvent?, note : NoteListItem?, noteIndex : Int, noteBoundingBoxes : Array<Rect>) : Boolean
        fun onUp(event: MotionEvent?, note : NoteListItem?, noteIndex : Int, noteBoundingBoxes : Array<Rect>) : Boolean
        fun onMove(event: MotionEvent?, note : NoteListItem?, noteIndex : Int) : Boolean
    }

    var onClickListener : OnClickListener? = null

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
            lineColor = ta.getColor(R.styleable.NoteView_lineColor, lineColor)
            noteColor = ta.getColor(R.styleable.NoteView_noteColor, noteColor)
            noteHighlightColor = ta.getColor(R.styleable.NoteView_noteHighlightColor, noteHighlightColor)

            volumePaintColor = ta.getColor(R.styleable.NoteView_volumeColor, volumePaintColor)
            volumePaintStrokeWidth = ta.getDimension(R.styleable.NoteView_volumeStrokeWidth, volumePaintStrokeWidth)
            ta.recycle()
        }

        volumePaint.strokeWidth = volumePaintStrokeWidth
        volumePaint.style = Paint.Style.FILL
        volumePaint.color = volumePaintColor

        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                for(n in notes)
                    n.animateToTarget(0)
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if(event == null)
            return super.onTouchEvent(event)

        val action = event.actionMasked
        val x = event.x
        // val y = event.y

        var overNoteIndex = -1
        for (i in notes.indices) {
            val noteLeft = getNoteLeft(i)
            //Log.v("Notes", "NoteView:onTouchEvent: noteLeft($i) = $noteLeft, noteWidth = $noteWidth, x=$x, numNotes=$numNotes")
            if(x >= noteLeft && x < noteLeft + noteWidth) {
                overNoteIndex = i
                break
            }
        }
        //Log.v("Notes", "NoteView:onTouchEvent: overNoteIndex=$overNoteIndex")
        var overNote : NoteListItem? = null
        if(overNoteIndex >= 0)
            overNote = notes[overNoteIndex].note

        var actionTaken = false

        when(action) {
            MotionEvent.ACTION_DOWN -> {
//                Log.v("Notes", "NoteViw action down: $overNote")
                actionTaken = onClickListener?.onDown(event, overNote, overNoteIndex, noteBoundingBoxes) ?: true
                if(actionTaken)
                    isPressed = true
            }
            MotionEvent.ACTION_UP -> {
                actionTaken = onClickListener?.onUp(event, overNote, overNoteIndex, noteBoundingBoxes) ?: true
                if(actionTaken)
                    isPressed = false
            }
            else -> {
                actionTaken = onClickListener?.onMove(event, overNote, overNoteIndex) ?: true
            }
        }

        return actionTaken
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if(canvas == null)
            return
//        if(notes.size >= 2)
//            Log.v("Notes", "NoteView.onDraw")
        checkAndRecomputeTargetBounds()

        lineDrawable?.setBounds(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        //Log.v("Metronome", "NoteView.onDraw: lineColor = ${lineColor}")
        lineDrawable?.draw(canvas)

        for(i in notes.indices) {
            val note = notes[i]
            note.draw(canvas)
//            if(notes.size >= 2)
//                Log.v("Notes", "NoteView.onDraw.drawableId[$i]=${note.drawableID}")
        }

        for(note in temporaryNotes) {
            note.draw(canvas)
        }
    }

    fun highlightNote(i : Int, flag : Boolean) {
        for(j in notes.indices) {
            if(i == j)
                notes[i].highlight = flag
            else
                notes[i].highlight = false
        }
        invalidate()
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
        invalidate()
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

    fun setNotes(notes : NoteList, animationDuration: Long = 300L) {
        temporaryNotes.clear()
        temporaryNotes.addAll(this.notes)
        this.notes.clear()
        numNotes = notes.size
//        Log.v("Notes", "NoteView:setNotes : numNotes=$numNotes")

        for (i in notes.indices) {
            val noteI = notes[i]
            var noteToAdd: Note? = null
            for (n in temporaryNotes) {
                if (noteI === n.note) {
                    noteToAdd = n
                    temporaryNotes.remove(n)
                    break
                }
            }

            if (noteToAdd == null) {
                this.notes.add(Note(noteI))
                this.notes[i].emerge(animationDuration)
            } else {
                noteToAdd.update()
                this.notes.add(noteToAdd)
                this.notes[i].animateToTarget(animationDuration)
//                if(notes.size >= 2)
//                    Log.v("Notes", "NoteView:setNotes : notes[$i]note.id=${noteToAdd.note.id}")
            }
        }
//        Log.v("Notes", "NoteView:setNotes : numNotes to dissolve=${temporaryNotes.size}")
        for(n in temporaryNotes) {
            n.dissolve(animationDuration)
        }

        // invalidate drawing rectangle, such that target bounds will be recomputed in onDraw
        lastDrawingRect.set(-1, -1, 0, 0)
        invalidate()
    }

    private fun checkAndRecomputeTargetBounds() {
        getDrawingRect(currentDrawingRect)
//        if(notes.size > 1)
//            Log.v("Notes", "NoteView.checkAndRecomputeTargetBounds: currentDrawingRect=$currentDrawingRect, lastDrawingRect=$lastDrawingRect")
        if (lastDrawingRect != currentDrawingRect) {
//            if(notes.size > 1)
//                Log.v("Notes", "NoteView.checkAndRecomputeTargetBounds: triggering update of target bounds")
            for (n in notes) {
                n.computeTargetBounds()
                if (!n.animationIsRunning)
                    n.animateToTarget(0L)
            }
            lastDrawingRect.set(currentDrawingRect)
        }
    }
}