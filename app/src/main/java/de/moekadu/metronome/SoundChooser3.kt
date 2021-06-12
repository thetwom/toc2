package de.moekadu.metronome

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.view.setPadding
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import kotlin.math.min
import kotlin.math.roundToInt

class SoundChooser3(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    interface StateChangedListener {
        fun onNoteIdChanged(uid: UId, noteId: Int, status: Status) {}
        fun onVolumeChanged(uid: UId, volume: Float, status: Status) {}
        fun addNote(note: NoteListItem)
        fun removeNote(uid: UId) {}
        fun onNoteMoved(uid: UId, toIndex: Int) {}
    }

    var stateChangedListener: StateChangedListener? = null

    var status = Status.Off
        private set
    private var triggerStaticSoundChooserOnUp = true

    val noteView = NoteView(context).apply {
        showNumbers = true
        translationZ = 1f
    }
    private var volumeColor = Color.GRAY
    private var noteColor: ColorStateList? = null
    private var noteHighlightColor: ColorStateList? = null

    private val noteViewTopFraction = 0.82f
    private val plusButtonRightFraction = 1f
    private val actionButtonTopFraction = 0.64f
    private val orientation = Orientation.Portrait
    private val actionButtonSpacing = Utilities.dp2px(4f)

    private val plusButton = ImageButton(context).apply {
        setBackgroundResource(R.drawable.plus_button_background)
        setImageResource(R.drawable.ic_add)
        scaleType = ImageView.ScaleType.FIT_CENTER
        translationZ = 5f
        setPadding(0)
    }
    private val plusButtonNoteLines = ImageView(context).apply {
        setImageResource(R.drawable.ic_notelines)
        scaleType = ImageView.ScaleType.FIT_XY
        translationZ = 1f
    }

    private val deleteButton = ImageButton(context).apply {
        setBackgroundResource(R.drawable.plus_button_background)
        setImageResource(R.drawable.delete_button_icon_small)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(0)
        translationZ = 6f
        visibility = GONE
    }

    private val doneButton = ImageButton(context).apply {
        setBackgroundResource(R.drawable.plus_button_background)
        setImageResource(R.drawable.ic_done_small)
        scaleType = ImageView.ScaleType.FIT_CENTER
        translationZ = 5.9f
        setPadding(0)
        visibility = GONE
    }

    private val actionButtonsToNoteViewRatio = 0.85f

    private val temporaryBackground = View(context).apply {
        setBackgroundColor(Color.WHITE)
        translationZ = 0f
    }

    private val controlButtons = ArrayList<SoundChooserControlButton2>()
    private var activeControlButton: SoundChooserControlButton2? = null
    private var nextActiveControlButtonUidOnNoteListChange: UId? = null
    private val measureRect = Rect()

    private fun getNoteViewBottom(viewHeight: Int) = viewHeight - paddingBottom
    private fun getNoteViewTop(viewHeight: Int) = viewHeight * noteViewTopFraction
    private fun getNoteViewHeight(viewHeight: Int) =
        (getNoteViewBottom(viewHeight) - getNoteViewTop(viewHeight)).roundToInt()
    private fun getActionButtonSize(viewHeight: Int) =
        (actionButtonsToNoteViewRatio * getNoteViewHeight(viewHeight)).roundToInt()
    private fun getPlusButtonBottom(viewHeight: Int) =
        getNoteViewBottom(viewHeight) - (getNoteViewHeight(viewHeight) - getActionButtonSize(viewHeight)) / 2
    private fun getDeleteButtonStaticStateTop(viewHeight: Int) =
        when (orientation) {
            Orientation.Portrait -> {
                (viewHeight * actionButtonTopFraction).roundToInt()
            }
            Orientation.Landscape -> {
                getPlusButtonBottom(viewHeight) - getActionButtonSize(viewHeight)
            }
        }

    private fun getPlusButtonNoteLinesRight(viewWidth: Int)
            = min((viewWidth * plusButtonRightFraction).roundToInt(), viewWidth - paddingRight)
    private fun getPlusButtonRight(viewWidth: Int)
            = (getPlusButtonNoteLinesRight(viewWidth) - actionButtonSpacing).roundToInt()
    private fun getNoteViewRight(viewWidth: Int, viewHeight: Int)
        = (getPlusButtonRight(viewWidth) - getActionButtonSize(viewHeight) - actionButtonSpacing).roundToInt()
    private fun getNoteViewWidth(viewWidth: Int, viewHeight: Int)
            = getNoteViewRight(viewWidth, viewHeight) - paddingLeft

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, R.attr.soundChooserStyle3)

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.SoundChooser3,
                defStyleAttr, R.style.Widget_AppTheme_SoundChooserStyle3)

            val actionButtonTintList = ta.getColorStateList(R.styleable.SoundChooser3_actionButtonTintList)
            plusButton.imageTintList = actionButtonTintList
            deleteButton.imageTintList = actionButtonTintList
            doneButton.imageTintList = actionButtonTintList

            noteColor = ta.getColorStateList(R.styleable.SoundChooser3_noteColor)
            noteHighlightColor = ta.getColorStateList(R.styleable.SoundChooser3_noteHighlightColor)

