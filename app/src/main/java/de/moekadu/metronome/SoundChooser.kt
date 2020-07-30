package de.moekadu.metronome

import android.animation.Animator
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import kotlin.math.*

class SoundChooser(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object{
        const val CHOICE_OFF = 0
        const val CHOICE_DYNAMIC = 1
        const val CHOICE_STATIC = 2
    }

    constructor(context : Context, attrs : AttributeSet? = null) : this(context, attrs, R.attr.soundChooserStyle)

    private val backgroundView = ImageButton(context)

    private val controlButton = SoundChooserControlButton(context)
    private val buttonNoteList = NoteList()

    private val choiceButtons = Array(availableNotes.size) {i ->
        SoundChooserChoiceButton(context, i)
    }
    var choiceStatus = CHOICE_OFF
        private set
    private var choicesBottom = 0

    private var boundingBoxes = ArrayList<Rect>(0)
    private var activeBoxIndex = -1
    private var activeBoxLeft = 0
    private var activeBoxRight = 0

    private var activeVerticalIndex = -1
    private var activeVerticalLargestBoxHeight = 0
    private val activeVerticalRatios = floatArrayOf(1f, 0.6f, 0.4f)
    private var activeVerticalTop = 0
    private var activeVerticalBottom = 0

    private val toleranceInDp = 2f
    private val tolerance = toleranceInDp * Resources.getSystem().displayMetrics.density

    private val choiceButtonSpacingInDp = 2f
    private val choiceButtonSpacing = choiceButtonSpacingInDp * Resources.getSystem().displayMetrics.density

    private var volumePaintColor = Color.BLACK
    private var volumePaintStrokeWidth = 10f

    var note : NoteListItem? = null
        private set

    interface StateChangedListener {
        fun onPositionChanged(note : NoteListItem, boxIndex : Int) { }
        fun onNoteDeleted(note : NoteListItem) { }
        fun onNoteChanged(note : NoteListItem, noteID : Int) { }
        fun onSoundChooserDeactivated(note : NoteListItem?) {}
        fun onVolumeChanged(note : NoteListItem, volume : Float) {}
    }
    var stateChangedListener : StateChangedListener? = null

    // reference to animator for final animation, before the sound chooser is deactivated
    private var finalAnimator : Animator ?= null

    private val deleteButton = SoundChooserDeleteButton(context)
    private var minimumDeleteButtonHeight = 20.0f

    private val doneButton = SoundChooserDoneButton(context)

    private var elementElevation = 5.0f
    private var activeElementTranslationZ = 10.0f
    private var activeTranslationZ = 10.0f

    private var elementPadding = 4.0f

    private var triggerStaticChooserOnUp = true

    private val volumeControl = VolumeControl(context)

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.SoundChooser,
                defStyleAttr, R.style.Widget_AppTheme_SoundChooserStyle)

//            Log.v("Metronome", "SoundChooser.init: lineColor: $lc, white: ${Color.WHITE}")
            minimumDeleteButtonHeight = ta.getDimension(R.styleable.SoundChooser_minimumDeleteButtonHeight, minimumDeleteButtonHeight)
            elementElevation = ta.getDimension(R.styleable.SoundChooser_elementElevation, elementElevation)
            activeElementTranslationZ = ta.getDimension(R.styleable.SoundChooser_activeElementTranslationZ, activeElementTranslationZ)
            activeTranslationZ = ta.getDimension(R.styleable.SoundChooser_activeTranslationZ, activeTranslationZ)
            elementPadding = ta.getDimension(R.styleable.SoundChooser_elementPadding, elementPadding)
            volumePaintColor = ta.getColor(R.styleable.SoundChooser_volumeColor, volumePaintColor)
            volumePaintStrokeWidth = ta.getDimension(R.styleable.SoundChooser_volumeStrokeWidth, volumePaintStrokeWidth)
            volumeControl.sliderColor = ta.getColor(R.styleable.SoundChooser_volumeControlSliderColor, volumeControl.sliderColor)
            volumeControl.belowSliderColor = ta.getColor(R.styleable.SoundChooser_volumeControlBelowSliderColor, volumeControl.belowSliderColor)
            volumeControl.iconColor = ta.getColor(R.styleable.SoundChooser_volumeControlOnSliderColor, volumeControl.iconColor)
            volumeControl.backgroundSurfaceColor = ta.getColor(R.styleable.SoundChooser_volumeControlBackgroundColor, volumeControl.backgroundSurfaceColor)
            backgroundView.setBackgroundColor(ta.getColor(R.styleable.SoundChooser_backgroundViewColor, Color.WHITE))
            ta.recycle()
        }

