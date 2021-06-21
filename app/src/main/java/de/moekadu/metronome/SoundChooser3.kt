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
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Portrait Layout:
// |-----|-------------------------------------| y = 0.94
// |  v  |         Tuplets : ??? x h/2         |
// |  o  |-------------------------------------| y = 0.846
// |  l  |          Note duration: ??? x h/2   |
// |  u  |-------------------------------------| y = 0.752
// |  m  |                                     |
// |  e  |        Note selection:              |
// |  :  |           ??? x 2 * h               |
// |  r  |                                     |
// |  1  |                                     |
// |  :  |                                     |
// |  5  |                                     |
// |-----------------------|---------|---------|  y = 0.376
// |                       | done:   | delete: |
// |                       |  h x h  |  h x h  |
// |                       |         |         |
// |---------------------------------|---------|  y = 0.188
// |                                 | plus:   |
// |                noteView: h      |  h x h  |
// |                                 |         |
// |-------------------------------------------|

// Landscape layout:
// |-----|-------------------------------|                            --- y = 0.9
// |  v  |       Tuplets: 4*h x h/2      |
// |  o  |-------------------------------|                            --- y = 0.75
// |  l  |    Note duration: 4*h x h/2   |
// |  u  |-------------------------------|------------------------------| y = 0.6
// |  m  |                                                              |
// |  e  |        Note selection:  max(7*h,all space) x h               |
// |     |                                                              |
// |---------------------------------|---------|---------|---------|----| y = 0.3
// |                                 | plus:   | delete: | done:   |
// |                noteView: h      |  h x h  |   h x h |  h x h  |
// |                                 |         |         |         |
// |-------------------------------------------|---------|---------|
private class SoundChooserViewMeasures(
        val viewSpacing: Float,
        val noteViewHeightPercent: Float,
        val plusButtonRightPercent: Float, // only landscape
        val paddingLeft: Int,
        val paddingTop: Int,
        val paddingRight: Int,
        val paddingBottom: Int,
        val staticElementPadding: Int) {
    private val actionButtonsToNoteViewRatio = 0.85f
    private val gridCellHeightToNoteViewRatio = 0.9f

    val noteView = Rect()
    val extraNoteLines = Rect()
    val plus = Rect()
    val delete = Rect()
    val done = Rect()
    val noteSelection = Rect()
    val noteDuration = Rect()
    val tuplets = Rect()
    val volume = Rect()

    fun layoutPortrait(width: Int, height: Int) {
        // vertical pass
        noteView.bottom = height - paddingBottom
        noteView.top = (noteView.bottom - noteViewHeightPercent * height).roundToInt()
        //Log.v("Metronome", "SoundChooser3.layotPortrait: ${noteView.bottom}, ${noteView.top} ")
        extraNoteLines.bottom = noteView.bottom
        extraNoteLines.top = noteView.bottom - (noteView.height() * NoteView.NOTE_IMAGE_HEIGHT_SCALING).roundToInt()

        val plusButtonHeight = (actionButtonsToNoteViewRatio * extraNoteLines.height())
        plus.top = (extraNoteLines.centerY() - 0.5f * plusButtonHeight).roundToInt()
        plus.bottom = plus.top + plusButtonHeight.roundToInt()

        delete.bottom = (noteView.top - viewSpacing).roundToInt()
        delete.top = delete.bottom - plus.height()

        done.bottom = delete.bottom
        done.top = delete.top

        val gridCellHeightMax = (done.top - 3 * viewSpacing - staticElementPadding) / 3f
        val gridCellDesired = noteView.height() * gridCellHeightToNoteViewRatio
        val gridCellHeight = min(gridCellHeightMax, gridCellDesired)

        noteSelection.bottom = (done.top - viewSpacing).roundToInt()
        noteSelection.top = (noteSelection.bottom - 2 * gridCellHeight).roundToInt()

        noteDuration.bottom = (noteSelection.top - viewSpacing).roundToInt()
        noteDuration.top = (noteDuration.bottom - 0.5f * gridCellHeight).roundToInt()

        tuplets.bottom = (noteDuration.top - viewSpacing).roundToInt()
        tuplets.top = (tuplets.bottom - 0.5f * gridCellHeight).roundToInt()

        volume.bottom = noteSelection.bottom
        volume.top = tuplets.top

        // horizontal pass
        extraNoteLines.right = width - paddingRight
        extraNoteLines.left = (extraNoteLines.right - 2 * viewSpacing - plus.height()).roundToInt()

        plus.left = (extraNoteLines.centerX() - 0.5f * plus.height()).roundToInt()
        plus.right = plus.left + plus.height()

        noteView.right = extraNoteLines.left
        noteView.left = paddingLeft

        delete.right = plus.right
        delete.left = delete.right - delete.height()

        done.right = (delete.left - viewSpacing).roundToInt()
        done.left = done.right - done.height()

        // volume width:
        // user the larger value of "ratio 1:7" and tuplets height
        // but max 0.2 * width of view
        val volumeWidth = min(0.2f * width, max(volume.height() / 7.0f, 0.8f * noteDuration.height()))
        //Log.v("Metronome", "SoundChooser3: h/5=${volume.height()/5.0f}")
        volume.left = staticElementPadding
        volume.right = (volume.left + volumeWidth).roundToInt()

        noteSelection.left = (volume.right + viewSpacing).roundToInt()
        noteSelection.right = width - staticElementPadding

        noteDuration.left = noteSelection.left
        noteDuration.right = noteSelection.right

        tuplets.left = noteDuration.left
        tuplets.right = noteDuration.right
    }

    fun layoutLandscape(width: Int, height: Int) {
        // vertical pass
        noteView.bottom = height - paddingBottom
        noteView.top = (noteView.bottom - noteViewHeightPercent * height).roundToInt()

        extraNoteLines.bottom = noteView.bottom
        extraNoteLines.top = noteView.bottom - (noteView.height() * NoteView.NOTE_IMAGE_HEIGHT_SCALING).roundToInt()

        val plusButtonHeight = (actionButtonsToNoteViewRatio * extraNoteLines.height())
        plus.top = (extraNoteLines.centerY() - 0.5f * plusButtonHeight).roundToInt()
        plus.bottom = plus.top + plusButtonHeight.roundToInt()

        delete.bottom = plus.bottom
        delete.top = plus.top

        done.bottom = delete.bottom
        done.top = delete.top

        val gridCellHeightMax = (noteView.top - 3 * viewSpacing - staticElementPadding) / 2f
        val gridCellDesired = noteView.height() * gridCellHeightToNoteViewRatio
        val gridCellHeight = min(gridCellHeightMax, gridCellDesired)

        noteSelection.bottom = (noteView.top - viewSpacing).roundToInt()
        noteSelection.top = (noteSelection.bottom - gridCellHeight).roundToInt()

        noteDuration.bottom = (noteSelection.top - viewSpacing).roundToInt()
        noteDuration.top = (noteDuration.bottom - 0.5f * gridCellHeight).roundToInt()

        tuplets.bottom = (noteDuration.top - viewSpacing).roundToInt()
        tuplets.top = (tuplets.bottom - 0.5f * gridCellHeight).roundToInt()

        volume.bottom = noteSelection.bottom
        volume.top = tuplets.top

        // horizontal pass
        plus.right = (width * plusButtonRightPercent).roundToInt()
        plus.left = plus.right - plus.height()

        extraNoteLines.right = width - paddingRight
        extraNoteLines.left = (plus.left - viewSpacing).roundToInt()

        noteView.right = extraNoteLines.left
        noteView.left = paddingLeft

        delete.left = (plus.right + viewSpacing).roundToInt()
        delete.right = delete.left + delete.height()

        done.left = (delete.left + viewSpacing).roundToInt()
        done.right = done.left + done.height()

        // volume width:
        // user the larger value of "ratio 1:5" and tuplets height
        // but max 0.2 * width of view
        val volumeWidth = min(0.2f * width, max(volume.height() / 7.0f, 0.8f * noteDuration.height()))
        volume.left = staticElementPadding
        volume.right = (volume.left + volumeWidth).roundToInt()

        noteSelection.left = (volume.right + viewSpacing).roundToInt()
        // note selection width: max space, but not don't exceed square ratio of single notes
        noteSelection.right = max(width - staticElementPadding,
            noteSelection.left + getNumAvailableNotes() * noteSelection.height())

        noteDuration.left = noteSelection.left
        noteDuration.right = max(noteSelection.right, noteDuration.left + 4 * noteSelection.height())

        tuplets.left = noteDuration.left
        tuplets.right = noteDuration.right
    }
}


