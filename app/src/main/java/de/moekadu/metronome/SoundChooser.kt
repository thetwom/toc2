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
import android.util.Log
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
import kotlin.math.*

class SoundChooser(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    constructor(context : Context, attrs : AttributeSet? = null) : this(context, attrs, R.attr.soundChooserStyle)

    private val backgroundView = ImageButton(context)
    private val controlButtons = ArrayList<SoundChooserControlButton>()

    private val choiceButtons = Array(availableNotes.size) {i ->
        NoteView(context).apply {
            val buttonNoteList = ArrayList<NoteListItem>()
            buttonNoteList.add(NoteListItem(i, 0.0f, 0.0f))
            setNoteList(buttonNoteList)
            setBackgroundResource(R.drawable.choice_button_background)
        }
    }

    var choiceStatus = Status.Off
        private set
    private var runningTransition = TransitionStatus.Finished

    private val noteViewBoundingBox = Rect()
    private val boundingBox = Rect()
    private var activeBoxLeft = 0
    private var activeBoxRight = 0
    private var activeControlButton: SoundChooserControlButton? = null
    val activeNoteUid get() = activeControlButton?.uid

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

    private val transitionEndListener = object : Transition.TransitionListener {
        override fun onTransitionEnd(transition: Transition) {
//            Log.v("Metronome", "SoundChooser.deactivate -> onTransitionEnd")
            if (runningTransition == TransitionStatus.Deactivating)
                onDeactivateComplete()
            runningTransition = TransitionStatus.Finished
        }
        override fun onTransitionResume(transition: Transition) { }
        override fun onTransitionPause(transition: Transition) { }
        override fun onTransitionCancel(transition: Transition) { }
        override fun onTransitionStart(transition: Transition) { }
    }

    interface StateChangedListener {
        fun onSoundChooserDeactivated(uid : UId?)
        fun onNoteIdChanged(uid: UId, noteId: Int, status: Status)
        fun onVolumeChanged(uid: UId, volume: Float, status: Status)
        fun onNoteRemoved(uid: UId)
        fun onNoteMoved(uid: UId, toIndex: Int)
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

        for(noteId in choiceButtons.indices) {
            val c = choiceButtons[noteId]
            c.elevation = elementElevation
            c.volumeColor = volumePaintColor

            addView(c)
            c.visibility = View.GONE
            c.onNoteClickListener = object : NoteView.OnNoteClickListener {
                override fun onDown(event: MotionEvent?, uid: UId?, noteIndex: Int): Boolean {
//                    Log.v("Notes", "SoundChooser.choiceButton.onDown")
                    return true
                }
                override fun onMove(event: MotionEvent?, uid: UId?, noteIndex: Int): Boolean {
                    return true
                }
                override fun onUp(event: MotionEvent?, uid: UId?, noteIndex: Int): Boolean {
                    val controlButton = activeControlButton ?: return true
//                    Log.v("Metronome", "SoundChooser: choiceButton id changed")

                    if (controlButton.noteId != noteId) {
                        controlButton.setNoteId(0, noteId)
                        stateChangedListener?.onNoteIdChanged(controlButton.uid, noteId, choiceStatus)
                    }

                    for (cB in choiceButtons)
                        highlightChoiceButton(cB, cB === c)

                    return true
                }
            }
        }

        addView(volumeControl)
        volumeControl.elevation = elementElevation
        volumeControl.visibility = View.GONE
        volumeControl.onVolumeChangedListener = VolumeControl.OnVolumeChangedListener { volume ->
            activeControlButton?.let { controlButton ->
                if (controlButton.volume != volume) {
                    controlButton.setVolume(0, volume)
                    stateChangedListener?.onVolumeChanged(controlButton.uid, volume, choiceStatus)
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        when(choiceStatus) {
            Status.Base-> measureChoiceBase(measuredWidth, measuredHeight)
            Status.Dynamic -> measureChoiceDynamic(measuredWidth, measuredHeight)
            Status.Static -> measureChoiceStatic(measuredWidth, measuredHeight)
            Status.Off -> {}
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

        for (i in controlButtons.indices) {
            NoteView.computeBoundingBox(i, controlButtons.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)
//                Log.v("Metronome", "SoundChooser.measureChoiceBase: boundingBox=$boundingBox, controlButtons[n]=${controlButtons[n]}")
            controlButtons[i].measure(
                    MeasureSpec.makeMeasureSpec(min(boundingBox.width(), boundingBox.height()), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(boundingBox.height(), MeasureSpec.EXACTLY)
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
            Status.Base -> layoutChoiceBase(l, t)
            Status.Dynamic -> layoutChoiceDynamic(l, t)
            Status.Static -> layoutChoiceStatic(l, t, r)
            Status.Off -> {}
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

        for (i in controlButtons.indices) {
            NoteView.computeBoundingBox(i, controlButtons.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)
            boundingBox.offset(noteViewBoundingBox.left - l, noteViewBoundingBox.top - t)
            val c = controlButtons[i]
            // don't re-layout for base or dynamic case since this would clash with the current translationX/Y
            if (c.translationXTarget == 0f && c.translationYTarget == 0f) {
                val cL = (boundingBox.centerX() - 0.5f * c.measuredWidth).toInt()
                c.layout(cL, boundingBox.top, cL + c.measuredWidth, boundingBox.top + c.measuredHeight)
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
        if (runningTransition != TransitionStatus.Finished)
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
                if (choiceStatus == Status.Static)
                    return false

                controlButton.eventXOnDown = x
                controlButton.eventYOnDown = y
                controlButton.translationXInit = controlButton.translationX
                controlButton.translationYInit = controlButton.translationY

                val noteViewLeftLocal = noteViewBoundingBox.left - left
                val noteViewRightLocal = noteViewBoundingBox.right - left

                if(choiceStatus != Status.Static && y >= noteViewBoundingBox.top - top
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
                if(choiceStatus == Status.Dynamic || choiceStatus == Status.Base) {
                    controlButton.translationX = tX
                    controlButton.translationY = tY

                    if(!inActiveBoundingBoxCheck() && controlButton.width > 0)
                        moveActiveNoteToNewBoundingBoxIfRequired()
                }

                if(choiceStatus == Status.Base && controlButton.width > 0) {
                    if (tY + 0.5f * (controlButton.top + controlButton.bottom) < noteViewBoundingBox.top - top + 0.3 * noteViewBoundingBox.height())
                        activateDynamicChoices()
                }

                if(choiceStatus == Status.Dynamic) {
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

    fun setNoteList(noteList: ArrayList<NoteListItem>) {
        var uidActive = activeControlButton?.uid

        val uidEqual = if (noteList.size == controlButtons.size) {
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
//        Log.v("Metronome", "SoundChooser.setNoteList: uidEqual=$uidEqual")
        if (uidEqual) {
            noteList.zip(controlButtons) { source, target ->
                target.set(source)
            }
        } else {
            val map = controlButtons.map {it.uid to it}.toMap().toMutableMap()
            controlButtons.clear()
            for (note in noteList) {
                val n = map.remove(note.uid)
                if (n != null) {
                    n.set(note)
                    controlButtons.add(n)
                } else {
                    val s = SoundChooserControlButton(context, note, elementElevation + tolerance, volumePaintColor)
                    s.visibility = View.GONE
                    controlButtons.add(s)
                    addView(s)
                    uidActive = s.uid
                }
            }
            map.forEach {
                val s = it.value
                if (s.visibility == View.VISIBLE && choiceStatus == Status.Static)
                    s.animate().alpha(0f).setDuration(200L).withEndAction { removeView(s) }.start()
                else
                    removeView(s)
            }

            if (uidActive != null && !controlButtons.any { it.uid == uidActive })
                uidActive = controlButtons.lastOrNull()?.uid

            if (uidActive != activeControlButton?.uid && uidActive != null)
                setActiveControlButton(uidActive, 200L)
            else
                activeControlButton?.let {setControlButtonTargetTranslation(it) }
            updateControlButtonNumbering()
        }

        // volume of active uid
        noteList.firstOrNull { uidActive == it.uid }?.volume?.let { volumeActiveNote ->
            volumeControl.setVolume(volumeActiveNote, if (choiceStatus == Status.Static) 200L else 0L)
        }
    }

    private fun deleteActiveNoteIfPossible() {
        if (controlButtons.size <= 1) {
            Toast.makeText(context, context.getString(R.string.cannot_delete_last_note), Toast.LENGTH_LONG).show()
        } else {
            var index = max(controlButtons.indexOf(activeControlButton), 0)
            val uid = activeControlButton?.uid

            if (controlButtons.remove(activeControlButton)) {
                removeView(activeControlButton)
                // set new active control button
                index = min(index, controlButtons.size - 1)
                setActiveControlButton(controlButtons[index].uid, 200L)
                // notify about change AFTER setting new active control button
                if (uid != null)
                    stateChangedListener?.onNoteRemoved(uid)
            }
            //activeNote?.let {notes.remove(it)}
        }
    }

    fun animateNote(uid: UId) {
        controlButtons.filter { it.uid == uid }.forEach { it.animateAllNotes() }
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
        val index = controlButtons.indexOf(activeControlButton)
        if (index in controlButtons.indices) {
            NoteView.computeBoundingBox(index, controlButtons.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)
            activeBoxLeft = noteViewBoundingBox.left - left + (boundingBox.left - tolerance).roundToInt()
            activeBoxRight = noteViewBoundingBox.left - left + (boundingBox.right + tolerance).roundToInt()
        }
    }

    /// Set active control button which the sound chooser should modify.
    /**
     * @param uid Note uid  which will be returned in the callbacks.
     * @param animationDuration Animation duration for animating the control button to the right place.
     *   only used if the current status is CHOICE_STATIC.
     */
    fun setActiveControlButton(uid: UId, animationDuration: Long = 200L) {
        activeControlButton = controlButtons.firstOrNull { it.uid == uid }
//        Log.v("Metronome", "SoundChooser.setActiveNote: activeControlButton.translationX=${activeControlButton?.translationX}")
        val activeControlButtonLocal = activeControlButton ?: return
        resetActiveNoteBoundingBox()

        volumeControl.setVolume(activeControlButtonLocal.volume, if(choiceStatus == Status.Static) animationDuration else 0L)
        for(i in choiceButtons.indices)
            highlightChoiceButton(choiceButtons[i], i == activeControlButtonLocal.noteId)

        if(choiceStatus == Status.Static && activeControlButton?.visibility != View.VISIBLE) {
            runningTransition = TransitionStatus.ActivatingStatic
            TransitionManager.beginDelayedTransition(this,
                    AutoTransition().apply {
                        duration = 70L // keep this short to avoid bad user experience when quickly changing notes
                        ordering = TransitionSet.ORDERING_TOGETHER
                        addListener(transitionEndListener)
                    }
            )
            for (cB in controlButtons) {
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
        choiceStatus = Status.Off
        runningTransition = TransitionStatus.Deactivating
        deleteButton.visibility = View.GONE
        doneButton.visibility = View.GONE
        backgroundView.visibility = View.GONE
        volumeControl.visibility = View.GONE
        for (c in choiceButtons)
            c.visibility = View.GONE
        for (cB in controlButtons) {
            if (cB.visibility == View.VISIBLE) {
                cB.moveToTarget(animationDuration)
            }
            cB.visibility = View.GONE
        }
    }

    private fun onDeactivateComplete() {
        translationZ = 0f
        stateChangedListener?.onSoundChooserDeactivated(activeControlButton?.uid)
        for ( cB in controlButtons) {
            cB.translationX = 0f
            cB.translationY = 0f
            cB.translationXTarget = 0f
            cB.translationYTarget = 0f
        }
        choiceStatus = Status.Off
    }

    private fun activateBaseLayout(animationDuration: Long = 200L) {
//        Log.v("Metronome", "SoundChooser.activateBaseLayout, choiceStatus=$choiceStatus")
        if (runningTransition != TransitionStatus.Finished)
            return
        choiceStatus = Status.Base
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
        for (cB in controlButtons) {
            cB.visibility = if (cB === activeControlButton) View.VISIBLE else View.GONE
        }
//        Log.v("Metronome", "SoundChooser.activateBaseLayout: activeControlButton=$activeControlButton")
    }

    fun activateStaticChoices(animationDuration : Long = 200L) {
        if (runningTransition != TransitionStatus.Finished)
            return
        activeVerticalCenters[0] = Float.MAX_VALUE
        choiceStatus = Status.Static
        translationZ = activeTranslationZ
        if(animationDuration > 0) {
            runningTransition = TransitionStatus.ActivatingStatic
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

        for(i in choiceButtons.indices) {
            val c = choiceButtons[i]
            c.visibility = View.VISIBLE
            c.translationX = 0f
            highlightChoiceButton(c, i == activeControlButton?.noteId)
        }

        for (cB in controlButtons)
            cB.visibility = if (cB === activeControlButton) View.VISIBLE else View.GONE
        activeControlButton?.moveToTarget(animationDuration)
    }

    /// This function assumes, that activateBaseLayout() was already called
    private fun activateDynamicChoices(animationDuration: Long = 200L) {
//        Log.v("Metronome", "SoundChooser.activateDynamicChoices, noteViewBoundingBox=$noteViewBoundingBox")
        if (runningTransition != TransitionStatus.Finished)
            return
        triggerStaticChooserOnUp = false
        choiceStatus = Status.Dynamic
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
        val index = controlButtons.indexOf(controlButton)
        if (index < 0)
            return

        NoteView.computeBoundingBox(index, controlButtons.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)

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

        val previousActiveBoxIndex = controlButtons.indexOf(controlButton)
        if (previousActiveBoxIndex == -1)
            return  // this actually indicates an error

        var newIndex = previousActiveBoxIndex

        val currentCenterX = 0.5f * (controlButton.left + controlButton.right) + controlButton.translationX
        // Log.v("Notes", "Button left: ${button.left}, button right=${button.right}, translationX = ${button.translationX}")
        var distanceX = Float.MAX_VALUE

        for (i in controlButtons.indices) {
            NoteView.computeBoundingBox(i, controlButtons.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)
            val localCenterX = boundingBox.centerX() + noteViewBoundingBox.left - left
            val distanceXBox = abs(currentCenterX - localCenterX)
            if (distanceXBox < distanceX) {
                distanceX = distanceXBox
                newIndex = i
            }
             // Log.v("Notes", "Box index: $i, distanceXBox=$distanceXBox, buttonCenter=$currentCenterX, boxCenter=$localCenterX")
        }

        controlButtons.remove(controlButton)
        controlButtons.add(newIndex, controlButton)
        updateControlButtonNumbering()
        setControlButtonTargetTranslation(controlButton)

        stateChangedListener?.onNoteMoved(controlButton.uid, newIndex)
        if (previousActiveBoxIndex != newIndex)
            triggerStaticChooserOnUp = false
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

                controlButton.setNoteId(0, activeVerticalIndex)
                stateChangedListener?.onNoteIdChanged(controlButton.uid, activeVerticalIndex, choiceStatus)

                for (i in choiceButtons.indices)
                    highlightChoiceButton(choiceButtons[i], i == activeVerticalIndex)

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

    private fun highlightChoiceButton(button: NoteView, state: Boolean = true) {
        button.highlightNote(0, state)
        if (state)
            button.setBackgroundResource(R.drawable.choice_button_background_active)
        else
            button.setBackgroundResource(R.drawable.choice_button_background)
    }

    private fun updateControlButtonNumbering() {
        for (i in controlButtons.indices)
            controlButtons[i].numberOffset = i
    }

    enum class Status {
        Base, Dynamic, Static, Off
    }
    enum class TransitionStatus {
        Finished, Deactivating, ActivatingStatic
    }
//    companion object{
//        const val TRANSITION_FINISHED = 4
//        const val TRANSITION_DEACTIVATING = 5
//        const val TRANSITION_ACTIVATING_STATIC = 6
//    }
}