//            Log.v("Metronome", "SoundChooser.init: lineColor: $lc, white: ${Color.WHITE}")
//            elementElevation = ta.getDimension(R.styleable.SoundChooser_elementElevation, elementElevation)
//            activeTranslationZ = ta.getDimension(R.styleable.SoundChooser_activeTranslationZ, activeTranslationZ)
//            elementPadding = ta.getDimension(R.styleable.SoundChooser_elementPadding, elementPadding)
            volumeColor = ta.getColor(R.styleable.SoundChooser3_volumeColor, Color.GRAY)
//            noteColor = ta.getColorStateList(R.styleable.SoundChooser_noteColor)
//            noteHighlightColor = ta.getColorStateList(R.styleable.SoundChooser_noteHighlightColor)
//            backgroundView.setBackgroundColor(ta.getColor(R.styleable.SoundChooser_backgroundViewColor, Color.WHITE))
            ta.recycle()
        }

        noteView.volumeColor = volumeColor
        noteView.noteColor = noteColor
        noteView.noteHighlightColor = noteHighlightColor

        plusButtonNoteLines.imageTintList = noteColor

        addView(temporaryBackground)
        addView(plusButtonNoteLines)
        addView(noteView)
        addView(plusButton)
        addView(deleteButton)
        addView(doneButton)

        plusButton.setOnClickListener {
            val newNote = controlButtons.lastOrNull()?.note?.clone()?.apply { uid = UId.create() }
                ?: NoteListItem(defaultNote, 1.0f, NoteDuration.Quarter)
            nextActiveControlButtonUidOnNoteListChange = newNote.uid
            stateChangedListener?.addNote(newNote)
        }
        deleteButton.setOnClickListener {
            deleteNote(activeControlButton?.uid)
//            activeControlButton?.let { button ->
//                val buttonIndex = controlButtons.indexOf(activeControlButton)
//                nextActiveControlButtonUidOnNoteListChange =
//                    if (buttonIndex < 0) null
//                    else if (buttonIndex >= controlButtons.size - 1) controlButtons[controlButtons.size - 2].uid
//                    else controlButtons[buttonIndex + 1].uid
//                stateChangedListener?.removeNote(button.uid)
//            }
        }
        doneButton.setOnClickListener {
            hideSoundChooser(200L)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        temporaryBackground.measure(widthMeasureSpec, heightMeasureSpec)

        val noteViewHeight = getNoteViewHeight(measuredHeight)
        val noteViewWidth = getNoteViewWidth(measuredWidth, measuredHeight)

        val noteViewHeightSpec = MeasureSpec.makeMeasureSpec(noteViewHeight, MeasureSpec.EXACTLY)
        val noteViewWidthSpec = MeasureSpec.makeMeasureSpec(noteViewWidth, MeasureSpec.EXACTLY)

        noteView.measure(noteViewWidthSpec, noteViewHeightSpec)

        val actionButtonSizeSpec = MeasureSpec.makeMeasureSpec(
            getActionButtonSize(measuredHeight),
            MeasureSpec.EXACTLY
        )
        plusButton.measure(actionButtonSizeSpec, actionButtonSizeSpec)
        doneButton.measure(actionButtonSizeSpec, actionButtonSizeSpec)
        deleteButton.measure(actionButtonSizeSpec, actionButtonSizeSpec)

        val noteLinesWidthSpec = MeasureSpec.makeMeasureSpec(
            getPlusButtonNoteLinesRight(measuredWidth) - getNoteViewRight(measuredWidth, measuredHeight),
            MeasureSpec.EXACTLY
        )
        plusButtonNoteLines.measure(noteLinesWidthSpec, noteViewHeightSpec)

        controlButtons.forEachIndexed { index, button ->
            NoteView.computeBoundingBox(index, controlButtons.size, noteViewWidth, noteViewHeight, measureRect)
            button.measure(
                MeasureSpec.makeMeasureSpec(min(measureRect.width(), measureRect.height()), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measureRect.height(), MeasureSpec.EXACTLY),
            )
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val viewHeight = b - t
        val viewWidth = r - l
        val noteViewBottom = getNoteViewBottom(viewHeight)
        val noteViewRight = getNoteViewRight(viewWidth, viewHeight)

        temporaryBackground.layout(
            0, 0, temporaryBackground.measuredWidth, temporaryBackground.measuredHeight
        )

        plusButtonNoteLines.layout(
            noteViewRight, // noteViewRight is noteLinesLeft
            noteViewBottom - plusButtonNoteLines.measuredHeight,
            noteViewRight + plusButtonNoteLines.measuredWidth,
            noteViewBottom
        )

        val plusButtonRight = getPlusButtonRight(viewWidth)
        val plusButtonBottom = getPlusButtonBottom(viewHeight)
        val plusButtonLeft = plusButtonRight - plusButton.measuredWidth
        val plusButtonTop = plusButtonBottom - plusButton.measuredHeight

        plusButton.layout(plusButtonLeft, plusButtonTop, plusButtonRight, plusButtonBottom)

        // find position of delete and done button
        var deleteButtonLeft = plusButtonLeft
        var deleteButtonTop = plusButtonTop

        var doneButtonLeft = plusButtonLeft
        var doneButtonTop = plusButtonTop

        if (status == Status.Static) {
            when (orientation) {
                Orientation.Landscape -> {
                    deleteButtonLeft += (plusButton.measuredWidth + actionButtonSpacing).toInt()
                    doneButtonLeft = (deleteButtonLeft + deleteButton.measuredWidth + actionButtonSpacing).toInt()
                }
                Orientation.Portrait -> {
                    deleteButtonTop = getDeleteButtonStaticStateTop(viewHeight)
                    doneButtonLeft = (deleteButtonLeft - deleteButton.measuredWidth - actionButtonSpacing).toInt()
                    doneButtonTop = deleteButtonTop
                }
            }
        }

        deleteButton.layout(
            deleteButtonLeft,
            deleteButtonTop,
            deleteButtonLeft + deleteButton.measuredWidth,
            deleteButtonTop + deleteButton.measuredHeight
        )

        doneButton.layout(
            doneButtonLeft,
            doneButtonTop,
            doneButtonLeft + doneButton.measuredWidth,
            doneButtonTop + doneButton.measuredHeight
        )

        val noteViewLeft = noteViewRight - noteView.measuredWidth
        val noteViewTop = noteViewBottom - noteView.measuredHeight
        noteView.layout(
            noteViewLeft,
            noteViewTop,
            noteViewRight,
            noteViewBottom)

        controlButtons.forEachIndexed { index, button ->
            NoteView.computeBoundingBox(index, controlButtons.size, noteView.measuredWidth, noteView.measuredHeight, measureRect)
            val buttonLeft = (measureRect.centerX() - 0.5f * button.measuredWidth + noteViewLeft).roundToInt()
            val buttonTop = measureRect.top + noteViewTop
            // don't re-layout for base or dynamic case since this would clash with the current translationX/Y
            //if (button.translationXTarget == 0f && button.translationYTarget == 0f)
                button.layout(buttonLeft, buttonTop, buttonLeft + button.measuredWidth, buttonTop + button. measuredHeight)
        }

        // TODO: start static sound chooser if required

    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null)
            return false
        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val previousActiveButton = activeControlButton
                var newActiveButton: SoundChooserControlButton2? = null
                for (button in controlButtons) {
                    if (button.containsCoordinates(event.x, event.y)) {
                        button.eventXOnDown = event.x
                        button.eventYOnDown = event.y
                        button.translationXInit = button.translationX
                        button.translationYInit = button.translationY
                        button.isMoving = true
                        newActiveButton = button
                        requestDisallowInterceptTouchEvent(true)
                        triggerStaticSoundChooserOnUp = true
                    }
                }

                if (newActiveButton != null && newActiveButton != previousActiveButton) {
                    setActiveControlButton(newActiveButton, 200L)
                    if (status == Status.Off)
                        showMoveOnlyState(200L)
                    return true
                }
//                controlButtons.filter { it != activeControlButton }
//                    .forEach { it.moveToTargetAndDisappear(200L) }

            }
            MotionEvent.ACTION_MOVE -> {
                activeControlButton?.let { button ->
//                    Log.v("Metronome", "SoundChooser.onTouchEvent: eventXOnDown=${button.eventXOnDown}, event.x=${event.x}")
                    button.translationX = button.translationXInit + event.x - button.eventXOnDown
                    button.translationY = button.translationYInit + event.y - button.eventYOnDown
                    deleteButton.isActivated = isViewCenterXOverDeleteButton(button)
                    return true
                }
                if (activeControlButton == null)
                    return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (deleteButton.isActivated) {
                    triggerStaticSoundChooserOnUp = false
                    activeControlButton?.moveToTargetOnDelete = true
                    deleteNote(activeControlButton?.uid)
//                    activeControlButton?.let { button ->
//                        button.isMoving = false
//                        stateChangedListener?.removeNote(button.uid)
//                        // disappearing is handled by the the setNoteList-method
//                    }
                    deleteButton.isActivated = false
                    if (status != Status.Static)
                        hideSoundChooser(0L)
                    return true
                }

                deleteButton.isActivated = false
                if (triggerStaticSoundChooserOnUp) {
                    activeControlButton?.moveToTarget(200L)
                    showStaticChooser(200L)
                    triggerStaticSoundChooserOnUp = false
                } else {
                    hideSoundChooser(200L)
                }
            }
        }
        return true
    }

    fun showMoveOnlyState(animationDuration: Long) {
        status = Status.MoveNote

//        if (animationDuration > 0L)
//            startTransitionManager(animationDuration)

        doneButton.visibility = VISIBLE // this is behind other buttons, but made visible to prepare for static sound chooser animations
        deleteButton.visibility = VISIBLE
        requestLayout()
    }

    fun showStaticChooser(animationDuration: Long) {
        if (status == Status.Static)
            return
        status = Status.Static

        if (animationDuration > 0L)
            startTransitionManager(animationDuration)
        deleteButton.visibility = VISIBLE
        doneButton.visibility = VISIBLE
        requestLayout()
    }

    fun hideSoundChooser(animationDuration: Long) {
        if (status == Status.Off)
            return
        Log.v("Metronome", "SoundChooser3.hideSoundChooser")
        status = Status.Off

        if (animationDuration > 0L)
            startTransitionManager(animationDuration)

        doneButton.visibility = GONE
        deleteButton.visibility = GONE
        setActiveControlButton(null)
    }

    fun setNoteList(noteList: ArrayList<NoteListItem>, animationDuration: Long) {

        noteView.setNoteList(noteList)

        // update control buttons if uid did not change
        if (areNoteListUidEqualToControlButtonUid(noteList)) {
            noteList.zip(controlButtons) { source, target ->
                target.set(source)
            }
        } else { // reorder renew, ... control button list
//            if (animationDuration > 0L)
//                startTransitionManager(animationDuration)
            val map = controlButtons.map { it.uid to it }.toMap().toMutableMap()
            controlButtons.clear()
            for (note in noteList) {
                val n = map.remove(note.uid)
                if (n != null) {
                    n.set(note)
                    controlButtons.add(n)
                } else {
                    val s = SoundChooserControlButton2(
                        context,
                        note,
                        volumeColor,
                        noteColor,
                        noteHighlightColor
                    )
                    s.translationZ = 8f
                    //s.visibility = View.GONE
                    controlButtons.add(s)
                    addView(s)
                }
            }
            // remove remaining control buttons
            map.forEach {
                val s = it.value
                if (s.visibility == VISIBLE && animationDuration > 0L && s.moveToTargetOnDelete) {
                    s.translationXTarget =  deleteButton.x + 0.5f * (deleteButton.width - s.width) - s.left
                    s.translationYTarget = deleteButton.y + 0.5f * (deleteButton.height - s.height) - s.top
                    s.moveToTargetAndDisappear(200L) { removeView(s) }
                } else {
                    removeView(s)
                }
            }

            controlButtons.forEachIndexed { index, button -> button.numberOffset = index }
            //setControlButtonTargetTranslations()
            requestLayout()
        }

        if (status == Status.Static && nextActiveControlButtonUidOnNoteListChange != null) {
            controlButtons.find { it.uid == nextActiveControlButtonUidOnNoteListChange }?.let {
                setActiveControlButton(it, animationDuration)
            }
            nextActiveControlButtonUidOnNoteListChange = null
        }
    }

    private fun setActiveControlButton(newActiveButton: SoundChooserControlButton2?, animationDuration: Long = 0L) {
        if (activeControlButton === newActiveButton)
            return

//        if (animationDuration > 0L)
//            startTransitionManager(animationDuration)

        Log.v("Metronome", "SoundChooser3.setControlButton: controlButtons.size = ${controlButtons.size}")
        for (button in controlButtons) {
            if (newActiveButton === button) {
//                Log.v("Metronome", "SoundChooser3.setActiveControlButton: visible")
                button.visibility = VISIBLE
            }
            else {
//                Log.v("Metronome", "SoundChooser3.setActiveControlButton: gone")
                button.visibility = GONE
            }
        }
        activeControlButton = newActiveButton
    }

    private fun areNoteListUidEqualToControlButtonUid(noteList: ArrayList<NoteListItem>): Boolean {
        return if (noteList.size == controlButtons.size) {
            var flag = true
            for (i in controlButtons.indices) {
                flag = (controlButtons[i].uid == noteList[i].uid)
                if (!flag)
                    break
            }
            flag
        } else {
            false
        }
    }

    private fun deleteNote(uid: UId?) {
        if (uid == null)
            return
        if (activeControlButton?.uid == uid) {
            val buttonIndex = controlButtons.indexOf(activeControlButton)
            nextActiveControlButtonUidOnNoteListChange =
                if (buttonIndex < 0) null
                else if (buttonIndex >= controlButtons.size - 1) controlButtons[controlButtons.size - 2].uid
                else controlButtons[buttonIndex + 1].uid
        }
        stateChangedListener?.removeNote(uid)
    }
    private fun startTransitionManager(animationDuration: Long = 200L) {
        TransitionManager.beginDelayedTransition(this,
            AutoTransition().apply {
                duration = animationDuration
                ordering = TransitionSet.ORDERING_TOGETHER
            }
        )
    }

//    private fun setControlButtonTargetTranslations() {
//        val rect = Rect()
//        controlButtons.forEachIndexed { index, button ->
//            NoteView.computeBoundingBox(index, controlButtons.size, noteView.measuredWidth, noteView.measuredHeight, rect)
//            button.setTargetTranslation(rect.left.toFloat() + noteView.x, rect.top.toFloat() + noteView.y)
//        }
//    }

    private fun isViewCenterXOverDeleteButton(view: View): Boolean {
        val centerX = view.x + 0.5f * view.width
        return centerX > deleteButton.x && centerX < deleteButton.x + deleteButton.width
    }
    enum class Orientation {Landscape, Portrait}
    enum class Status { Off, MoveNote, Dynamic, Static}
}