class SoundChooser3(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    interface StateChangedListener {
        fun changeNoteId(uid: UId, noteId: Int, status: Status)
        fun changeVolume(uid: UId, volume: Float, status: Status)
        fun changeNoteDuration(uid: UId, duration: NoteDuration) {}
        fun addNote(note: NoteListItem)
        fun removeNote(uid: UId)
        fun moveNote(uid: UId, toIndex: Int)
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

    private val orientation = Orientation.Portrait

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

    private val volumeControl = VolumeControl(context).apply {
        vertical = true
    }
    private var noteSelection: GridSelection
    private var noteDuration: GridSelection
    private var tuplets: GridSelection

    private val temporaryBackground = View(context).apply {
        setBackgroundColor(Color.WHITE)
        translationZ = 0f
    }

    private val controlButtons = ArrayList<SoundChooserControlButton2>()
    private var activeControlButton: SoundChooserControlButton2? = null
    private var nextActiveControlButtonUidOnNoteListChange: UId? = null
    private val measureRect = Rect()

    private val staticElementPadding = Utilities.dp2px(8f).roundToInt()
    private val viewSpacing = Utilities.dp2px(8f)

    private var soundChooserViewMeasures: SoundChooserViewMeasures

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, R.attr.soundChooserStyle3)

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.SoundChooser3,
                defStyleAttr, R.style.Widget_AppTheme_SoundChooserStyle3)

            val actionButtonTintList = ta.getColorStateList(R.styleable.SoundChooser3_actionButtonTintList)
            plusButton.imageTintList = actionButtonTintList
            deleteButton.imageTintList = actionButtonTintList
            doneButton.imageTintList = actionButtonTintList

            temporaryBackground.backgroundTintList = ta.getColorStateList(R.styleable.SoundChooser3_backgroundViewColor)

            noteColor = ta.getColorStateList(R.styleable.SoundChooser3_noteColor)
            noteHighlightColor = ta.getColorStateList(R.styleable.SoundChooser3_noteHighlightColor)

