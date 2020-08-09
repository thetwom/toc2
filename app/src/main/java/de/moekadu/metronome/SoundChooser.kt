package de.moekadu.metronome

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.transition.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import kotlin.math.*

class SoundChooser(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    companion object{
        const val CHOICE_BASE = 0
        const val CHOICE_DYNAMIC = 1
        const val CHOICE_STATIC = 2
        const val CHOICE_OFF = 3
    }

    constructor(context : Context, attrs : AttributeSet? = null) : this(context, attrs, R.attr.soundChooserStyle)

    private val backgroundView = ImageButton(context)
    private val controlButton = SoundChooserControlButton(context)
    private val controlButtonAnimator = AnimatorSet()

    private val choiceButtons = Array(availableNotes.size) {i ->
        SoundChooserChoiceButton(context, i)
    }

    var choiceStatus = CHOICE_OFF
        private set

    private var boundingBoxes = ArrayList<Rect>(0)
    private var activeBoxIndex = -1
    private var activeBoxLeft = 0
    private var activeBoxRight = 0

    private var activeVerticalIndex = -1
    private val activeVerticalRatios = floatArrayOf(1f, 0.6f, 0.4f)
    private var activeVerticalTop = 0
    private var activeVerticalBottom = 0
    private var activeVerticalCenters = FloatArray(availableNotes.size) {Float.MAX_VALUE}

    private val toleranceInDp = 2f
    private val tolerance = toleranceInDp * Resources.getSystem().displayMetrics.density

    private val choiceButtonSpacingInDp = 2f
    private val choiceButtonSpacing = choiceButtonSpacingInDp * Resources.getSystem().displayMetrics.density

    private var volumePaintColor = Color.BLACK

    var activeNote : NoteListItem? = null
        private set

    interface StateChangedListener {
        fun onPositionChanged(note : NoteListItem, boxIndex : Int) { }
        fun onNoteDeleted(note : NoteListItem) { }
        fun onNoteChanged(note : NoteListItem, noteID : Int) { }
        fun onSoundChooserDeactivated(note : NoteListItem?) {}
        fun onVolumeChanged(note : NoteListItem, volume : Float) {}
    }
    var stateChangedListener : StateChangedListener? = null

    private val deleteButton = SoundChooserDeleteButton(context)
    private val doneButton = SoundChooserDoneButton(context)

    private var elementElevation = 5.0f
    private var activeTranslationZ = 10.0f

    private var elementPadding = 4.0f

    private var triggerStaticChooserOnUp = true

    private val volumeControl = VolumeControl(context)

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.SoundChooser,
                defStyleAttr, R.style.Widget_AppTheme_SoundChooserStyle)

//            Log.v("Metronome", "SoundChooser.init: lineColor: $lc, white: ${Color.WHITE}")
            elementElevation = ta.getDimension(R.styleable.SoundChooser_elementElevation, elementElevation)
            activeTranslationZ = ta.getDimension(R.styleable.SoundChooser_activeTranslationZ, activeTranslationZ)
            elementPadding = ta.getDimension(R.styleable.SoundChooser_elementPadding, elementPadding)
            volumePaintColor = ta.getColor(R.styleable.SoundChooser_volumeColor, volumePaintColor)