//        controlButton.setBackgroundColor(Color.GREEN)
        controlButton.volumeColor = volumePaintColor
        addView(controlButton)

        addView(deleteButton)
//        deleteButton.setBackgroundResource(R.drawable.delete_button_background)
//        deleteButton.imageTintList = ContextCompat.getColorStateList(context, R.color.delete_button_icon)
        deleteButton.visibility = View.GONE
        deleteButton.setOnClickListener {
            Log.v("Notes", "SoundChooser.deleteButton.onClick")
            note?.let { stateChangedListener?.onNoteDeleted(it) }
            deactivate(true)
        }

        addView(doneButton)
        doneButton.elevation = elementElevation
        doneButton.setOnClickListener {
            deactivate(false)
        }
        controlButton.onClickListener = object : NoteView.OnClickListener {
            var previousX = 0f
            var previousY = 0f
//            val animateX = ObjectAnimator.ofFloat(controlButton, "translationX", 0f, 0f)
//            val animateY = ObjectAnimator.ofFloat(controlButton, "translationY", 0f, 0f)
//            val animateZ = ObjectAnimator.ofFloat(controlButton, "translationZ", 0f, activeElementTranslationZ)

            override fun onDown(event: MotionEvent?, note: NoteListItem?, noteIndex: Int, noteBoundingBoxes: Array<Rect>
            ): Boolean {
                Log.v("Notes", "SoundChooser.controlButton.onDown")
                if(choiceStatus != CHOICE_OFF)
                    return false

                if(event != null) {
                    previousX = event.rawX
                    previousY = event.rawY
                }
                translationZ = activeTranslationZ
                controlButton.elevate(activeTranslationZ)
                return true
            }

            override fun onUp(event: MotionEvent?, note: NoteListItem?, noteIndex: Int, noteBoundingBoxes: Array<Rect>
            ): Boolean {

                if(triggerStaticChooserOnUp) {
                    val box = boundingBoxes[activeBoxIndex]
                    controlButton.animateTo(box.left.toFloat(), box.top.toFloat())
                    activateStaticChoices(300L)

                }
                else {

                    if (deleteButton.isPressed)
                        note?.let { stateChangedListener?.onNoteDeleted(it) }

                    deactivate(deleteButton.isPressed)
                }
                return true
            }

            override fun onMove(event: MotionEvent?, note: NoteListItem?, noteIndex: Int
            ): Boolean {
                if(event != null) {
                    controlButton.translationX += event.rawX - previousX
                    controlButton.translationY += event.rawY - previousY
                    previousX = event.rawX
                    previousY = event.rawY
                    if(!inActiveBoundingBoxCheck())
                        resetActiveBoundingBoxRegion()

                    if(choiceStatus == CHOICE_OFF) {
                        if (controlButton.translationY + 0.5f * controlButton.height < boundingBoxesTop)
                            activateDynamicChoices()
                    }
                    if(choiceStatus == CHOICE_DYNAMIC)
                        repositionDynamicChoices()
                }
                return true
            }

        }

        for(c in choiceButtons) {
            c.button.elevation = elementElevation
            c.button.volumeColor = volumePaintColor

            addView(c.button)
            c.button.visibility = View.GONE
            c.button.onClickListener = object : NoteView.OnClickListener {
                override fun onDown(event: MotionEvent?, note: NoteListItem?, noteIndex: Int, noteBoundingBoxes: Array<Rect>): Boolean {
                    Log.v("Notes", "SoundChooser.choiceButton.onDown")
                    return true
                }
                override fun onMove(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {
                    return true
                }
                override fun onUp(event: MotionEvent?, note: NoteListItem?, noteIndex: Int, noteBoundingBoxes: Array<Rect>): Boolean {

                    this@SoundChooser.note?.let {
                        stateChangedListener?.onNoteChanged(it, c.noteID)
                    }
                    // show new choice in our moving button
                    buttonNoteList.clear()
                    buttonNoteList.addAll(controlButton.noteList)
                    buttonNoteList[0].id = c.noteID
                    controlButton.setNotes(buttonNoteList)
                    controlButton.highlightNote(buttonNoteList[0], true)
                    updateStaticChoices(80L)
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
                buttonNoteList.clear()
                buttonNoteList.addAll(controlButton.noteList)
                buttonNoteList[0].id = note?.id ?: 0
                buttonNoteList[0].volume = volume
                controlButton.setNotes(buttonNoteList)
                controlButton.highlightNote(buttonNoteList[0], true)

                // call our listener that the note changed
                note?.let {
                    stateChangedListener?.onVolumeChanged(it, volume)
                }
            }
        }

        addView(backgroundView)
        backgroundView.visibility = View.GONE
    }

    fun animateNote(note : NoteListItem) {
        controlButton.animateNote(note)
    }

    fun activate(note : NoteListItem, noteIndex : Int, noteBoundingBoxes : Array<Rect>, animationDuration: Long = 300L, forceStatic : Boolean = false) {
        Log.v("Notes", "SoundChooser.activate, translationZ=$translationZ, controlButton.translationZ=${controlButton.translationZ}")
        // make sure that we use no padding, for element distances from the borders, use elementPadding
        require(paddingLeft == 0)
        require(paddingRight == 0)
        require(paddingTop == 0)
        require(paddingBottom == 0)

        triggerStaticChooserOnUp = true
        visibility = View.VISIBLE

        this.note = note

        volumeControl.volume = note.volume

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

        val bb = boundingBoxes[noteIndex]
        controlButton.setSize(bb.width(), bb.height())
        buttonNoteList.clear()
        buttonNoteList.add(note)
        controlButton.setNotes(buttonNoteList, 0L)
        controlButton.highlightNote(buttonNoteList[0], true)

        controlButton.stopAllAnimations()
        controlButton.allAnimationsEndListener = null

        controlButton.translationX = bb.left.toFloat()
        controlButton.translationY = bb.top.toFloat()

        activeBoxIndex = noteIndex
        activeBoxLeft = (bb.left - tolerance).roundToInt()
        activeBoxRight = (bb.right + tolerance).roundToInt()

        Log.v("Notes", "SoundChooser.activate: choiceStatus = $choiceStatus, " +
                "controlButton.translationX = ${controlButton.translationX}, controlButton.translationY = ${controlButton.translationY} " +
                "controlButton.translationZ = ${controlButton.translationZ}, controlButton.elevation = ${controlButton.elevation}"
        )
        Log.v("Notes", "SoundChooser.activate: controlButton.height = ${controlButton.height}, controlButton.width = ${controlButton.width} ")
        Log.v("Notes", "SoundChooser.activate: bb.height = ${bb.height()}, bb.width = ${bb.width()} ")
        Log.v("Metronome", "SoundChooser.activate: choiceStatus = $choiceStatus")
        if(choiceStatus == CHOICE_STATIC) {
            updateStaticChoices(animationDuration / 3)
            return
        }

        val backgroundParams = backgroundView.layoutParams
        backgroundParams.width = width
        backgroundParams.height = boundingBoxesTop
        backgroundView.layoutParams = backgroundParams
        backgroundView.alpha = 0f
        backgroundView.animate().alpha(0.7f).setDuration(animationDuration).start()
        backgroundView.visibility = View.VISIBLE

        deleteButton.isPressed = false
        deleteButton.elevation = elementElevation
        //deleteButton.setSize(width - 2 * elementPadding.roundToInt(), minimumDeleteButtonHeight.roundToInt()) // TODO: should this become bigger if there is space
        deleteButton.setSize(width - 2 * elementPadding.roundToInt(), deleteButtonHeight)
        deleteButton.translationX = elementPadding
        deleteButton.emerge(-minimumDeleteButtonHeight, elementPadding, animationDuration)

        if (forceStatic) {
            translationZ = activeTranslationZ
            controlButton.elevate(activeTranslationZ)
            activateStaticChoices(animationDuration)
        }
    }

    private fun deactivate(isNoteDeleted : Boolean) {
        choiceStatus = CHOICE_OFF
        controlButton.elevate(0f)

        val box = boundingBoxes[activeBoxIndex]
        if (isNoteDeleted) {
            controlButton.vanish()
        } else {
            controlButton.animateTo(box.left.toFloat(), box.top.toFloat())
        }

        controlButton.allAnimationsEndListener = object : SoundChooserControlButton.AllAnimationsEndListener {
            override fun onAnimationEnd() {
                Log.v("Metronome", "SoundChooser.deactivate.controlButton.onAnimationEnd")
                visibility = View.INVISIBLE
                translationZ = 0f

                controlButton.allAnimationsEndListener = null
                controlButton.translationX = 0.0f
                controlButton.translationY = 0.0f
                controlButton.setSize(width, height)
                stateChangedListener?.onSoundChooserDeactivated(note)
                //choiceStatus = CHOICE_OFF
                finalAnimator = null
            }
        }

        for (c in choiceButtons) {
            c.dissolve(200L)
        }
        deleteButton.disappear(
            deleteButton.translationY,
            -deleteButton.height.toFloat(),
            200L
        )

        doneButton.disappear(200L)

        backgroundView.animate()
                .setDuration(300L)
                .setListener(object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(animation: Animator?) { }
                    override fun onAnimationEnd(animation: Animator?) {
                        backgroundView.animate().setListener(null)
                        backgroundView.visibility = View.GONE
                    }
                    override fun onAnimationCancel(animation: Animator?) { }
                    override fun onAnimationStart(animation: Animator?) { }

                })
                .alpha(0f)
                .start()

        volumeControl.animate()
                .setListener(object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(animation: Animator?) { }
                    override fun onAnimationEnd(animation: Animator?) {
                        volumeControl.animate().setListener(null)
                        volumeControl.visibility = View.GONE
                    }
                    override fun onAnimationCancel(animation: Animator?) { }
                    override fun onAnimationStart(animation: Animator?) { }
                })
                .alpha(0f)
                .start()
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

        // Log.v("Notes", "SoundChooser: ... previous index: $previousActiveBoxIndex, new index = $activeBoxIndex")
        if(previousActiveBoxIndex != activeBoxIndex) {
            note?.let {
                stateChangedListener?.onPositionChanged(it, activeBoxIndex)
            }
            triggerStaticChooserOnUp = false
        }
    }

    private fun activateDynamicChoices() {
        triggerStaticChooserOnUp = false
        choiceStatus = CHOICE_DYNAMIC

        // compute size of largest box
        val choicesTop = deleteButtonTop + deleteButton.height + elementPadding
        choicesBottom = Int.MAX_VALUE
        for(box in boundingBoxes)
            choicesBottom = min(choicesBottom, box.top - elementPadding.roundToInt())
        val verticalSpace = choicesBottom - choicesTop
        
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

        activeVerticalLargestBoxHeight =  (min(activeVerticalRatios[0] * verticalSpace / normalizedSize, controlButton.height.toFloat())).roundToInt()
        activeVerticalBottom = -1
        activeVerticalTop = 0
        activeVerticalIndex = -1

        for(c in choiceButtons)
            c.doNotAnimateX = true
        repositionDynamicChoices()
    }

    private fun repositionDynamicChoices() {
        // always update horizontal position to be left of our button
        val choiceLeft = (controlButton.right + controlButton.translationX + elementPadding).roundToInt()
        for(i in choiceButtons.indices) {
            val c = choiceButtons[i]
            c.left = choiceLeft
            // we don't animate the x-translation since this should always be to the right to our moving button
            c.button.translationX = choiceLeft.toFloat()
        }

        val buttonY = 0.5f * (controlButton.top + controlButton.bottom) + controlButton.translationY

        deleteButton.isPressed = buttonY < deleteButton.bottom + deleteButton.translationY

        // if the button outside the current choice bounds, find a new choice
        if (buttonY > activeVerticalBottom || buttonY <= activeVerticalTop) {
            Log.v("Notes", "Needing new vertical choice index")
            var distanceY = Float.MAX_VALUE
            var newChoiceIndex = 0

            for (i in choiceButtons.indices) {
                val c = choiceButtons[i]
                //val cCenterY = 0.5f * (c.top + c.bottom) + c.translationY
                val cCenterY = c.centerY
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

                // show new choice in out moving button
                buttonNoteList.clear()
                buttonNoteList.addAll(controlButton.noteList)
                buttonNoteList[0].id = choiceButtons[activeVerticalIndex].noteID
                controlButton.setNotes(buttonNoteList)
                controlButton.highlightNote(buttonNoteList[0], true)

                // call our listener that the note changed
                note?.let {
                    stateChangedListener?.onNoteChanged(it, choiceButtons[activeVerticalIndex].noteID)
                }

                var currentPosition = choicesBottom.toFloat()

                for (i in choiceButtons.indices) {
                    val sizeRatio = activeVerticalRatios[min(
                        activeVerticalRatios.size - 1,
                        abs(i - activeVerticalIndex)
                    )]
                    val c = choiceButtons[i]

                    c.button.highlightNote(0, i == activeVerticalIndex)

                    var extendedAnimationDuration = 0L
                    if(c.button.visibility != View.VISIBLE) {
                        c.top = (controlButton.translationY + 0.5f * controlButton.height).roundToInt()
                        c.left = (controlButton.translationX + 0.5f * controlButton.width).roundToInt()
                        c.height = 0
                        c.width = 0
                        c.animateToTarget(0L)
                        c.button.visibility = View.VISIBLE
                        extendedAnimationDuration = 200L
                    }

                    val newHeight = (activeVerticalLargestBoxHeight * sizeRatio).roundToInt() - choiceButtonSpacing.toInt()
                    val newWidth = (controlButton.width * newHeight / controlButton.height.toFloat()).toInt()
                    c.top = (currentPosition - newHeight).roundToInt()
                    c.width = newWidth
                    c.height = newHeight

                    currentPosition -= newHeight.toFloat() + choiceButtonSpacing

                    c.animateToTarget(80L + extendedAnimationDuration)
                }

                activeVerticalBottom =
                    (choiceButtons[activeVerticalIndex].top + choiceButtons[activeVerticalIndex].height + choiceButtonSpacing).roundToInt()
                activeVerticalTop =
                    (choiceButtons[activeVerticalIndex].top - choiceButtonSpacing).roundToInt()
            }
        }
    }

    private fun activateStaticChoices(animationDuration: Long) {
        choiceStatus = CHOICE_STATIC

        for(i in choiceButtons.indices) {
            val c = choiceButtons[i]
            c.doNotAnimateX = false
            c.top = (controlButton.translationY + 0.5f * controlButton.height).roundToInt()
            c.left = (controlButton.translationX + 0.5f * controlButton.width).roundToInt()
            c.height = 0
            c.width = 0
            c.animateToTarget(0L)
            c.button.visibility = View.VISIBLE
        }

        doneButton.setSize(0, 0)
        volumeControl.alpha = 0.0f

        updateStaticChoices(animationDuration)
    }

    private fun updateStaticChoices(animationDuration : Long) {
        val choicesTop = deleteButtonHeight + 2 * elementPadding
        val choicesBottom = doneButtonTop - 2 * elementPadding - volumeControlHeight
        val spaceY = choicesBottom - choicesTop
        val cY = 0.5f * (choicesTop + choicesBottom)

        doneButton.setCenter(elementPadding + 0.5f * doneButtonWidth, doneButtonTop + 0.5f * doneButtonHeight)
        doneButton.emerge(doneButtonWidth, doneButtonHeight, animationDuration)

//        val choicesLeft = elementPadding
//        val choicesRight = width - 2 * elementPadding
//        val spaceX = choicesRight - choicesLeft
//        val cX = 0.5f * (choicesLeft + choicesRight)

        // val radOuter = 0.5f * min(spaceX, spaceY)

        //val choiceButtonDiagonal = 2.0f * PI * radOuter / (choiceButtons.size + PI.toFloat())
        //val choiceButtonSize = choiceButtonDiagonal / 2.0f.pow(0.5f)
        //val circleRadius = radOuter - 0.5f * choiceButtonDiagonal
        val buttonSize = choiceButtonSize
        val nC = numCols
        val buttonsTop = choiceButtonsTop
        val buttonsLeft = choiceButtonsLeft
        Log.v("Metronome", "SoundChooser.activateStaticChoices: buttonSize = $buttonSize, buttonsTop = $buttonsTop, buttonsLeft = $buttonsLeft")
        var iCol = 0
        var iRow = 0

//        val dPhi = 2.0f * PI.toFloat() / choiceButtons.size
//        Log.v("Notes", "SoundChooser.activateStaticChoices: radOuter = $radOuter, circleRadius = $circleRadius")

        for(i in choiceButtons.indices) {
//            val buttonAngle = i * dPhi
//
//            val buttonCX = cX + circleRadius * cos(buttonAngle)
//            val buttonCY = cY + circleRadius * sin(buttonAngle)
            val c = choiceButtons[i]

//            var choiceButtonModifiedSize = choiceButtonSize

            if(c.noteID == controlButton.noteList[0].id) {
                c.button.highlightNote(0, true)
            }
            else {
                c.button.highlightNote(0, false)
//                choiceButtonModifiedSize *= 1.0 // 0.85
            }
            c.button.visibility = View.VISIBLE

            c.top = (buttonsTop + iRow * (choiceButtonSpacing + buttonSize) - choiceButtonSpacing).roundToInt()
            c.left = (buttonsLeft + iCol * (choiceButtonSpacing + buttonSize) - choiceButtonSpacing).roundToInt()
            c.width = buttonSize.roundToInt()
            c.height = buttonSize.roundToInt()

            iCol += 1
            if(iCol == nC) {
                iCol = 0
                iRow += 1
            }
//            c.top = (buttonCY - 0.5f * choiceButtonModifiedSize).roundToInt()
//            c.left = (buttonCX - 0.5f * choiceButtonModifiedSize).roundToInt()
//            c.width = choiceButtonModifiedSize.roundToInt()
//            c.height = choiceButtonModifiedSize.roundToInt()
            c.animateToTarget(animationDuration)
        }

        val volumeControlParams = volumeControl.layoutParams
        volumeControlParams.height = volumeControlHeight
        volumeControlParams.width =  volumeControlLength
        volumeControl.layoutParams = volumeControlParams
        volumeControl.translationX = elementPadding
        volumeControl.translationY = doneButtonTop - elementPadding - volumeControlHeight
        volumeControl.vertical = false

        if(volumeControl.alpha == 0f)
            volumeControl.animate().setDuration(animationDuration).alpha(1.0f)
        volumeControl.visibility = View.VISIBLE
    }

    private val boundingBoxesTop : Int
        get() {
            var value = Int.MAX_VALUE
            for(box in boundingBoxes)
                value = min(value, box.top)
            return value
        }

    private val boundingBoxesHeight : Int
        get() {
            var value = 0
            for(box in boundingBoxes)
                value = max(value, box.height())
            return value
        }

    private val deleteButtonHeight : Int
        get() {
            val freeSpaceAboveBoxes = boundingBoxesTop - 2 *  elementPadding
            return min(boundingBoxesHeight / 1.5f, freeSpaceAboveBoxes / 4.0f).roundToInt()
        }

    private val deleteButtonTop
        get() = elementPadding

    private val doneButtonHeight
        get() = deleteButtonHeight

    private val doneButtonWidth
        get() = (width - 2 * elementPadding).roundToInt()

    private val doneButtonTop
        get() = boundingBoxesTop - elementPadding - doneButtonHeight