//            Log.v("Metronome", "SoundChooser.init: lineColor: $lc, white: ${Color.WHITE}")
//            elementElevation = ta.getDimension(R.styleable.SoundChooser_elementElevation, elementElevation)
//            activeTranslationZ = ta.getDimension(R.styleable.SoundChooser_activeTranslationZ, activeTranslationZ)
//            elementPadding = ta.getDimension(R.styleable.SoundChooser_elementPadding, elementPadding)
            volumeColor = ta.getColor(R.styleable.SoundChooser3_volumeColor, Color.GRAY)
            ta.recycle()

        }

        soundChooserViewMeasures = SoundChooserViewMeasures(viewSpacing, 0.22f, 1.0f,
            paddingLeft, paddingTop, paddingTop, paddingRight, staticElementPadding)
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

        addView(volumeControl)
        volumeControl.onVolumeChangedListener = VolumeControl.OnVolumeChangedListener { volume ->
            activeControlButton?.let { controlButton ->
                if (controlButton.volume != volume) {
//                    controlButton.setVolume(0, volume)
                    stateChangedListener?.changeVolume(controlButton.uid, volume, status)
                }
            }
        }

//
//        // TODO: numRows, numCols, spacing must be a parameter
        noteSelection = GridSelection(2, 4, Utilities.dp2px(2f).roundToInt(),
            R.drawable.grid_background_topleft_withlines,
            R.drawable.grid_background_topright_withlines,
            R.drawable.grid_background_bottomleft_withlines,
            R.drawable.grid_background_bottomright_withlines,
            R.drawable.grid_background_left_withlines,
            R.drawable.grid_background_right_withlines,
            R.drawable.grid_background_center_withlines,
            noteColor
        )
        noteSelection.activeButtonChangedListener = object : GridSelection.ActiveButtonChangedListener {
            override fun onActiveButtonChanged(index: Int) {
                activeControlButton?.let { controlButton ->
                    if (controlButton.noteId != index && index < getNumAvailableNotes()) {
//                        controlButton.setNoteId(0, index)
                        stateChangedListener?.changeNoteId(controlButton.uid, index, status)
                    }
                }
            }
        }
        noteSelection.addView(this)
        setNoteSelectionNotes(NoteDuration.Quarter)

        noteDuration = GridSelection(1, 3, Utilities.dp2px(2f).roundToInt(),
            R.drawable.grid_background_topleft,
            R.drawable.grid_background_topright,
            R.drawable.grid_background_bottomleft,
            R.drawable.grid_background_bottomright,
            R.drawable.grid_background_left,
            R.drawable.grid_background_right,
            R.drawable.grid_background_center,
            noteColor
        )
        noteDuration.addView(this)
        noteDuration.setButtonDrawable(0, R.drawable.ic_note_duration_sixteenth)
        noteDuration.setButtonDrawable(1, R.drawable.ic_note_duration_eighth)
        noteDuration.setButtonDrawable(2, R.drawable.ic_note_duration_quarter)

        tuplets = GridSelection(1, 3, Utilities.dp2px(2f).roundToInt(),
            R.drawable.grid_background_topleft,
            R.drawable.grid_background_topright,
            R.drawable.grid_background_bottomleft,
            R.drawable.grid_background_bottomright,
            R.drawable.grid_background_left,
            R.drawable.grid_background_right,
            R.drawable.grid_background_center,
            noteColor
        )
        tuplets.addView(this)
        tuplets.setButtonDrawable(0, R.drawable.ic_note_duration_normal)
        tuplets.setButtonDrawable(1, R.drawable.ic_note_duration_triplet)
        tuplets.setButtonDrawable(2, R.drawable.ic_note_duration_quintuplet)

        noteDuration.activeButtonChangedListener =
            GridSelection.ActiveButtonChangedListener { index ->
                activeControlButton?.let { controlButton ->
                    val noteDuration = getSelectedNoteDuration(index, tuplets.activeButtonIndex)
                    Log.v("Metronome", "SoundChooser3: noteDuration.onActiveButtonChanged: duration=$noteDuration, controlButton.duration=${controlButton.noteDuration}")
                    if (controlButton.noteDuration != noteDuration)
                        stateChangedListener?.changeNoteDuration(controlButton.uid, noteDuration)
                }
            }
        tuplets.activeButtonChangedListener =
            GridSelection.ActiveButtonChangedListener { index ->
                activeControlButton?.let { controlButton ->
                    val noteDuration = getSelectedNoteDuration(noteDuration.activeButtonIndex, index)
                    if (controlButton.noteDuration != noteDuration)
                        stateChangedListener?.changeNoteDuration(controlButton.uid, noteDuration)
                }
            }

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

    private fun measureView(view: View, rect: Rect) {
//        Log.v("Metronome", "SoundChooser3.measureView: $rect")
        view.measure(
            MeasureSpec.makeMeasureSpec(rect.width(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(rect.height(), MeasureSpec.EXACTLY)
        )
    }

    private fun layoutView(view: View, rect: Rect) {
//        Log.v("Metronome", "SoundChooser3.layoutView: $rect")
        view.layout(rect.left, rect.top, rect.right, rect.bottom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        Log.v("Metronome", "SoundChooser3.onMeasure")
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        temporaryBackground.measure(widthMeasureSpec, heightMeasureSpec)

        soundChooserViewMeasures.layoutPortrait(measuredWidth, measuredHeight)

        measureView(noteView, soundChooserViewMeasures.noteView)
        measureView(volumeControl, soundChooserViewMeasures.volume)
        measureView(plusButtonNoteLines, soundChooserViewMeasures.extraNoteLines)
        measureView(plusButton, soundChooserViewMeasures.plus)
        measureView(deleteButton, soundChooserViewMeasures.delete)
        measureView(doneButton, soundChooserViewMeasures.done)

        noteSelection.measure(
            soundChooserViewMeasures.noteSelection.width(),
            soundChooserViewMeasures.noteSelection.height()
        )
        noteDuration.measure(
           soundChooserViewMeasures.noteDuration.width(),
            soundChooserViewMeasures.noteDuration.height()
        )
        tuplets.measure(
            soundChooserViewMeasures.tuplets.width(),
            soundChooserViewMeasures.tuplets.height()
        )

        val noteViewWidth = soundChooserViewMeasures.noteView.width()
        val noteViewHeight = soundChooserViewMeasures.noteView.height()
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
        Log.v("Metronome", "SoundChooser3.onLayout")
        val viewHeight = b - t
        val viewWidth = r - l
        soundChooserViewMeasures.layoutPortrait(viewWidth, viewHeight)

        temporaryBackground.layout(
           0, 0, temporaryBackground.measuredWidth, temporaryBackground.measuredHeight
        )

        layoutView(noteView, soundChooserViewMeasures.noteView)
        layoutView(volumeControl, soundChooserViewMeasures.volume)
        layoutView(plusButtonNoteLines, soundChooserViewMeasures.extraNoteLines)
        layoutView(plusButton, soundChooserViewMeasures.plus)
        // position of the following elements on plus button ... will change by using translation later
        layoutView(deleteButton, soundChooserViewMeasures.plus)
        layoutView(doneButton, soundChooserViewMeasures.plus)

        noteSelection.layout(
            soundChooserViewMeasures.noteSelection.left,
            soundChooserViewMeasures.noteSelection.top,
            soundChooserViewMeasures.noteSelection.right,
            soundChooserViewMeasures.noteSelection.bottom
        )
        noteDuration.layout(
            soundChooserViewMeasures.noteDuration.left,
            soundChooserViewMeasures.noteDuration.top,
            soundChooserViewMeasures.noteDuration.right,
            soundChooserViewMeasures.noteDuration.bottom
        )
        tuplets.layout(
            soundChooserViewMeasures.tuplets.left,
            soundChooserViewMeasures.tuplets.top,
            soundChooserViewMeasures.tuplets.right,
            soundChooserViewMeasures.tuplets.bottom
        )

        controlButtons.forEach { button ->
            button.layout(0, 0, button.measuredWidth, button.measuredHeight)
        }
        setControlButtonTargetTranslations(
            soundChooserViewMeasures.noteView.left,
            soundChooserViewMeasures.noteView.top
        )

        controlButtons.forEach { button ->
            //if (button.visibility != VISIBLE || !button.isBeingDragged)
            if (!button.isBeingDragged)
                button.moveToTarget(0L)
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
                        button.startDragging(event.x, event.y)
                        newActiveButton = button
                        requestDisallowInterceptTouchEvent(true)
                        triggerStaticSoundChooserOnUp = true
                    }
                }

                if (newActiveButton != null && newActiveButton != previousActiveButton) {
                    setActiveControlButton(newActiveButton, 0L)

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

//                    Log.v("Metronome", "SoundChooser.onTouchEvent: butonCenterXCheck: ${button.centerXWithinBoundsToKeepPosition()}")
                    if (!button.centerXWithinBoundsToKeepPosition()) {
                        val newIndex = findNewNoteListPosition(button)
                        if (newIndex >= 0) {
                            stateChangedListener?.moveNote(button.uid, newIndex)
                            triggerStaticSoundChooserOnUp = false
                        }
                    }
                    return true
                }
                if (activeControlButton == null)
                    return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeControlButton?.stopDragging()

                if (deleteButton.isActivated) {
                    triggerStaticSoundChooserOnUp = false
                    activeControlButton?.moveToTargetOnDelete = true
                    deleteNote(activeControlButton?.uid)
                    deleteButton.isActivated = false
//                    activeControlButton?.stopDragging()
                }

                if (triggerStaticSoundChooserOnUp) {
                    activeControlButton?.moveToTarget(200L)
                    showStaticChooser(200L)
                    triggerStaticSoundChooserOnUp = false
                } else if (status != Status.Static){
                    // activeControlButton?.stopDragging()
                    hideSoundChooser(200L)
                }
                else {
                    activeControlButton?.moveToTarget(200L)
                }
            }
        }
        return true
    }

    fun showMoveOnlyState(animationDuration: Long) {
        if (status == Status.MoveNote)
            return
        status = Status.MoveNote

        if (animationDuration == 0L) {
            deleteButton.alpha = 1f
            deleteButton.translationX = 0f
            deleteButton.translationY = 0f
            deleteButton.visibility = VISIBLE
        } else {
            if (deleteButton.visibility != VISIBLE)
                deleteButton.alpha = 0f
            deleteButton.visibility = VISIBLE
            deleteButton.animate()
                .setDuration(animationDuration)
                .translationX(0f)
                .translationY(0f)
                .alpha(1f)
        }
    }

    fun showStaticChooser(animationDuration: Long) {
        if (status == Status.Static)
            return
        status = Status.Static

        val deleteButtonTranslationX: Float = (soundChooserViewMeasures.delete.left - soundChooserViewMeasures.plus.left).toFloat()
        val deleteButtonTranslationY: Float = (soundChooserViewMeasures.delete.top - soundChooserViewMeasures.plus.top).toFloat()
        val doneButtonTranslationX: Float = (soundChooserViewMeasures.done.left - soundChooserViewMeasures.plus.left).toFloat()
        val doneButtonTranslationY: Float = (soundChooserViewMeasures.done.top - soundChooserViewMeasures.plus.top).toFloat()

        if (animationDuration > 0L) {
            if (doneButton.visibility != VISIBLE)
                doneButton.alpha = 0f
            if (deleteButton.visibility != VISIBLE)
                deleteButton.alpha = 0f

            doneButton.animate()
                .setDuration(animationDuration)
                .translationX(doneButtonTranslationX)
                .translationY(doneButtonTranslationY)
                .alpha(1.0f)

            deleteButton.animate()
                .setDuration(animationDuration)
                .translationX(deleteButtonTranslationX)
                .translationY(deleteButtonTranslationY)
                .alpha(1.0f)
        } else {
            deleteButton.translationX = deleteButtonTranslationX
            deleteButton.translationY = deleteButtonTranslationY
            deleteButton.alpha = 1.0f
            doneButton.translationX = doneButtonTranslationX
            doneButton.translationY = doneButtonTranslationY
            doneButton.alpha = 1.0f
        }

        deleteButton.visibility = VISIBLE
        doneButton.visibility = VISIBLE
    }

    fun hideSoundChooser(animationDuration: Long) {
        if (status == Status.Off)
            return
//        Log.v("Metronome", "SoundChooser3.hideSoundChooser: animationDuration=$animationDuration")
        status = Status.Off

        if (animationDuration > 0L) {
            if (doneButton.visibility == VISIBLE) {
                doneButton.animate()
                    .setDuration(animationDuration)
                    .alpha(0f)
                    .withEndAction {
                        doneButton.translationX = 0f
                        doneButton.translationY = 0f
                        doneButton.visibility = GONE
                    }
            }
            if (deleteButton.visibility == VISIBLE) {
                deleteButton.animate()
                    .setDuration(animationDuration)
                    .alpha(0f)
                    .withEndAction {
                        deleteButton.translationX = 0f
                        deleteButton.translationY = 0f
                        deleteButton.visibility = GONE
                    }
            }
        } else {
            doneButton.visibility = GONE
            deleteButton.visibility = GONE
        }
        setActiveControlButton(null, animationDuration)
    }

    fun setNoteList(noteList: ArrayList<NoteListItem>, animationDuration: Long) {
        Log.v("Metronome", "SoundChooser3.setNoteList: noteList[0] = ${noteList[0].duration}")
        noteView.setNoteList(noteList)
        val noteDurationBefore = activeControlButton?.noteDuration

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
                        noteColor //  noteHighlightColor
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
                    val translationXTarget =  deleteButton.x + 0.5f * (deleteButton.width - s.width) - s.left
                    val translationYTarget = deleteButton.y + 0.5f * (deleteButton.height - s.height) - s.top
                    s.setTargetTranslation(translationXTarget, translationYTarget)
                    //s.moveToTargetAndDisappear(animationDuration) { removeView(s) }
                    s.moveToTarget(animationDuration, true) { removeView(s) }
                } else {
                    removeView(s)
                }
            }

            controlButtons.forEachIndexed { index, button -> button.numberOffset = index }
            setControlButtonTargetTranslations(noteView.x.roundToInt(), noteView.y.roundToInt())
            //controlButtons.forEach { it.moveToTarget(0L) }
            //requestLayout()
        }

        if (status == Status.Static && nextActiveControlButtonUidOnNoteListChange != null) {
            controlButtons.find { it.uid == nextActiveControlButtonUidOnNoteListChange }?.let {
                setActiveControlButton(it, 0L)
            }
            nextActiveControlButtonUidOnNoteListChange = null
        }

        activeControlButton?.noteDuration?.let {noteDuration ->
            if (noteDuration != noteDurationBefore)
                setNoteSelectionNotes(noteDuration)
        }

    }

    private fun setActiveControlButton(button: SoundChooserControlButton2?, animationDuration: Long) {
        if (activeControlButton === button)
            return
        activeControlButton = button

        controlButtons.forEach {
            noteView.setNoteAlpha(it.uid, if (it.uid == button?.uid) 0.3f else 1f)

            if (animationDuration == 0L) {
                if (it === button) {
                    it.visibility = VISIBLE
                    it.alpha = 1.0f
                } else {
                    it.visibility = GONE
                }
            } else if (it === button && it.visibility != VISIBLE) {
                it.alpha = 0.0f
                it.animate().setDuration(animationDuration).alpha(1.0f)
            } else if (!(it === button) && it.visibility == VISIBLE) {
                Log.v("Metronome", "SoundChooser3.setActiveControlButton, fade out")
                //it.moveToTargetAndDisappear(animationDuration) { it.visibility = GONE }
                it.moveToTarget(animationDuration, true) { it.visibility = GONE }
            }
        }

        // set static sound chooser elements
        activeControlButton?.let { controlButton ->
            volumeControl.setVolume(controlButton.volume, animationDuration)
            noteSelection.setActiveButton(controlButton.noteId)
            noteDuration.setActiveButton(getNoteDurationIndex(controlButton.noteDuration))
            tuplets.setActiveButton(getTupletIndex(controlButton.noteDuration))
            setNoteSelectionNotes(controlButton.noteDuration)
        }
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
                if (buttonIndex < 0 || controlButtons.size <= 1) null
                else if (buttonIndex >= controlButtons.size - 1) controlButtons[controlButtons.size - 2].uid
                else controlButtons[buttonIndex + 1].uid
        }
        stateChangedListener?.removeNote(uid)
    }

    private fun setControlButtonTargetTranslations(noteViewLeft: Int, noteViewTop: Int) {
        controlButtons.forEachIndexed { index, button ->
            NoteView.computeBoundingBox(
                index,
                controlButtons.size,
                noteView.measuredWidth,
                noteView.measuredHeight,
                measureRect
            )
            val translationXTarget = measureRect.centerX() - 0.5f * button.measuredWidth + noteViewLeft
            val translationYTarget = measureRect.top.toFloat() + noteViewTop
            button.setTargetTranslation(translationXTarget, translationYTarget)
        }

        // set left bounds which defines when the button is being dragged so far that the
        // note should switch the position in the note list
        controlButtons.forEachIndexed { index, button ->
            button.leftBoundToSwitchPosition = if (index == 0) {
                Float.NEGATIVE_INFINITY
            } else {
                val leftButton = controlButtons[index - 1]
                val rightBoundLeftButton = leftButton.translationXTarget + leftButton.measuredWidth
                val additionalBound = min(Utilities.dp2px(4f), leftButton.measuredWidth / 3.0f)
                rightBoundLeftButton - additionalBound
            }
        }

        // set right bounds which defines when the button is being dragged so far that the
        // note should switch the position in the note list
        controlButtons.forEachIndexed { index, button ->
            button.rightBoundToSwitchPosition = if (index == controlButtons.size - 1) {
                Float.POSITIVE_INFINITY
            } else {
                val rightButton = controlButtons[index + 1]
                val rightBoundLeftButton = rightButton.translationXTarget
                val additionalBound = min(Utilities.dp2px(4f), rightButton.measuredWidth / 3.0f)
                rightBoundLeftButton + additionalBound
            }
        }
    }

    private fun findNewNoteListPosition(view: View): Int {
        var newIndex = -1
        var minCenterDist = Float.POSITIVE_INFINITY
        val xCenter = view.x + 0.5f * view.width
        controlButtons.forEachIndexed { index, button ->
            val centerDist = (button.translationXTarget + 0.5f * button.measuredWidth - xCenter).absoluteValue
            if (button.coordinateXWithinBoundsToKeepPosition(xCenter) && centerDist < minCenterDist){
                minCenterDist = centerDist
                newIndex = index
            }
        }
        return newIndex
    }

    private fun isViewCenterXOverDeleteButton(view: View): Boolean {
        val centerX = view.x + 0.5f * view.width
        return centerX > deleteButton.x && centerX < deleteButton.x + deleteButton.width
    }

    private fun getSelectedNoteDuration(indexDuration: Int, indexTuplets: Int): NoteDuration {
        Log.v("Metronome", "SoundChooser3.getSelectedNoteDuration: indexDuration: $indexDuration, indexTuplets: $indexTuplets")
        return when (indexTuplets) {
            1 -> when (indexDuration) {
                1 -> NoteDuration.EighthTriplet
                0 -> NoteDuration.SixteenthTriplet
                else -> NoteDuration.QuarterTriplet
            }
            2 -> when (indexDuration) {
                1 -> NoteDuration.EighthQuintuplet
                0 -> NoteDuration.SixteenthQuintuplet
                else -> NoteDuration.QuarterQuintuplet
            }
            else -> when (indexDuration) {
                1 -> NoteDuration.Eighth
                0 -> NoteDuration.Sixteenth
                else -> NoteDuration.Quarter
            }
        }
    }

    private fun getNoteDurationIndex(noteDuration: NoteDuration): Int {
        return when (noteDuration) {
            NoteDuration.Quarter, NoteDuration.QuarterTriplet, NoteDuration.QuarterQuintuplet -> 2
            NoteDuration.Eighth, NoteDuration.EighthTriplet, NoteDuration.EighthQuintuplet -> 1
            NoteDuration.Sixteenth, NoteDuration.SixteenthTriplet, NoteDuration.SixteenthQuintuplet -> 0
        }
    }

    private fun getTupletIndex(noteDuration: NoteDuration): Int {
        return when (noteDuration) {
            NoteDuration.Quarter, NoteDuration.Eighth, NoteDuration.Sixteenth -> 0
            NoteDuration.QuarterTriplet, NoteDuration.EighthTriplet, NoteDuration.SixteenthTriplet -> 1
            NoteDuration.QuarterQuintuplet, NoteDuration.EighthQuintuplet, NoteDuration.SixteenthQuintuplet -> 2
        }
    }

    private fun setNoteSelectionNotes(noteDuration: NoteDuration) {
        for (note in 0 until getNumAvailableNotes())
            noteSelection.setButtonDrawable(note, getNoteDrawableResourceID(note, noteDuration))
    }

    enum class Orientation {Landscape, Portrait}
    enum class Status { Off, MoveNote, Dynamic, Static}
}