//            volumeControl.sliderColor = ta.getColor(R.styleable.SoundChooser_volumeControlSliderColor, volumeControl.sliderColor)
//            volumeControl.belowSliderColor = ta.getColor(R.styleable.SoundChooser_volumeControlBelowSliderColor, volumeControl.belowSliderColor)
//            volumeControl.iconColor = ta.getColor(R.styleable.SoundChooser_volumeControlOnSliderColor, volumeControl.iconColor)
//            volumeControl.backgroundSurfaceColor = ta.getColor(R.styleable.SoundChooser_volumeControlBackgroundColor, volumeControl.backgroundSurfaceColor)
            backgroundView.setBackgroundColor(ta.getColor(R.styleable.SoundChooser_backgroundViewColor, Color.WHITE))
            ta.recycle()
        }

        addView(backgroundView)
        backgroundView.alpha = 0.7f
        //backgroundView.elevation = elementElevation
        backgroundView.visibility = View.GONE

        controlButton.volumeColor = volumePaintColor
        addView(controlButton)
        controlButton.elevation = elementElevation + tolerance // slightly increase elevation to be sure that this button is always on top
        controlButton.visibility = View.GONE
        addView(deleteButton)

        deleteButton.visibility = View.GONE
        deleteButton.elevation = elementElevation
        deleteButton.setOnClickListener {
            Log.v("Notes", "SoundChooser.deleteButton.onClick")
            deleteActiveNoteIfPossible()
        }

        addView(doneButton)
        doneButton.elevation = elementElevation
        doneButton.setOnClickListener {
            deactivate()
        }

        for(c in choiceButtons) {
            c.button.elevation = elementElevation
            c.button.volumeColor = volumePaintColor

            addView(c.button)
            c.button.visibility = View.GONE
            c.button.onNoteClickListener = object : NoteView.OnNoteClickListener {
                override fun onDown(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {
//                    Log.v("Notes", "SoundChooser.choiceButton.onDown")
                    return true
                }
                override fun onMove(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {
                    return true
                }
                override fun onUp(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {

                    this@SoundChooser.activeNote?.let {
                        stateChangedListener?.onNoteChanged(it, c.noteID)
                    }
                    controlButton.setNoteId(c.noteID)
                    for(cB in choiceButtons)
                        cB.button.highlightNote(0, cB === c)
                    return true
                }
            }
        }

        addView(volumeControl)
        volumeControl.elevation = elementElevation
        volumeControl.visibility = View.GONE
        volumeControl.onVolumeChangedListener = object : VolumeControl.OnVolumeChangedListener {
            override fun onVolumeChanged(volume: Float) {
                // show new volume in control button
                controlButton.setNoteVolume(volume)

                // call our listener that the note changed
                activeNote?.let {
                    stateChangedListener?.onVolumeChanged(it, volume)
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        when(choiceStatus) {
            CHOICE_BASE -> measureChoiceBase(measuredWidth, measuredHeight)
            CHOICE_DYNAMIC -> measureChoiceDynamic(measuredWidth, measuredHeight)
            CHOICE_STATIC -> measureChoiceStatic(measuredWidth, measuredHeight)
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun measureChoiceBase(measuredWidth : Int, measuredHeight : Int) {
        deleteButton.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth - 2 * elementPadding.roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(computeDeleteButtonHeight(measuredHeight), MeasureSpec.EXACTLY)
        )

        backgroundView.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight - boundingBoxesHeight - paddingBottom, MeasureSpec.EXACTLY)
        )

        if(activeBoxIndex >= 0 && activeBoxIndex < boundingBoxes.size) {
            val box = boundingBoxes[activeBoxIndex]
            controlButton.measure(
                    MeasureSpec.makeMeasureSpec(min(box.width(), box.height()), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(box.height(), MeasureSpec.EXACTLY)
            )
        }
    }

    private fun measureChoiceDynamic(measuredWidth : Int, measuredHeight : Int) {
        measureChoiceBase(measuredWidth, measuredHeight)

        var normalizedSize = activeVerticalRatios[0]
        var counter = 1
        for(i in 1 until activeVerticalRatios.size - 1) {
            if(availableNotes.size - counter >= 2) {
                normalizedSize += 2 * activeVerticalRatios[i]
                counter += 2
            }
            else if(availableNotes.size - counter == 1) {
                normalizedSize += activeVerticalRatios[i]
                ++counter
                break
            }
            else {
                break
            }
        }
        normalizedSize += (availableNotes.size - counter) * activeVerticalRatios.last()

        val verticalSpace = (measuredHeight
                - computeDeleteButtonHeight(measuredHeight)
                - boundingBoxesHeight
                - 3 * elementPadding
                - paddingBottom
                )
        val largestChoiceSize =  min(activeVerticalRatios[0] * verticalSpace / normalizedSize, boundingBoxesHeight.toFloat())


        for(i in choiceButtons.indices) {
            val sizeRatio = activeVerticalRatios[min(
                    activeVerticalRatios.size - 1,
                    abs(i - activeVerticalIndex)
            )]

            val newSizeSpec = MeasureSpec.makeMeasureSpec(
                    (largestChoiceSize * sizeRatio).roundToInt() - choiceButtonSpacing.toInt(),
                    MeasureSpec.EXACTLY
            )
            choiceButtons[i].button.measure(newSizeSpec, newSizeSpec)
        }
    }

    private fun measureChoiceStatic(measuredWidth : Int, measuredHeight : Int) {
        measureChoiceBase(measuredWidth, measuredHeight)

        doneButton.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth - 2 * elementPadding.roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(computeDoneButtonHeight(measuredHeight), MeasureSpec.EXACTLY)
        )

        volumeControl.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth - 2 * elementPadding.roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(computeVolumeControlHeight(measuredWidth, measuredHeight), MeasureSpec.EXACTLY)
        )

        val buttonSize = MeasureSpec.makeMeasureSpec(computeChoiceButtonSize(measuredWidth, measuredHeight).toInt(), MeasureSpec.EXACTLY)
        for(c in choiceButtons){
            c.button.measure(buttonSize, buttonSize)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        Log.v("Metronome", "SoundChooser.onLayout")
        when(choiceStatus) {
            CHOICE_BASE -> layoutChoiceBase(l, t, r, b)
            CHOICE_DYNAMIC -> layoutChoiceDynamic(l, t, r, b)
            CHOICE_STATIC -> layoutChoiceStatic(l, t, r, b)
        }
    }

    private fun layoutChoiceBase(l: Int, t: Int, r: Int, b: Int) {
        Log.v("Metronome", "SoundChooser.layoutChoiceBase")
        deleteButton.layout(
                elementPadding.toInt(),
                elementPadding.toInt(),
                elementPadding.toInt() + deleteButton.measuredWidth,
                elementPadding.toInt() + deleteButton.measuredHeight)

        backgroundView.layout(0, 0, backgroundView.measuredWidth, backgroundView.measuredHeight)

        controlButton.layout(0, 0, controlButton.measuredWidth, controlButton.measuredHeight)
    }

    private fun layoutChoiceDynamic(l: Int, t: Int, r: Int, b: Int) {
        Log.v("Metronome", "SoundChooser.layoutChoiceDynamic")
        layoutChoiceBase(l, t, r, b)

        var verticalPosition = b - t - boundingBoxesHeight - elementPadding - paddingBottom

        for(i in choiceButtons.indices) {
            val v = choiceButtons[i].button
            v.layout(0, verticalPosition.toInt() - v.measuredHeight, v.measuredWidth, verticalPosition.toInt())
            activeVerticalCenters[i] = verticalPosition - 0.5f * v.measuredHeight
            if(i == activeVerticalIndex) {
                activeVerticalBottom = (verticalPosition + choiceButtonSpacing).toInt()
                activeVerticalTop = (verticalPosition - v.measuredHeight - choiceButtonSpacing).toInt()
            }

            verticalPosition -= v.measuredHeight + choiceButtonSpacing
        }
    }

    private fun layoutChoiceStatic(l: Int, t: Int, r: Int, b: Int) {
//        Log.v("Metronome", "SoundChooser.layoutChoiceStatic")
        layoutChoiceBase(l, t, r, b)

        var pos = (b - t - paddingBottom - boundingBoxesHeight - elementPadding).toInt()

        doneButton.layout(
                elementPadding.toInt(),
                pos - doneButton.measuredHeight,
                elementPadding.toInt() + doneButton.measuredWidth,
                pos
        )

        pos = pos - doneButton.measuredHeight - elementPadding.toInt()

        volumeControl.layout(
                elementPadding.toInt(),
                pos - volumeControl.measuredHeight,
                elementPadding.toInt() + volumeControl.measuredWidth,
                pos
        )
        pos = pos - volumeControl.measuredHeight - elementPadding.toInt()

        val numCols = computeNumCols(measuredWidth, measuredHeight)
        // we assume that all buttons have the same size
        val buttonWidth = choiceButtons[0].button.measuredWidth

        val choiceButtonsTotalWidth = numCols * buttonWidth + (numCols - 1) * choiceButtonSpacing
        val effectiveWidth = r - l - 2 * elementPadding
        val choiceButtonsLeft = elementPadding + 0.5f * (effectiveWidth - choiceButtonsTotalWidth)

        val numRows = computeNumRows(numCols)
        val buttonHeight = choiceButtons[0].button.measuredHeight
        val choiceButtonsTotalHeight = numRows * buttonHeight + (numRows - 1) * choiceButtonSpacing
        val choiceButtonSpaceTop = 2 * elementPadding + deleteButton.measuredHeight
        val effectiveHeight = pos - choiceButtonSpaceTop

        val choiceButtonsTop = choiceButtonSpaceTop + 0.5f * (effectiveHeight - choiceButtonsTotalHeight)

        var iCol = 0
        var iRow = 0
        var horizontalPosition = choiceButtonsLeft.toInt()
        var verticalPosition = choiceButtonsTop.toInt()

        for(i in choiceButtons.indices) {
            val c = choiceButtons[i]
            val v = c.button

            v.layout(
                    horizontalPosition,
                    verticalPosition,
                    horizontalPosition + v.measuredWidth,
                    verticalPosition + v.measuredHeight
            )
            horizontalPosition += choiceButtonSpacing.toInt() + v.measuredWidth
            iCol += 1
            if(iCol == numCols) {
                iCol = 0
                iRow += 1
                horizontalPosition = choiceButtonsLeft.toInt()
                verticalPosition += choiceButtonSpacing.toInt() + v.measuredHeight
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
//        Log.v("Metronome", "SoundChooser.onTouchEvent: translationZ = $translationZ, elevation = $elevation")
        if(event == null)
            return super.onTouchEvent(event)

        val action = event.actionMasked
        val x = event.x
        val y = event.y

        when(action) {
            MotionEvent.ACTION_DOWN -> {
                Log.v("Metronome", "SoundChooser.onTouchEvent: ACTION_DOWN, boundingBoxes.size=${boundingBoxes.size}")
                if (choiceStatus == CHOICE_STATIC)
                    return false

                controlButton.eventXOnDown = x
                controlButton.eventYOnDown = y
                controlButton.translationXInit = controlButtonTranslationXInit
                controlButton.translationYInit = controlButtonTranslationYInit

                if(choiceStatus != CHOICE_STATIC && y >= height - boundingBoxesHeight - paddingBottom
                        && x >= boundingBoxesLeft && x <= boundingBoxesRight) {
                    activateBaseLayout()
                    triggerStaticChooserOnUp = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val tX = controlButton.translationXInit + x - controlButton.eventXOnDown
                val tY = controlButton.translationYInit + y - controlButton.eventYOnDown
                if(choiceStatus == CHOICE_DYNAMIC || choiceStatus == CHOICE_BASE) {
                    controlButton.translationX = tX
                    controlButton.translationY = tY

                    if(!inActiveBoundingBoxCheck())
                        resetActiveBoundingBoxRegion()
                }

                if(choiceStatus == CHOICE_BASE) {
                    if (tY + 0.5f * controlButton.height < height - paddingBottom - 0.7f * boundingBoxesHeight)
                        activateDynamicChoices()
                }

                if(choiceStatus == CHOICE_DYNAMIC) {
                    val tXC = tX + controlButton.width + elementPadding
                    for(c in choiceButtons)
                        c.button.translationX = tXC
                    repositionDynamicChoices()
                }
            }
            MotionEvent.ACTION_UP -> {
                if(triggerStaticChooserOnUp) {
                    activateStaticChoices()
                }
                else {
                    if(deleteButton.isPressed)
                        deleteActiveNoteIfPossible()
                    deactivate()
                }
                return true
            }
        }

        return false
    }

    private fun deleteActiveNoteIfPossible() {
        if (boundingBoxes.size <= 1) {
            Toast.makeText(context, context.getString(R.string.cannot_delete_last_note), Toast.LENGTH_LONG).show()
        }
        else {
            activeNote?.let { stateChangedListener?.onNoteDeleted(it) }
        }
    }
    fun animateNote(note : NoteListItem) {
        controlButton.animateNote(note)
    }

    /// Set bounding boxes of corresponding noteView.
    /**
     * This function takes bounding boxes of a corresponding noteView in absolute coordinates and
     * then locally stores these boxes in local coordinates.
     * @param noteBoundingBoxes Bounding boxes of notes in corresponding noteView in absolute coordinates
     */
    fun setBoundingBoxes(noteBoundingBoxes: Array<Rect>) {
        // TODO: should we better store the raw coordinates, since left/top might be not correct here?
        boundingBoxes.clear()
        for(bb in noteBoundingBoxes) {
            boundingBoxes.add(
                    Rect(
                            bb.left - left - translationX.roundToInt(),
                            bb.top - top - translationY.roundToInt(),
                            bb.right - left - translationX.roundToInt(),
                            bb.bottom - top - translationY.roundToInt()
                    )
            )
        }

        if (activeBoxIndex < boundingBoxes.size && activeBoxIndex >= 0) {
            moveControlButtonToActiveBoundingBox(0L) // TODO: set animation duration
            activeBoxLeft = (boundingBoxes[activeBoxIndex].left - tolerance).roundToInt()
            activeBoxRight = (boundingBoxes[activeBoxIndex].right + tolerance).roundToInt()
        }

        if (boundingBoxes.size <= 1 && deleteButton.alpha != 0.5f)
            deleteButton.animate().alpha(0.5f)
        else if (deleteButton.alpha != 1f)
            deleteButton.animate().alpha(1f)
        requestLayout()
    }

    /// Set active note which the sound chooser should modify.
    /**
     * @param noteIndex Note index in corresponding noteView (index of bounding box, which is set in setBoundingBoxes)
     * @param note Note list item as used in noteView, and which will then be returned in the callbacks. Also
     *   this note is required to set the correct controlButton image.
     * @param animationDuration Animation duration for animating the control button to the right place.
     *   only used if the current status is CHOICE_STATIC.
     */
    fun setActiveNote(noteIndex: Int, note: NoteListItem, animationDuration: Long = 300L) {
        Log.v("Metronome", "SoundChooser2.setActiveNote: noteIndex=$noteIndex")
        activeBoxIndex = noteIndex

        // ony move controlbutton and set active region if there is a valid bounding box
        if(activeBoxIndex < boundingBoxes.size) {
//        if(choiceStatus == CHOICE_STATIC)
//            moveControlButtonToActiveBoundingBox(animationDuration, true)
//        else
//            moveControlButtonToActiveBoundingBox(0L)
            moveControlButtonToActiveBoundingBox(0L)

            activeBoxLeft = (boundingBoxes[activeBoxIndex].left - tolerance).roundToInt()
            activeBoxRight = (boundingBoxes[activeBoxIndex].right + tolerance).roundToInt()
        }

        activeNote = note
        controlButton.setNoteId(note.id)
        controlButton.setNoteVolume(note.volume)
        volumeControl.setVolume(note.volume, if(choiceStatus == CHOICE_STATIC) animationDuration else 0L)
        for(c in choiceButtons)
            c.button.highlightNote(0, c.button.noteList[0].id == activeNote?.id)
    }

    fun deactivate(animationDuration: Long = 300L) {
        activeVerticalCenters[0] = Float.MAX_VALUE
        val autoTransition = AutoTransition().apply {
            duration = animationDuration
            addListener(object: Transition.TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    translationZ = 0f
                    stateChangedListener?.onSoundChooserDeactivated(activeNote)
                }
                override fun onTransitionResume(transition: Transition) { }
                override fun onTransitionPause(transition: Transition) { }
                override fun onTransitionCancel(transition: Transition) { }
                override fun onTransitionStart(transition: Transition) { }
            })
        }
        TransitionManager.beginDelayedTransition(this, autoTransition)
        choiceStatus = CHOICE_OFF
//        controlButton.visibility = View.GONE
        deleteButton.visibility = View.GONE
        deleteButton.isPressed = false
        doneButton.visibility = View.GONE
        backgroundView.visibility = View.GONE
        volumeControl.visibility = GONE
        for(c in choiceButtons)
            c.button.visibility = GONE

        controlButton.animate()
                .translationX(controlButtonTranslationXInit)
                .translationY(controlButtonTranslationYInit)
                .withEndAction{controlButton.visibility = View.GONE}
                .setDuration(animationDuration)
                .start()
    }

    fun activateBaseLayout() {
        choiceStatus = CHOICE_BASE
        translationZ = activeTranslationZ
        TransitionManager.beginDelayedTransition(this)
        backgroundView.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE
        controlButton.visibility = View.VISIBLE
        doneButton.visibility = View.GONE
        volumeControl.visibility = View.GONE
        for(c in choiceButtons)
            c.button.visibility = View.GONE

        controlButton.visibility = View.VISIBLE
        moveControlButtonToActiveBoundingBox(0L)
    }


    fun activateStaticChoices(animationDuration : Long = 300L) {
        activeVerticalCenters[0] = Float.MAX_VALUE
        choiceStatus = CHOICE_STATIC
        translationZ = activeTranslationZ
        if(animationDuration > 0) {
            val autoTransition = AutoTransition().apply { duration = animationDuration }
            TransitionManager.beginDelayedTransition(this, autoTransition)
        }
        backgroundView.visibility = View.VISIBLE
        doneButton.visibility = View.VISIBLE
        volumeControl.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE

        for(c in choiceButtons) {
            c.button.visibility = View.VISIBLE
            c.button.translationX = 0f
            c.button.highlightNote(0, c.button.noteList[0].id == activeNote?.id)
        }

        controlButton.visibility = View.VISIBLE
        moveControlButtonToActiveBoundingBox(animationDuration)
    }

    /// This function assumes, that activateBaseLayout() was already called
    private fun activateDynamicChoices() {
        triggerStaticChooserOnUp = false
        choiceStatus = CHOICE_DYNAMIC
        translationZ = activeTranslationZ

        //val autoTransition = AutoTransition().apply { duration = 80L }
        val slideTransition = Slide().apply {
            duration = 300L
            slideEdge = Gravity.BOTTOM
        }
        TransitionManager.beginDelayedTransition(this, slideTransition)
        doneButton.visibility = View.GONE
        volumeControl.visibility = View.GONE
        deleteButton.visibility = View.VISIBLE

        for(c in choiceButtons)
            c.button.visibility = View.VISIBLE
    }

    private fun moveControlButtonToActiveBoundingBox(animationDuration: Long = 300L, fadeOutIn : Boolean = false) {
//        Log.v("Metronome", "SoundChooser.moveControlButtonToActiveBoundingBox")
//        controlButtonAnimator.pause()

//        if(animationDuration > 0L && fadeOutIn) {
//            Log.v("Metronome", "SoundChooser.moveControlButtonToActiveBoundingBox: fadeOutIn, controlbutto..translationX=${controlButton.translationX}, controlButtonTranslationXInit=$controlButtonTranslationXInit")
//            controlButtonAnimator.playSequentially(
//                    ObjectAnimator.ofFloat(controlButton, View.ALPHA, controlButton.alpha, 0f).setDuration((0.5f * animationDuration).toLong())
//                    ObjectAnimator.ofFloat(controlButton, View.TRANSLATION_X, controlButton.translationX, controlButtonTranslationXInit).setDuration((0.2f * animationDuration).toLong()),
                    //ObjectAnimator.ofFloat(controlButton, View.TRANSLATION_Y, controlButton.translationY, controlButtonTranslationYInit).setDuration((0.2f * animationDuration).toLong()),
//                    ObjectAnimator.ofFloat(controlButton, View.ALPHA, 0f, 1f).setDuration((0.5f * animationDuration).toLong())
//            )
//        }
        if (animationDuration > 0L) {
            controlButton.animate()
                    .translationX(controlButtonTranslationXInit)
                    .translationY(controlButtonTranslationYInit)
                    .setDuration(animationDuration)
                    .start()
        }
        else {
            controlButton.translationX = controlButtonTranslationXInit
            controlButton.translationY = controlButtonTranslationYInit
        }

        controlButton.translationXInit = controlButtonTranslationXInit
        controlButton.translationYInit = controlButtonTranslationYInit
    }

    private val controlButtonTranslationXInit: Float
            get() {
//                Log.v("Metronome", "controlButtonTranslationXInit: activeBoxIndex=$activeBoxIndex, boundingboxes.size=${boundingBoxes.size}")
                if(activeBoxIndex >= 0 && activeBoxIndex < boundingBoxes.size) {
                    val box = boundingBoxes[activeBoxIndex]
                    return box.left.toFloat() + 0.5f * (box.width() - min(box.width(), box.height()))
                }
                return 0f
            }

    private val controlButtonTranslationYInit: Float
        get() {
            if(activeBoxIndex >= 0 && activeBoxIndex < boundingBoxes.size) {
                val box = boundingBoxes[activeBoxIndex]
                return box.top.toFloat()
            }
            return 0f
        }

    private fun inActiveBoundingBoxCheck() : Boolean {
        val centerX = 0.5f * (controlButton.left + controlButton.right) + controlButton.translationX
        return (centerX >= activeBoxLeft && centerX <= activeBoxRight)
    }

    private fun resetActiveBoundingBoxRegion() {
        val previousActiveBoxIndex = activeBoxIndex

        val currentCenterX = 0.5f * (controlButton.left + controlButton.right) + controlButton.translationX
        // Log.v("Notes", "Button left: ${button.left}, button right=${button.right}, translationX = ${button.translationX}")
        var distanceX = Float.MAX_VALUE

        for (i in boundingBoxes.indices) {
            val box = boundingBoxes[i]
            val distanceXBox = abs(currentCenterX - box.centerX())
            if(distanceXBox < distanceX) {
                distanceX = distanceXBox
                activeBoxIndex = i
            }
            // Log.v("Notes", "Box index: $i, distanceXBox=$distanceXBox, buttonCenter=$currentCenterX, boxCenter=$boxCenterX")
        }

        activeBoxLeft = (boundingBoxes[activeBoxIndex].left - tolerance).roundToInt()
        activeBoxRight = (boundingBoxes[activeBoxIndex].right + tolerance).roundToInt()

//        Log.v("Notes", "SoundChooser: ... previous index: $previousActiveBoxIndex, new index = $activeBoxIndex")
        if(previousActiveBoxIndex != activeBoxIndex) {
            activeNote?.let {
                stateChangedListener?.onPositionChanged(it, activeBoxIndex)
            }
            triggerStaticChooserOnUp = false
        }
    }


    private fun repositionDynamicChoices() {
        // Don't position dynamic choices, if centers are not set, this will be done in the layout step
        if(activeVerticalCenters[0] == Float.MAX_VALUE)
            return

        val buttonY = 0.5f * (controlButton.top + controlButton.bottom) + controlButton.translationY

        deleteButton.isPressed = buttonY < deleteButton.bottom + deleteButton.translationY

        // if the button outside the current choice bounds, find a new choice
        if (buttonY > activeVerticalBottom || buttonY <= activeVerticalTop) {
//            Log.v("Notes", "Needing new vertical choice index")
            var distanceY = Float.MAX_VALUE
            var newChoiceIndex = 0

            for (i in choiceButtons.indices) {
                val cCenterY = activeVerticalCenters[i]
                val distanceYC = abs(buttonY - cCenterY)
                if (distanceYC < distanceY) {
                    distanceY = distanceYC
                    newChoiceIndex = i
                }
                // Log.v("Notes", "Box index: $i, distanceXBox=$distanceXBox, buttonCenter=$currentCenterX, boxCenter=$boxCenterX")
            }

            // we got a new choice, update sizes
            if (activeVerticalIndex != newChoiceIndex) {
                activeVerticalIndex = newChoiceIndex

                // call our listener that the note changed
                activeNote?.let {
                    stateChangedListener?.onNoteChanged(it, choiceButtons[activeVerticalIndex].noteID)
                    controlButton.setNoteId(choiceButtons[activeVerticalIndex].noteID)
                }

                for (i in choiceButtons.indices)
                    choiceButtons[i].button.highlightNote(0, i == activeVerticalIndex)

                activeVerticalCenters[0] = Float.MAX_VALUE
                val transition = ChangeBounds().apply {
                    duration = 150L
                    interpolator = OvershootInterpolator()
                }
                TransitionManager.beginDelayedTransition(this, transition)
                requestLayout()
            }
        }
    }


    /// Compute height of delete button.
    /**
     * This function assumes, that the bounding boxes are aligned with the bottom of the sound chooser
     * and the paddingBotton is the same for the soundchooser and the note view.
     */
    private fun computeDeleteButtonHeight(measuredHeight: Int) : Int {
        val freeSpaceAboveBoxes = measuredHeight - boundingBoxesHeight - paddingBottom
        return min(boundingBoxesHeight / 1.5f, freeSpaceAboveBoxes / 4.0f).roundToInt()
    }

    /// Compute height of done button.
    /**
     * This function assumes, that the bounding boxes are aligned with the bottom of the sound chooser
     * and the paddingBotton is the same for the soundchooser and the note view.
     */
    private fun computeDoneButtonHeight(measuredHeight: Int) : Int {
        return computeDeleteButtonHeight(measuredHeight)
    }

    /// Compute height of volume control.
    /**
     * This function assumes, that the bounding boxes are aligned with the bottom of the sound chooser
     * and the paddingBotton is the same for the soundchooser and the note view.
     */
    private fun computeVolumeControlHeight(measuredWidth: Int, measuredHeight: Int) : Int {
        val freeSpace = (measuredHeight
                - boundingBoxesHeight
                - paddingBottom
                - computeDeleteButtonHeight(measuredHeight)
                - computeDoneButtonHeight(measuredHeight)
                - 4 * elementPadding)
        var w = min(freeSpace / 3.0f, boundingBoxesHeight / 3.0f)
        val volumeControlLength = (measuredWidth - 2 * elementPadding).roundToInt()
        w = min(0.2f * volumeControlLength, w)
        return w.roundToInt()
    }

    private fun computeChoiceButtonSpaceWidth(measuredWidth: Int) : Int {
        return (measuredWidth - 2 * elementPadding).roundToInt()
    }

    private fun computeChoiceButtonSpaceHeight(measuredWidth: Int, measuredHeight: Int) : Int {
        return (measuredHeight
                - boundingBoxesHeight
                - paddingBottom
                - computeDeleteButtonHeight(measuredHeight)
                - computeDoneButtonHeight(measuredHeight)
                - computeVolumeControlHeight(measuredWidth, measuredHeight)
                - 4 * elementPadding
                ).toInt()
    }

    private fun computeNumCols(measuredWidth: Int, measuredHeight: Int) : Int {
        val choiceButtonSpaceWidth = computeChoiceButtonSpaceWidth(measuredWidth)
        val choiceButtonSpaceHeight = computeChoiceButtonSpaceHeight(measuredWidth, measuredHeight)
        val aspect = choiceButtonSpaceWidth.toFloat() / choiceButtonSpaceHeight.toFloat()
        var bestColRowAspect = Float.MAX_VALUE
        var bestNumCols = 0
        for(cols in 1 .. getNumAvailableNotes()) {
            val rows = ceil(getNumAvailableNotes().toFloat() / cols.toFloat())
            val colRowAspect = cols.toFloat() / rows
            if(abs(colRowAspect - aspect) < abs(bestColRowAspect - aspect)) {
                bestNumCols = cols
                bestColRowAspect = colRowAspect
            }
        }
//        Log.v("Metronome", "SoundChooser.computeNumCols: bestNumCols = $bestNumCols")
        return bestNumCols
    }

    private fun computeNumRows(numCols: Int) : Int {
        return ceil(getNumAvailableNotes().toFloat() / numCols.toFloat()).toInt()
    }

    private fun computeChoiceButtonSize(measuredWidth: Int, measuredHeight: Int) : Float {
        val numCols = computeNumCols(measuredWidth, measuredHeight)
        val numRows = computeNumRows(numCols)
        val choiceButtonSpaceWidth = computeChoiceButtonSpaceWidth(measuredWidth)
        val choiceButtonSpaceHeight = computeChoiceButtonSpaceHeight(measuredWidth, measuredHeight)
        val s = min(
                (choiceButtonSpaceWidth + choiceButtonSpacing) / numCols.toFloat() - choiceButtonSpacing,
                (choiceButtonSpaceHeight + choiceButtonSpacing) / numRows.toFloat() - choiceButtonSpacing
        )
        return min(s, boundingBoxesHeight.toFloat())
    }

    private val boundingBoxesHeight : Int
        get() {
            var value = 0
            for(box in boundingBoxes)
                value = max(value, box.height())
            return value
        }

    private val boundingBoxesLeft : Int
        get() {
            var value = Int.MAX_VALUE
            for(box in boundingBoxes)
                value = min(value, box.left)
            return value
        }

    private val boundingBoxesRight : Int
        get() {
            var value = 0
            for(box in boundingBoxes)
                value = max(value, box.right)
            return value
        }

}