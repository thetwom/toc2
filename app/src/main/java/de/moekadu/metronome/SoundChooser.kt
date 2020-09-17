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

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.transition.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

class SoundChooser(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    companion object{
        const val CHOICE_BASE = 0
        const val CHOICE_DYNAMIC = 1
        const val CHOICE_STATIC = 2
        const val CHOICE_OFF = 3
        const val TRANSITION_FINISHED = 4
        const val TRANSITION_DEACTIVATING = 5
        const val TRANSITION_ACTIVATING_STATIC = 6
    }

    constructor(context : Context, attrs : AttributeSet? = null) : this(context, attrs, R.attr.soundChooserStyle)

    private val backgroundView = ImageButton(context)
    private val controlButtons = mutableMapOf<NoteListItem, SoundChooserControlButton>() //ArrayList<SoundChooserControlButton>()

    private val choiceButtons = Array(availableNotes.size) {i ->
        NoteView(context).apply {
            val buttonNoteList = NoteList()
            buttonNoteList.add(NoteListItem(i, 0.0f, 0.0f))
            noteList = buttonNoteList
            setBackgroundResource(R.drawable.choice_button_background)
        }
    }

    var choiceStatus = CHOICE_OFF
        private set
    private var runningTransition = TRANSITION_FINISHED

    private val noteViewBoundingBox = Rect()
    private val boundingBox = Rect()
    private var activeBoxLeft = 0
    private var activeBoxRight = 0
    var activeNote : NoteListItem? = null
        private set
    private var activeControlButton: SoundChooserControlButton? = null

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

    private val noteListChangedListener = object: NoteList.NoteListChangedListener {
        override fun onNoteAdded(note: NoteListItem, index: Int) {
            val s = SoundChooserControlButton(context, note, elementElevation + tolerance, volumePaintColor)
            controlButtons[note] = s
            addView(s)
            for (c in controlButtons.values)
                c.visibility = View.GONE
            setActiveNote(note, 200L)
        }

        override fun onNoteRemoved(note: NoteListItem, index: Int) {
            noteList?.let { notes ->
                val s = controlButtons[note]
                controlButtons.remove(note)
                if (s?.visibility == View.VISIBLE)
                    s.animate().alpha(0f).setDuration(200L).withEndAction { removeView(s) }.start()
                else
                    removeView(s)

                val newActiveIndex = if(index < notes.size) index else notes.size - 1
                if (newActiveIndex in notes.indices)
                    setActiveNote(notes[newActiveIndex], 200L)
            }
        }

        override fun onNoteMoved(note: NoteListItem, fromIndex: Int, toIndex: Int) {
//            Log.v("Metronome", "SoundChooser.noteList.onNoteMoved: moving from $fromIndex to $toIndex")
            activeControlButton?.let { setControlButtonTargetTranslation(it) }
            resetActiveNoteBoundingBox()
        }

        override fun onVolumeChanged(note: NoteListItem, index: Int) {
            if (note === activeNote)
                volumeControl.setVolume(note.volume, 200L)
            controlButtons[note]?.volume = note.volume
        }

        override fun onNoteIdChanged(note: NoteListItem, index: Int) {
            controlButtons[note]?.noteId = note.id
        }

        override fun onDurationChanged(note: NoteListItem, index: Int) { }
    }

    private val transitionEndListener = object : Transition.TransitionListener {
        override fun onTransitionEnd(transition: Transition) {
//            Log.v("Metronome", "SoundChooser.deactivate -> onTransitionEnd")
            if (runningTransition == TRANSITION_DEACTIVATING)
                onDeactivateComplete()
            runningTransition = TRANSITION_FINISHED
        }

        override fun onTransitionResume(transition: Transition) { }
        override fun onTransitionPause(transition: Transition) { }
        override fun onTransitionCancel(transition: Transition) { }
        override fun onTransitionStart(transition: Transition) { }
    }

    var noteList: NoteList? = null
        set(value) {
            field?.unregisterNoteListChangedListener(noteListChangedListener)
            field = value
            field?.registerNoteListChangedListener(noteListChangedListener)

            for (n in controlButtons.values)
                removeView(n)
            controlButtons.clear()

            value?.let { notes ->
                for (n in notes) {
                    controlButtons[n] = SoundChooserControlButton(context, n, elementElevation + tolerance, volumePaintColor)
                    addView(controlButtons[n])
                    controlButtons[n]?.visibility = View.GONE
                }
            }
        }

    interface StateChangedListener {
        fun onSoundChooserDeactivated(note : NoteListItem?) {}
    }
    var stateChangedListener : StateChangedListener? = null

    private val deleteButton = androidx.appcompat.widget.AppCompatImageButton(context)
            .apply {
                setImageResource(R.drawable.delete_button_icon)
                setBackgroundResource(R.drawable.delete_button_background)
                imageTintList = ContextCompat.getColorStateList(context, R.color.delete_button_icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

    private val doneButton = androidx.appcompat.widget.AppCompatButton(context)
        .apply {
            text = context.getString(R.string.done)
            setBackgroundResource(R.drawable.done_button_background)
            setTextColor(ContextCompat.getColorStateList(context, R.color.done_button_text))
            visibility = View.GONE
        }

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
            backgroundView.setBackgroundColor(ta.getColor(R.styleable.SoundChooser_backgroundViewColor, Color.WHITE))
            ta.recycle()
        }

        addView(backgroundView)
        backgroundView.alpha = 0.7f
        backgroundView.visibility = View.GONE
        addView(deleteButton)

        deleteButton.visibility = View.GONE
        deleteButton.elevation = elementElevation
        deleteButton.setOnClickListener {
//            Log.v("Notes", "SoundChooser.deleteButton.onClick")
            deleteActiveNoteIfPossible()
        }

        addView(doneButton)
        doneButton.elevation = elementElevation
        doneButton.setOnClickListener {
            deactivate()
        }

        for(c in choiceButtons) {
            c.elevation = elementElevation
            c.volumeColor = volumePaintColor

            addView(c)
            c.visibility = View.GONE
            c.onNoteClickListener = object : NoteView.OnNoteClickListener {
                override fun onDown(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {
//                    Log.v("Notes", "SoundChooser.choiceButton.onDown")
                    return true
                }
                override fun onMove(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {
                    return true
                }
                override fun onUp(event: MotionEvent?, note: NoteListItem?, noteIndex: Int): Boolean {
                    noteList?.let { notes ->
                        val index = notes.indexOf(activeNote)
                        if (index in notes.indices)
                            notes.setNote(index, c.noteList?.get(0)?.id ?: defaultNote)
                    }
                    for(cB in choiceButtons)
                        cB.highlightNote(0, cB === c)
                    return true
                }
            }
        }

        addView(volumeControl)
        volumeControl.elevation = elementElevation
        volumeControl.visibility = View.GONE
        volumeControl.onVolumeChangedListener = object : VolumeControl.OnVolumeChangedListener {
            override fun onVolumeChanged(volume: Float) {
                noteList?.let { notes ->
                    val index = notes.indexOf(activeNote)
                    if (index in notes.indices)
                        notes.setVolume(index, volume)
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

    private fun measureChoiceBase(measuredWidth: Int, measuredHeight: Int) {
        deleteButton.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth - 2 * elementPadding.roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(computeDeleteButtonHeight(), MeasureSpec.EXACTLY)
        )

        /// here we assume that the note view bottom is aligned with the sound chooser bottom, and the padding at the bottom is equal
        backgroundView.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight - noteViewBoundingBox.height() - paddingBottom, MeasureSpec.EXACTLY)
        )

        noteList?.let { notes ->
            for (i in notes.indices) {
                val n = notes[i]
                NoteView.computeBoundingBox(i, notes.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)
//                Log.v("Metronome", "SoundChooser.measureChoiceBase: boundingBox=$boundingBox, controlButtons[n]=${controlButtons[n]}")
                controlButtons[n]?.measure(
                        MeasureSpec.makeMeasureSpec(min(boundingBox.width(), boundingBox.height()), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(boundingBox.height(), MeasureSpec.EXACTLY)
                )
            }
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

        /// here we assume that the note view bottom is aligned with the sound chooser bottom, and the padding at the bottom is equal
        val verticalSpace = (measuredHeight
                - computeDeleteButtonHeight()
                - noteViewBoundingBox.height()
                - 3 * elementPadding
                - paddingBottom
                )
        val largestChoiceSize =  min(activeVerticalRatios[0] * verticalSpace / normalizedSize, noteViewBoundingBox.height().toFloat())

        for(i in choiceButtons.indices) {
            val sizeRatio = activeVerticalRatios[min(
                    activeVerticalRatios.size - 1,
                    abs(i - activeVerticalIndex)
            )]

            val newSizeSpec = MeasureSpec.makeMeasureSpec(
                    (largestChoiceSize * sizeRatio).roundToInt() - choiceButtonSpacing.toInt(),
                    MeasureSpec.EXACTLY
            )
            choiceButtons[i].measure(newSizeSpec, newSizeSpec)
        }
    }

    private fun measureChoiceStatic(measuredWidth : Int, measuredHeight : Int) {
        measureChoiceBase(measuredWidth, measuredHeight)

        doneButton.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth - 2 * elementPadding.roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(computeDoneButtonHeight(), MeasureSpec.EXACTLY)
        )

        volumeControl.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth - 2 * elementPadding.roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(computeVolumeControlHeight(measuredWidth), MeasureSpec.EXACTLY)
        )

        val buttonSize = MeasureSpec.makeMeasureSpec(computeChoiceButtonSize(measuredWidth).toInt(), MeasureSpec.EXACTLY)
        for(c in choiceButtons){
            c.measure(buttonSize, buttonSize)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
//        Log.v("Metronome", "SoundChooser.onLayout")
        when(choiceStatus) {
            CHOICE_BASE -> layoutChoiceBase(l, t)
            CHOICE_DYNAMIC -> layoutChoiceDynamic(l, t)
            CHOICE_STATIC -> layoutChoiceStatic(l, t, r)
        }
    }

    private fun layoutChoiceBase(l: Int, t: Int) {
//        Log.v("Metronome", "SoundChooser.layoutChoiceBase")
        deleteButton.layout(
                elementPadding.toInt(),
                elementPadding.toInt(),
                elementPadding.toInt() + deleteButton.measuredWidth,
                elementPadding.toInt() + deleteButton.measuredHeight)

        backgroundView.layout(0, 0, backgroundView.measuredWidth, backgroundView.measuredHeight)

        noteList?.let { notes ->
            for (i in notes.indices) {
                val n = notes[i]
                NoteView.computeBoundingBox(i, notes.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)
                boundingBox.offset(noteViewBoundingBox.left - l, noteViewBoundingBox.top - t)
                controlButtons[n]?.let { c ->
                    // don't re-layout for base or dynamic case since this would clash with the current translationX/Y
                    if (c.translationXTarget == 0f && c.translationYTarget == 0f) {
                        val cL = (boundingBox.centerX() - 0.5f * c.measuredWidth).toInt()
                        c.layout(cL, boundingBox.top, cL + c.measuredWidth, boundingBox.top + c.measuredHeight)
                    }
                }
            }
        }
    }

    private fun layoutChoiceDynamic(l: Int, t: Int) {
//        Log.v("Metronome", "SoundChooser.layoutChoiceDynamic")
        layoutChoiceBase(l, t)

        var verticalPosition = noteViewBoundingBox.top - t - elementPadding

        for(i in choiceButtons.indices) {
            val v = choiceButtons[i]
            v.layout(0, verticalPosition.toInt() - v.measuredHeight, v.measuredWidth, verticalPosition.toInt())
            activeVerticalCenters[i] = verticalPosition - 0.5f * v.measuredHeight
            if(i == activeVerticalIndex) {
                activeVerticalBottom = (verticalPosition + choiceButtonSpacing).toInt()
                activeVerticalTop = (verticalPosition - v.measuredHeight - choiceButtonSpacing).toInt()
            }

            verticalPosition -= v.measuredHeight + choiceButtonSpacing
        }
    }

    private fun layoutChoiceStatic(l: Int, t: Int, r: Int) {
//        Log.v("Metronome", "SoundChooser.layoutChoiceStatic")
        layoutChoiceBase(l, t)

        var pos = (noteViewBoundingBox.top - t - elementPadding).toInt()

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

        val numCols = computeNumCols(measuredWidth)
        // we assume that all buttons have the same size
        val buttonWidth = choiceButtons[0].measuredWidth

        val choiceButtonsTotalWidth = numCols * buttonWidth + (numCols - 1) * choiceButtonSpacing
        val effectiveWidth = r - l - 2 * elementPadding
        val choiceButtonsLeft = elementPadding + 0.5f * (effectiveWidth - choiceButtonsTotalWidth)

        val numRows = computeNumRows(numCols)
        val buttonHeight = choiceButtons[0].measuredHeight
        val choiceButtonsTotalHeight = numRows * buttonHeight + (numRows - 1) * choiceButtonSpacing
        val choiceButtonSpaceTop = 2 * elementPadding + deleteButton.measuredHeight
        val effectiveHeight = pos - choiceButtonSpaceTop

        val choiceButtonsTop = choiceButtonSpaceTop + 0.5f * (effectiveHeight - choiceButtonsTotalHeight)

        var iCol = 0
        var iRow = 0
        var horizontalPosition = choiceButtonsLeft.toInt()
        var verticalPosition = choiceButtonsTop.toInt()

        for(i in choiceButtons.indices) {
            val v = choiceButtons[i]

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
//        Log.v("Metronome", "SoundChooser.onTouchEvent: translationZ = $translationZ, elevation = $elevation, choiceStatus=$choiceStatus")
        // it messes up to much if we allow input during a transition
        if (runningTransition != TRANSITION_FINISHED)
            return true

        if (event == null)
            return super.onTouchEvent(event)

        val action = event.actionMasked
        val x = event.x
        val y = event.y

        val controlButton = activeControlButton ?: return false

        when(action) {
            MotionEvent.ACTION_DOWN -> {
//                Log.v("Metronome", "SoundChooser.onTouchEvent: ACTION_DOWN, x=$x, y=$y")
                if (choiceStatus == CHOICE_STATIC)
                    return false

                controlButton.eventXOnDown = x
                controlButton.eventYOnDown = y
                controlButton.translationXInit = controlButton.translationX
                controlButton.translationYInit = controlButton.translationY

                val noteViewLeftLocal = noteViewBoundingBox.left - left
                val noteViewRightLocal = noteViewBoundingBox.right - left

                if(choiceStatus != CHOICE_STATIC && y >= noteViewBoundingBox.top - top
                        && x >= noteViewLeftLocal && x <= noteViewRightLocal) {
                    activateBaseLayout()
                    triggerStaticChooserOnUp = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
//                Log.v("Metronome", "SoundChooser.onTouchEvent: ACTION_MOVE, x=$x, y=$y, choiceStatus=$choiceStatus, inActiveBoundingBoxCheck=${inActiveBoundingBoxCheck()}, activeBoxLeft=$activeBoxLeft, activeBoxRight=$activeBoxRight")
                val tX = controlButton.translationXInit + x - controlButton.eventXOnDown
                val tY = controlButton.translationYInit + y - controlButton.eventYOnDown
                if(choiceStatus == CHOICE_DYNAMIC || choiceStatus == CHOICE_BASE) {
                    controlButton.translationX = tX
                    controlButton.translationY = tY

                    if(!inActiveBoundingBoxCheck() && controlButton.width > 0)
                        moveActiveNoteToNewBoundingBoxIfRequired()
                }

                if(choiceStatus == CHOICE_BASE && controlButton.width > 0) {
                    if (tY + 0.5f * (controlButton.top + controlButton.bottom) < noteViewBoundingBox.top - top + 0.3 * noteViewBoundingBox.height())
                        activateDynamicChoices()
                }

                if(choiceStatus == CHOICE_DYNAMIC) {
                    val tXC = tX + controlButton.right + elementPadding
                    for(c in choiceButtons)
                        c.translationX = tXC
                    repositionDynamicChoices()
                }
            }
            MotionEvent.ACTION_UP -> {
                if(triggerStaticChooserOnUp) {
                    activateStaticChoices()
                }
                else {
                    if(deleteButton.isPressed) {
                        activeControlButton?.let { cB ->
                            cB.translationXTarget = 0.5f * (deleteButton.left + deleteButton.right) - 0.5f * (cB.left + cB.right)
                            cB.translationYTarget = 0.5f * (deleteButton.top + deleteButton.bottom) - 0.5f * (cB.top + cB.bottom)
                        }
                        deleteActiveNoteIfPossible()
                    }
                    deactivate()
                }
                return true
            }
        }
        return false
    }

    private fun deleteActiveNoteIfPossible() {
        noteList?.let { notes ->
            if (notes.size <= 1) {
                Toast.makeText(context, context.getString(R.string.cannot_delete_last_note), Toast.LENGTH_LONG).show()
            } else {
                activeNote?.let {notes.remove(it)}
            }
        }
    }

    fun animateNote(note : NoteListItem) {
        controlButtons[note]?.animateAllNotes()
    }

    /// Set bounding boxes of corresponding noteView.
    /**
     * This function takes bounding boxes of a corresponding noteView in absolute coordinates and
     * then locally stores these boxes in local coordinates.
     * @param left Left of bounding box in coordinates of parent view.
     * @param left Left of bounding box in coordinates of parent view.
     * @param right Right of bounding box in coordinates of parent view.
     * @param bottom Bottom of bounding box in coordinates of parent view.
     */
    fun setNoteViewBoundingBox(left: Int, top: Int, right: Int, bottom: Int) {
        if (noteViewBoundingBox.left != left
                || noteViewBoundingBox.top != top
                || noteViewBoundingBox.right != right
                || noteViewBoundingBox.bottom != bottom) {
            noteViewBoundingBox.left = left
            noteViewBoundingBox.top = top
            noteViewBoundingBox.right = right
            noteViewBoundingBox.bottom = bottom
            requestLayout()
        }
    }

    private fun resetActiveNoteBoundingBox() {
        noteList?.let { notes ->
            val index = notes.indexOf(activeNote)
            if (index in notes.indices) {
                NoteView.computeBoundingBox(index, notes.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)
                activeBoxLeft = noteViewBoundingBox.left - left + (boundingBox.left - tolerance).roundToInt()
                activeBoxRight = noteViewBoundingBox.left - left + (boundingBox.right + tolerance).roundToInt()
            }
        }
    }

    /// Set active note which the sound chooser should modify.
    /**
     * @param note Note list item as used in noteView, and which will then be returned in the callbacks. Also
     *   this note is required to set the correct controlButton image.
     * @param animationDuration Animation duration for animating the control button to the right place.
     *   only used if the current status is CHOICE_STATIC.
     */
    fun setActiveNote(note: NoteListItem, animationDuration: Long = 200L) {
        activeNote = note
        activeControlButton = controlButtons[note]
//        Log.v("Metronome", "SoundChooser.setActiveNote: activeControlButton.translationX=${activeControlButton?.translationX}")

        resetActiveNoteBoundingBox()

        volumeControl.setVolume(note.volume, if(choiceStatus == CHOICE_STATIC) animationDuration else 0L)
        for(c in choiceButtons)
            c.highlightNote(0, c.noteList?.get(0)?.id == activeNote?.id)

        if(choiceStatus == CHOICE_STATIC && activeControlButton?.visibility != View.VISIBLE) {
            runningTransition = TRANSITION_ACTIVATING_STATIC
            TransitionManager.beginDelayedTransition(this,
                    AutoTransition().apply {
                        duration = 70L // keep this short to avoid bad user experience when quickly changing notes
                        ordering = TransitionSet.ORDERING_TOGETHER
                        addListener(transitionEndListener)
                    }
            )
            for (cB in controlButtons.values) {
                cB.visibility = if (cB === activeControlButton) View.VISIBLE else View.GONE
            }
        }
    }

    private fun deactivate(animationDuration: Long = 200L) {
//        Log.v("Metronome", "SoundChooser.deactivate")

        activeVerticalCenters[0] = Float.MAX_VALUE
        val autoTransition = AutoTransition().apply {
            duration = animationDuration
            addListener(transitionEndListener)
        }

        deleteButton.isPressed = false

        TransitionManager.beginDelayedTransition(this, autoTransition)
        choiceStatus = CHOICE_OFF
        runningTransition = TRANSITION_DEACTIVATING
        deleteButton.visibility = View.GONE
        doneButton.visibility = View.GONE
        backgroundView.visibility = View.GONE
        volumeControl.visibility = View.GONE
        for (c in choiceButtons)
            c.visibility = View.GONE
        for (cB in controlButtons.values) {
            if (cB.visibility == View.VISIBLE) {
                cB.moveToTarget(animationDuration)
            }
            cB.visibility = View.GONE
        }
    }

    private fun onDeactivateComplete() {
        translationZ = 0f
        stateChangedListener?.onSoundChooserDeactivated(activeNote)
        for ( cB in controlButtons.values) {
            cB.translationX = 0f
            cB.translationY = 0f
            cB.translationXTarget = 0f
            cB.translationYTarget = 0f
        }
        choiceStatus = CHOICE_OFF
    }

    private fun activateBaseLayout(animationDuration: Long = 200L) {
//        Log.v("Metronome", "SoundChooser.activateBaseLayout, choiceStatus=$choiceStatus")
        if (runningTransition != TRANSITION_FINISHED)
            return
        choiceStatus = CHOICE_BASE
        translationZ = activeTranslationZ
        TransitionManager.beginDelayedTransition(this,
                Fade().apply{
                    duration = animationDuration
                    addListener(transitionEndListener) // we need this seems deactivate sometimes doesn't call it
                })
        backgroundView.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE
        doneButton.visibility = View.GONE
        volumeControl.visibility = View.GONE
        for(c in choiceButtons)
            c.visibility = View.GONE
        for (cB in controlButtons.values) {
            cB.visibility = if (cB === activeControlButton) View.VISIBLE else View.GONE
        }
//        Log.v("Metronome", "SoundChooser.activateBaseLayout: activeControlButton=$activeControlButton")
    }

    fun activateStaticChoices(animationDuration : Long = 200L) {
        if (runningTransition != TRANSITION_FINISHED)
            return
        activeVerticalCenters[0] = Float.MAX_VALUE
        choiceStatus = CHOICE_STATIC
        translationZ = activeTranslationZ
        if(animationDuration > 0) {
            runningTransition = TRANSITION_ACTIVATING_STATIC
            val autoTransition = AutoTransition().apply {
                duration = animationDuration
                addListener(transitionEndListener)
            }
            TransitionManager.beginDelayedTransition(this, autoTransition)
        }
        backgroundView.visibility = View.VISIBLE
        doneButton.visibility = View.VISIBLE
        volumeControl.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE

        for(c in choiceButtons) {
            c.visibility = View.VISIBLE
            c.translationX = 0f
            c.highlightNote(0, c.noteList?.get(0)?.id == activeNote?.id)
        }

        for (cB in controlButtons.values)
            cB.visibility = if (cB === activeControlButton) View.VISIBLE else View.GONE
        activeControlButton?.moveToTarget(animationDuration)
    }

    /// This function assumes, that activateBaseLayout() was already called
    private fun activateDynamicChoices(animationDuration: Long = 200L) {
//        Log.v("Metronome", "SoundChooser.activateDynamicChoices, noteViewBoundingBox=$noteViewBoundingBox")
        if (runningTransition != TRANSITION_FINISHED)
            return
        triggerStaticChooserOnUp = false
        choiceStatus = CHOICE_DYNAMIC
        translationZ = activeTranslationZ

        val slideTransition = Slide().apply {
            duration = animationDuration
            slideEdge = Gravity.BOTTOM

            // without this listener the choice buttons can stay mispositioned after the transition ended.
            addListener(object: Transition.TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    transitionEndListener.onTransitionEnd(transition)
                    post {
                        activeControlButton?.let { controlButton ->
                            val tXC = controlButton.translationX + controlButton.right + elementPadding
                            for (c in choiceButtons)
                                c.translationX = tXC
                        }
                    }
                }

                override fun onTransitionResume(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionStart(transition: Transition) {}

            })
        }
        TransitionManager.beginDelayedTransition(this, slideTransition)
        doneButton.visibility = View.GONE
        volumeControl.visibility = View.GONE
        deleteButton.visibility = View.VISIBLE

        for(c in choiceButtons)
            c.visibility = View.VISIBLE
    }

    private fun setControlButtonTargetTranslation(controlButton: SoundChooserControlButton) {
        var note: NoteListItem? = null
        for (cB in controlButtons) {
            if (cB.value === controlButton)
                note = cB.key
        }
        if (note == null)
            return

        val notes = noteList ?: return
        val index = notes.indexOf(note)
        if (index < 0)
            return

        NoteView.computeBoundingBox(index, notes.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)

        controlButton.translationXTarget = (
                noteViewBoundingBox.left - left
                        + boundingBox.left.toFloat()
                        + 0.5f * (boundingBox.width() - min(boundingBox.width(), boundingBox.height()))
                        - controlButton.left
                )
        controlButton.translationYTarget = 0f

        if (controlButton.visibility != View.VISIBLE)
            controlButton.moveToTarget()
    }

    private fun inActiveBoundingBoxCheck() : Boolean {
        activeControlButton?.let { controlButton ->
            val centerX = 0.5f * (controlButton.left + controlButton.right) + controlButton.translationX
            return (centerX >= activeBoxLeft && centerX <= activeBoxRight)
        }
        return false
    }

    private fun moveActiveNoteToNewBoundingBoxIfRequired() {
        val controlButton = activeControlButton ?: return

        noteList?.let { notes ->
            val previousActiveBoxIndex = notes.indexOf(activeNote)
            var newIndex = previousActiveBoxIndex

            val currentCenterX = 0.5f * (controlButton.left + controlButton.right) + controlButton.translationX
            // Log.v("Notes", "Button left: ${button.left}, button right=${button.right}, translationX = ${button.translationX}")
            var distanceX = Float.MAX_VALUE

            for (i in notes.indices) {
                NoteView.computeBoundingBox(i, notes.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)
                val localCenterX = boundingBox.centerX() + noteViewBoundingBox.left - left
                val distanceXBox = abs(currentCenterX - localCenterX)
                if (distanceXBox < distanceX) {
                    distanceX = distanceXBox
                    newIndex = i
                }
                // Log.v("Notes", "Box index: $i, distanceXBox=$distanceXBox, buttonCenter=$currentCenterX, boxCenter=$boxCenterX")
            }

            notes.move(previousActiveBoxIndex, newIndex)
            if (previousActiveBoxIndex != newIndex)
                triggerStaticChooserOnUp = false
        }
    }

    private fun repositionDynamicChoices() {
        // Don't position dynamic choices, if centers are not set, this will be done in the layout step
        if(activeVerticalCenters[0] == Float.MAX_VALUE)
            return
        val controlButton = activeControlButton ?: return

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

                noteList?.let { notes ->
                    val index = notes.indexOf(activeNote)
                    if (index in notes.indices) {
                        notes.setNote(index, choiceButtons[activeVerticalIndex].noteList?.get(0)?.id ?: defaultNote)
                    }
                }

                for (i in choiceButtons.indices)
                    choiceButtons[i].highlightNote(0, i == activeVerticalIndex)

                activeVerticalCenters[0] = Float.MAX_VALUE
                val transition = ChangeBounds().apply {
                    duration = 150L
                    interpolator = OvershootInterpolator()
                    addListener(transitionEndListener)
                }
                TransitionManager.beginDelayedTransition(this, transition)
                requestLayout()
            }
        }
    }

    /// Compute height of delete button.
    private fun computeDeleteButtonHeight() : Int {
//        Log.v("Metronome", "SoundChooser.computeDeleteButtonHeight: noteViewBoundingBox.top=${noteViewBoundingBox.top}, top=$top")
        val freeSpaceAboveBoxes = noteViewBoundingBox.top - top
        return min(noteViewBoundingBox.height() / 1.5f, freeSpaceAboveBoxes / 4.0f).roundToInt()
    }

    /// Compute height of done button.
    private fun computeDoneButtonHeight() : Int {
        return computeDeleteButtonHeight()
    }

    /// Compute height of volume control.
    private fun computeVolumeControlHeight(measuredWidth: Int) : Int {
        val freeSpace = (
                (noteViewBoundingBox.top - top)
                - computeDeleteButtonHeight()
                - computeDoneButtonHeight()
                - 4 * elementPadding)
        var w = min(freeSpace / 3.0f, noteViewBoundingBox.height() / 3.0f)
        val volumeControlLength = (measuredWidth - 2 * elementPadding).roundToInt()
        w = min(0.2f * volumeControlLength, w)
        return w.roundToInt()
    }

    private fun computeChoiceButtonSpaceWidth(measuredWidth: Int) : Int {
        return (measuredWidth - 2 * elementPadding).roundToInt()
    }

    private fun computeChoiceButtonSpaceHeight(measuredWidth: Int) : Int {
        return ((noteViewBoundingBox.top - top)
                - computeDeleteButtonHeight()
                - computeDoneButtonHeight()
                - computeVolumeControlHeight(measuredWidth)
                - 4 * elementPadding
                ).toInt()
    }

    private fun computeNumCols(measuredWidth: Int) : Int {
        val choiceButtonSpaceWidth = computeChoiceButtonSpaceWidth(measuredWidth)
        val choiceButtonSpaceHeight = computeChoiceButtonSpaceHeight(measuredWidth)
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

    private fun computeChoiceButtonSize(measuredWidth: Int) : Float {
        val numCols = computeNumCols(measuredWidth)
        val numRows = computeNumRows(numCols)
        val choiceButtonSpaceWidth = computeChoiceButtonSpaceWidth(measuredWidth)
        val choiceButtonSpaceHeight = computeChoiceButtonSpaceHeight(measuredWidth)
        val s = min(
                (choiceButtonSpaceWidth + choiceButtonSpacing) / numCols.toFloat() - choiceButtonSpacing,
                (choiceButtonSpaceHeight + choiceButtonSpacing) / numRows.toFloat() - choiceButtonSpacing
        )
        return min(s, noteViewBoundingBox.height().toFloat())
    }
}