//    private val volumeControlOrientationVertical : Boolean
//        get() {
//            val freeSpaceWidth = width - 2 * elementPadding
//            val freeSpaceHeight = boundingBoxesTop - elementPadding - 2 * verticalSpacing - deleteButtonHeight
//            return freeSpaceHeight < freeSpaceWidth
//        }

    private val volumeControlHeight : Int
        get() {
            val freeSpace = doneButtonTop - deleteButtonTop - deleteButtonHeight - 2 * elementPadding
            var w = min(freeSpace / 3.0f, boundingBoxesHeight / 3.0f)
            w = min(0.2f * volumeControlLength, w)
            return w.roundToInt()
        }

    private val volumeControlTop
        get() = doneButtonTop - elementPadding - volumeControlHeight

    private val volumeControlLength
        get() = (width - 2 * elementPadding).roundToInt()

    private val choiceButtonSpaceWidth
        get() = (width - 2 * elementPadding).roundToInt()

    private val choiceButtonSpaceHeight
        get() = volumeControlTop - deleteButtonTop - deleteButtonHeight - 2 * elementPadding

    private val choiceButtonSpaceLeft
        get() = elementPadding

    private val choiceButtonSpaceTop
        get() = deleteButtonTop + deleteButtonHeight + elementPadding

    private val numCols : Int
        get() {
            val aspect = choiceButtonSpaceWidth / choiceButtonSpaceHeight
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
            return bestNumCols
        }

    private val numRows : Int
        get() {
            val nC = numCols
//            Log.v("Metronome", "SoundChooser.numRows: nC=$nC, numNotes=${getNumAvailableNotes()}")
            return ceil(getNumAvailableNotes().toFloat() / nC.toFloat()).toInt()
        }

    private val choiceButtonSize : Float
        get() {
            val nR = numRows
            val nC = numCols

            val s = min(
                    (choiceButtonSpaceWidth + choiceButtonSpacing) / nC.toFloat() - choiceButtonSpacing,
                    (choiceButtonSpaceHeight + choiceButtonSpacing) / nR.toFloat() - choiceButtonSpacing
            )
            return min(s, boundingBoxesHeight.toFloat())
        }

    private val choiceButtonsLeft : Float
        get() {
            val nC = numCols
            val buttonSize = choiceButtonSize
            val choiceButtonsTotalWidth = nC * buttonSize + (nC - 1) * choiceButtonSpacing
            return choiceButtonSpaceLeft + 0.5f * (choiceButtonSpaceWidth - choiceButtonsTotalWidth)
        }

    private val choiceButtonsTop : Float
        get() {
            val nR = numRows
            val buttonSize = choiceButtonSize
            val choiceButtonsTotalHeight = nR * buttonSize + (nR - 1) * choiceButtonSpacing
            Log.v("Metronome", "SoundChooser.choiceButtonsTop: choiceButtonSpaceHeight = $choiceButtonSpaceHeight, choiceButtonsTotalHeight = $choiceButtonsTotalHeight, nR = $nR, buttonSize = $buttonSize")
            return choiceButtonSpaceTop + 0.5f * (choiceButtonSpaceHeight - choiceButtonsTotalHeight)
        }
}