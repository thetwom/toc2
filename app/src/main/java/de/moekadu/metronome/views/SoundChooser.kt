/*
 * Copyright 2021 Michael Moessner
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

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.view.setPadding
import de.moekadu.metronome.R
import de.moekadu.metronome.metronomeproperties.*
import de.moekadu.metronome.misc.Utilities
import de.moekadu.metronome.viewmanagers.AnimateView
import de.moekadu.metronome.viewmanagers.DynamicSelection
import de.moekadu.metronome.viewmanagers.GridSelection
import de.moekadu.metronome.viewmanagers.VolumeSliders
import kotlinx.parcelize.Parcelize
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
    val dynamicSelection = Rect()
    val volume = Rect()
    val clearAll = Rect()
    val openVolumeSliders = Rect()

    val volumeSliders = Rect()
    var volumeSlidersButtonSize = 0

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

        dynamicSelection.bottom = (noteView.top + viewSpacing).roundToInt()
        dynamicSelection.top = staticElementPadding

        volumeSliders.bottom = (noteView.top - viewSpacing).roundToInt()
        volumeSliders.top = staticElementPadding
        volumeSlidersButtonSize = (plusButtonHeight / 3f * 2f).roundToInt()

        openVolumeSliders.bottom = (noteView.top - viewSpacing).roundToInt()
        openVolumeSliders.top = openVolumeSliders.bottom - volumeSlidersButtonSize
        clearAll.bottom = openVolumeSliders.bottom
        clearAll.top = openVolumeSliders.top

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

        dynamicSelection.left = 0
        dynamicSelection.right = dynamicSelection.left + plus.width()

        volumeSliders.left = noteView.left
        volumeSliders.right = noteView.right

        openVolumeSliders.left = 0
        openVolumeSliders.right = openVolumeSliders.left + openVolumeSliders.height()

        clearAll.right = width
        clearAll.left = clearAll.right - clearAll.height()
    }

    fun layoutLandscape(width: Int, height: Int) {
        //Log.v("Metronome", "SoundChooser.layoutLandscape: width=$width, height=$height")
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

        dynamicSelection.bottom = (noteView.top + viewSpacing).roundToInt()
        dynamicSelection.top = staticElementPadding

        volumeSliders.bottom = (noteView.top - viewSpacing).roundToInt()
        volumeSliders.top = staticElementPadding
        volumeSlidersButtonSize = (plusButtonHeight / 3f * 2f).roundToInt()

        clearAll.bottom = (noteView.top - viewSpacing + 0.1f * noteView.height()).roundToInt()
        clearAll.top = clearAll.bottom - volumeSlidersButtonSize

        openVolumeSliders.bottom = (clearAll.top - viewSpacing).roundToInt()
        openVolumeSliders.top = openVolumeSliders.bottom - volumeSlidersButtonSize

        // horizontal pass
        plus.right = (width * plusButtonRightPercent).roundToInt()
        plus.left = plus.right - plus.height()

        extraNoteLines.right = width - paddingRight
        extraNoteLines.left = (plus.left - viewSpacing).roundToInt()

        noteView.right = extraNoteLines.left
        noteView.left = paddingLeft

        delete.left = (plus.right + viewSpacing).roundToInt()
        delete.right = delete.left + delete.height()

        done.left = (delete.right + viewSpacing).roundToInt()
        done.right = done.left + done.height()

        // volume width:
        // user the larger value of "ratio 1:5" and tuplets height
        // but max 0.2 * width of view
        val volumeWidth = min(0.2f * width, max(volume.height() / 7.0f, 0.8f * noteDuration.height()))
        volume.left = staticElementPadding
        volume.right = (volume.left + volumeWidth).roundToInt()

        noteSelection.left = (volume.right + viewSpacing).roundToInt()
        // note selection width: max space, but not don't exceed square ratio of single notes
        noteSelection.right = min(width - staticElementPadding,
            noteSelection.left + getNumAvailableNotes() * noteSelection.height())

        noteDuration.left = noteSelection.left
        noteDuration.right = min(noteSelection.right, noteDuration.left + 4 * noteSelection.height())

        tuplets.left = noteDuration.left
        tuplets.right = noteDuration.right

        dynamicSelection.left = 0
        dynamicSelection.right = dynamicSelection.left + plus.width()

        volumeSliders.left = noteView.left
        volumeSliders.right = noteView.right

        clearAll.left = plus.left
        clearAll.right = clearAll.left + clearAll.height()

        openVolumeSliders.left = clearAll.left
        openVolumeSliders.right = openVolumeSliders.left + openVolumeSliders.height()
    }
}

class SoundChooser(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    @Parcelize
    private class SavedState(val status: Status, val uid: UId?, val volumeSlidersFolded: Boolean) :
        Parcelable

    interface StateChangedListener {
        fun changeNoteId(uid: UId, noteId: Int, status: Status)
        fun changeVolume(uid: UId, volume: Float)
        fun changeVolume(index: Int, volume: Float)
        fun changeNoteDuration(uid: UId, duration: NoteDuration)
        fun addNote(note: NoteListItem)
        fun removeNote(uid: UId)
        fun removeAllNotes()
        fun moveNote(uid: UId, toIndex: Int)
    }

    var stateChangedListener: StateChangedListener? = null

    var status = Status.Off
        private set
    private var triggerStaticSoundChooserOnUp = true

    val noteView = NoteView(context).apply {
        showNumbers = true
        translationZ = Utilities.dp2px(1f)
    }
    private var volumeColor = Color.GRAY
    private var noteColor: ColorStateList? = null
    private var noteHighlightColor: ColorStateList? = null

    private var orientation = Orientation.Portrait

    private val plusButton = ImageButton(context).apply {
        setBackgroundResource(R.drawable.plus_button_background)
        setImageResource(R.drawable.ic_add)
        scaleType = ImageView.ScaleType.FIT_CENTER
        translationZ = Utilities.dp2px(5f)
        setPadding(0)
        disableSwipeForClickableButton(this)
    }
    private val plusButtonNoteLines = ImageView(context).apply {
        setImageResource(R.drawable.ic_notelines)
        scaleType = ImageView.ScaleType.FIT_XY
        translationZ = Utilities.dp2px(1f)
    }

    private val clearAllButton = ImageButton(context).apply {
        setBackgroundResource(R.drawable.plus_button_background)
        setImageResource(R.drawable.ic_clear_all_small)
        scaleType = ImageView.ScaleType.FIT_CENTER
        translationZ = Utilities.dp2px(5f)
        setPadding(0)
        disableSwipeForClickableButton(this)
    }

    private val openVolumeSlidersButton = ImageButton(context).apply {
        setBackgroundResource(R.drawable.plus_button_background)
        setImageResource(R.drawable.ic_tune)
        scaleType = ImageView.ScaleType.FIT_CENTER
        translationZ = Utilities.dp2px(5f)
        setPadding(0)
        disableSwipeForClickableButton(this)
    }

    private val deleteButton = ImageButton(context).apply {
        setBackgroundResource(R.drawable.plus_button_background)
        setImageResource(R.drawable.delete_button_icon_small)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(0)
        translationZ = Utilities.dp2px(6f)
        visibility = GONE
        disableSwipeForClickableButton(this)
    }

    private val doneButton = ImageButton(context).apply {
        setBackgroundResource(R.drawable.plus_button_background)
        setImageResource(R.drawable.ic_done_small)
        scaleType = ImageView.ScaleType.FIT_CENTER
        translationZ = Utilities.dp2px(5.9f)
        setPadding(0)
        visibility = GONE
        disableSwipeForClickableButton(this)
    }

    private val volumeControl = VolumeControl(context).apply {
        vertical = true
        visibility = GONE
    }
    private var noteSelection: GridSelection
    private var noteDuration: GridSelection
    private var tuplets: GridSelection

    private var dynamicSelection: DynamicSelection

    private var volumeSliders: VolumeSliders

    private val backgroundSurface = View(context).apply {
        setBackgroundColor(Color.WHITE)
        translationZ = 0f
        visibility = View.GONE
    }

    private val controlButtons = ArrayList<SoundChooserControlButton>()
    private var activeControlButton: SoundChooserControlButton? = null
    private var nextActiveControlButtonUidOnNoteListChange: UId? = null
    private val measureRect = Rect()

    private val staticElementPadding = Utilities.dp2px(8f).roundToInt()
    private val viewSpacing = Utilities.dp2px(8f)

    private var soundChooserViewMeasures: SoundChooserViewMeasures

    constructor(context: Context, attrs: AttributeSet? = null) : this(
        context,
        attrs,
        R.attr.soundChooserStyle
    )

    init {
        var numRows = 1
        var numCols = getNumAvailableNotes()
        var noteViewHeightPercent = 0.2f
        var plusButtonRightPercent = 0.6f

        attrs?.let {
            val ta = context.obtainStyledAttributes(
                attrs, R.styleable.SoundChooser,
                defStyleAttr, R.style.Widget_AppTheme_SoundChooserStyle
            )

            val actionButtonTintList =
                ta.getColorStateList(R.styleable.SoundChooser_actionButtonTintList)
            plusButton.imageTintList = actionButtonTintList
            deleteButton.imageTintList = actionButtonTintList
            doneButton.imageTintList = actionButtonTintList
            clearAllButton.imageTintList = actionButtonTintList
            openVolumeSlidersButton.imageTintList = actionButtonTintList

            backgroundSurface.backgroundTintList =
                ta.getColorStateList(R.styleable.SoundChooser_backgroundViewColor)

            noteColor = ta.getColorStateList(R.styleable.SoundChooser_noteColor)
            noteHighlightColor = ta.getColorStateList(R.styleable.SoundChooser_noteHighlightColor)

            orientation = if (ta.getBoolean(R.styleable.SoundChooser_vertical, true)) {
                Orientation.Portrait
            } else {
                Orientation.Landscape
            }

            numRows = ta.getInteger(R.styleable.SoundChooser_numRows, numRows)
            numCols = ta.getInteger(R.styleable.SoundChooser_numCols, numCols)

            noteViewHeightPercent =
                ta.getFloat(R.styleable.SoundChooser_noteViewHeightPercent, noteViewHeightPercent)
            plusButtonRightPercent = ta.getFloat(
                R.styleable.SoundChooser_plusButtonRightPercent,
                plusButtonRightPercent
            )

//            Log.v("Metronome", "SoundChooser.init: lineColor: $lc, white: ${Color.WHITE}")
//            elementElevation = ta.getDimension(R.styleable.SoundChooser_elementElevation, elementElevation)
//            activeTranslationZ = ta.getDimension(R.styleable.SoundChooser_activeTranslationZ, activeTranslationZ)
//            elementPadding = ta.getDimension(R.styleable.SoundChooser_elementPadding, elementPadding)
            volumeColor = ta.getColor(R.styleable.SoundChooser_volumeColor, Color.GRAY)
            ta.recycle()
        }

        soundChooserViewMeasures = SoundChooserViewMeasures(
            viewSpacing, noteViewHeightPercent, plusButtonRightPercent,
            paddingLeft, paddingTop, paddingTop, paddingRight, staticElementPadding
        )
        noteView.volumeColor = volumeColor
        noteView.noteColor = noteColor
        noteView.noteHighlightColor = noteHighlightColor

        plusButtonNoteLines.imageTintList = noteColor

        addView(backgroundSurface)
        addView(plusButtonNoteLines)
        addView(noteView)
        addView(plusButton)
        addView(deleteButton)
        addView(doneButton)
        addView(clearAllButton)
        addView(openVolumeSlidersButton)

        addView(volumeControl)
        volumeControl.onVolumeChangedListener = VolumeControl.OnVolumeChangedListener { volume ->
            activeControlButton?.let { controlButton ->
                if (controlButton.volume != volume) {
//                    controlButton.setVolume(0, volume)
                    stateChangedListener?.changeVolume(controlButton.uid, volume)
                }
            }
        }

        noteSelection = GridSelection(
            numRows, numCols, Utilities.dp2px(2f).roundToInt(),
            R.drawable.grid_background_topleft_withlines,
            R.drawable.grid_background_topright_withlines,
            R.drawable.grid_background_bottomleft_withlines,
            R.drawable.grid_background_bottomright_withlines,
            R.drawable.grid_background_left_withlines,
            R.drawable.grid_background_right_withlines,
            R.drawable.grid_background_center_withlines,
            noteColor
        )
        noteSelection.activeButtonChangedListener =
            GridSelection.ActiveButtonChangedListener { index ->
                activeControlButton?.let { controlButton ->
                    if (controlButton.noteId != index && index < getNumAvailableNotes()) {
                        //                        controlButton.setNoteId(0, index)
                        stateChangedListener?.changeNoteId(controlButton.uid, index, status)
                    }
                }
            }

        noteSelection.addView(this)
        noteSelection.disappear(0L)
        for (i in getNumAvailableNotes() until noteSelection.size)
            noteSelection.deactivateButton(i)

        noteDuration = GridSelection(
            1, 3, Utilities.dp2px(2f).roundToInt(),
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
        noteDuration.disappear(0L)
        noteDuration.setButtonDrawable(0, R.drawable.ic_note_duration_quarter)
        noteDuration.setButtonDrawable(1, R.drawable.ic_note_duration_eighth)
        noteDuration.setButtonDrawable(2, R.drawable.ic_note_duration_sixteenth)

        tuplets = GridSelection(
            1, 3, Utilities.dp2px(2f).roundToInt(),
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
        tuplets.disappear(0L)
        tuplets.setButtonDrawable(0, R.drawable.ic_note_duration_normal)
        tuplets.setButtonDrawable(1, R.drawable.ic_note_duration_triplet)
        tuplets.setButtonDrawable(2, R.drawable.ic_note_duration_quintuplet)

        noteDuration.activeButtonChangedListener =
            GridSelection.ActiveButtonChangedListener { index ->
                activeControlButton?.let { controlButton ->
                    val noteDuration = getSelectedNoteDuration(index, tuplets.activeButtonIndex)
//                    Log.v(
//                        "Metronome",
//                        "SoundChooser: noteDuration.onActiveButtonChanged: duration=$noteDuration, controlButton.duration=${controlButton.noteDuration}"
//                    )
                    if (controlButton.noteDuration != noteDuration)
                        stateChangedListener?.changeNoteDuration(controlButton.uid, noteDuration)
                }
            }
        tuplets.activeButtonChangedListener =
            GridSelection.ActiveButtonChangedListener { index ->
                activeControlButton?.let { controlButton ->
                    val noteDuration =
                        getSelectedNoteDuration(noteDuration.activeButtonIndex, index)
                    if (controlButton.noteDuration != noteDuration)
                        stateChangedListener?.changeNoteDuration(controlButton.uid, noteDuration)
                }
            }

        dynamicSelection = DynamicSelection(
            Utilities.dp2px(3f).roundToInt(),
            R.drawable.dynamic_selection_background_withlines,
            noteColor
        )
        dynamicSelection.addView(this, getNumAvailableNotes())

        volumeSliders = VolumeSliders(context)
        volumeSliders.addButtons(this)
        volumeSliders.volumeChangedListener = object : VolumeSliders.VolumeChangedListener {
            override fun onVolumeChanged(index: Int, volume: Float) {
                stateChangedListener?.changeVolume(index, volume)
            }

            override fun fold() {
                volumeSliders.fold(animateLong)
                AnimateView.emerge(openVolumeSlidersButton, animateLong)
                hideBackground(animateLong)
            }

//            override fun unfold() {
//                hideSoundChooser(animateLong)
//                volumeSliders.unfold(animateLong)
//                showBackground(animateLong)
//            }
        }

        openVolumeSlidersButton.setOnClickListener {
            hideSoundChooser(animateLong)
            volumeSliders.unfold(animateLong)
            AnimateView.hide(openVolumeSlidersButton, animateLong)
            showBackground(animateLong)
        }

        plusButton.setOnClickListener {
            val newNote = controlButtons.lastOrNull()?.note?.clone()?.apply { uid = UId.create() }
                ?: NoteListItem(defaultNote, 1.0f, NoteDuration.Quarter)
            if (status == Status.Static)
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
            hideSoundChooser(animateLong)
            hideBackground(animateLong)
        }
        clearAllButton.setOnClickListener {
            stateChangedListener?.removeAllNotes()
        }

        backgroundSurface.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    volumeSliders.fold(animateLong)
                    AnimateView.emerge(openVolumeSlidersButton, animateLong)
                    hideBackground(animateLong)
                    hideSoundChooser(animateLong)
                    performClick()
                    true
                }
                MotionEvent.ACTION_DOWN -> {
                    event.y < noteView.top || event.x > noteView.right // don't capture inside note view
                }
                else -> {
                    false
                }
            }
        }

        setNoteSelectionNotes(NoteDuration.Quarter)
    }

    fun animateNote(uid: UId) {
        noteView.animateNote(uid)
        if (activeControlButton?.visibility == VISIBLE && activeControlButton?.uid == uid)
            activeControlButton?.animateAllNotes()
        //controlButtons.firstOrNull {it.uid == uid}?.animateAllNotes()
    }

    fun setAdvanceMarker(uid: UId?) {
        noteView.setAdvanceMarker(uid)
    }

    private fun measureView(view: View, rect: Rect) {
//        Log.v("Metronome", "SoundChooser.measureView: $rect")
        view.measure(
            MeasureSpec.makeMeasureSpec(rect.width(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(rect.height(), MeasureSpec.EXACTLY)
        )
    }

    private fun layoutView(view: View, rect: Rect) {
//        Log.v("Metronome", "SoundChooser.layoutView: $rect")
        view.layout(rect.left, rect.top, rect.right, rect.bottom)
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable("super state", super.onSaveInstanceState())

        val state = SavedState(status, activeControlButton?.uid, volumeSliders.folded)
        bundle.putParcelable("sound chooser state", state)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {

        val superState = if (state is Bundle) {
            state.getParcelable<SavedState>("sound chooser state")?.let { soundChooserState ->
                if (soundChooserState.status == Status.Static) {
                    soundChooserState.uid?.let { uid ->
                        //nextActiveControlButtonUidOnNoteListChange = uid
                        post {
                            controlButtons.firstOrNull { it.uid == uid }?.let { button ->
                                showStaticChooser(0L)
                                setActiveControlButton(button, 0L)
                            }
                        }
                    }
                }
                if (!soundChooserState.volumeSlidersFolded) {
                    volumeSliders.unfold(0L)
                    showBackground(0L)
                }
            }
            state.getParcelable("super state")
        } else {
            state
        }
        super.onRestoreInstanceState(superState)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        Log.v("Metronome", "SoundChooser.onMeasure")
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        backgroundSurface.measure(widthMeasureSpec, heightMeasureSpec)

        when (orientation) {
            Orientation.Portrait -> soundChooserViewMeasures.layoutPortrait(
                measuredWidth,
                measuredHeight
            )
            Orientation.Landscape -> soundChooserViewMeasures.layoutLandscape(
                measuredWidth,
                measuredHeight
            )
        }

        measureView(noteView, soundChooserViewMeasures.noteView)
        measureView(volumeControl, soundChooserViewMeasures.volume)
        measureView(plusButtonNoteLines, soundChooserViewMeasures.extraNoteLines)
        measureView(plusButton, soundChooserViewMeasures.plus)
        measureView(deleteButton, soundChooserViewMeasures.delete)
        measureView(doneButton, soundChooserViewMeasures.done)
        measureView(clearAllButton, soundChooserViewMeasures.clearAll)
        measureView(openVolumeSlidersButton, soundChooserViewMeasures.openVolumeSliders)

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
        dynamicSelection.measure(
            soundChooserViewMeasures.dynamicSelection.width(),
            soundChooserViewMeasures.dynamicSelection.height()
        )
        volumeSliders.measure(
            soundChooserViewMeasures.noteView.width(),
            soundChooserViewMeasures.noteView.height(),
            soundChooserViewMeasures.volumeSliders.height(),
            soundChooserViewMeasures.volumeSlidersButtonSize,
            soundChooserViewMeasures.plus.height()
        )

        val noteViewWidth = soundChooserViewMeasures.noteView.width()
        val noteViewHeight = soundChooserViewMeasures.noteView.height()
        controlButtons.forEachIndexed { index, button ->
            NoteView.computeBoundingBox(
                index,
                controlButtons.size,
                noteViewWidth,
                noteViewHeight,
                measureRect
            )
            button.measure(
                MeasureSpec.makeMeasureSpec(
                    min(measureRect.width(), measureRect.height()),
                    MeasureSpec.EXACTLY
                ),
                MeasureSpec.makeMeasureSpec(
                    (NoteView.NOTE_IMAGE_HEIGHT_SCALING * measureRect.height()).roundToInt(),
                    MeasureSpec.EXACTLY
                )
            )
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
//        Log.v("Metronome", "SoundChooser.onLayout")

        if (changed) {
            backgroundSurface.layout(
                0, 0, backgroundSurface.measuredWidth, backgroundSurface.measuredHeight
            )
        }

        layoutView(noteView, soundChooserViewMeasures.noteView)
        layoutView(volumeControl, soundChooserViewMeasures.volume)
        if (changed) {
            layoutView(plusButtonNoteLines, soundChooserViewMeasures.extraNoteLines)
            layoutView(plusButton, soundChooserViewMeasures.plus)

            // position of the following elements on plus button ... will change by using translation later
            layoutView(deleteButton, soundChooserViewMeasures.plus)
            layoutView(doneButton, soundChooserViewMeasures.plus)

            layoutView(clearAllButton, soundChooserViewMeasures.clearAll)
            layoutView(openVolumeSlidersButton, soundChooserViewMeasures.openVolumeSliders)

            noteSelection.layout(
                soundChooserViewMeasures.noteSelection.left,
                soundChooserViewMeasures.noteSelection.top
            )
            noteDuration.layout(
                soundChooserViewMeasures.noteDuration.left,
                soundChooserViewMeasures.noteDuration.top
            )
            tuplets.layout(
                soundChooserViewMeasures.tuplets.left,
                soundChooserViewMeasures.tuplets.top
            )
            dynamicSelection.layout(
                soundChooserViewMeasures.dynamicSelection.left,
                soundChooserViewMeasures.dynamicSelection.bottom
            )
        }
        volumeSliders.layout(
            soundChooserViewMeasures.volumeSliders.left,
            soundChooserViewMeasures.volumeSliders.bottom,
            soundChooserViewMeasures.noteView.width(),
            soundChooserViewMeasures.noteView.height(),
            soundChooserViewMeasures.plus.left,
            orientation
        )

        controlButtons.forEach { button ->
            button.layout(0, 0, button.measuredWidth, button.measuredHeight)
        }

        setControlButtonTargetTranslations(
            soundChooserViewMeasures.noteView.left,
            (soundChooserViewMeasures.noteView.bottom - NoteView.NOTE_IMAGE_HEIGHT_SCALING * soundChooserViewMeasures.noteView.height()).roundToInt()
        )

        controlButtons.forEach { button ->
            //if (button.visibility != VISIBLE || !button.isBeingDragged)
            if (button.visibility != VISIBLE) {
//                Log.v("Metronome", "SoundChooser.onLayout: button.moveToTarget2")
                button.moveToTarget2(0L)
            }
//            if (!button.isBeingDragged) {
//                Log.v("Metronome", "SoundChooser.onLayout: button.moveToTarget")
//                //button.moveToTarget(0L)
//                button.moveToTarget2(0L)
//            }
        }

        if (status == Status.Static && changed) {
            deleteButton.translationX =
                (soundChooserViewMeasures.delete.left - soundChooserViewMeasures.plus.left).toFloat()
            deleteButton.translationY =
                (soundChooserViewMeasures.delete.top - soundChooserViewMeasures.plus.top).toFloat()
            doneButton.translationX =
                (soundChooserViewMeasures.done.left - soundChooserViewMeasures.plus.left).toFloat()
            doneButton.translationY =
                (soundChooserViewMeasures.done.top - soundChooserViewMeasures.plus.top).toFloat()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null)
            return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val previousActiveButton = activeControlButton
                var newActiveButton: SoundChooserControlButton? = null
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
                        showMoveOnlyState(animateLong)
                    return true
                } else if (newActiveButton != null && newActiveButton == previousActiveButton) {
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

                    if (status == Status.Dynamic) {
                        updateDynamicChooser(animateShort)
                        triggerStaticSoundChooserOnUp = false
                    } else if (status != Status.Static && button.centerY < noteView.top + 0.2f * noteView.height) {
                        showDynamicChooser(animateLong)
                        triggerStaticSoundChooserOnUp = false
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
//                    Log.v("Metronome", "SoundChooser.onTouchEvent: activeControlbutton.moveToTarget at triggerStaticSoundChooserUp")
                    //activeControlButton?.moveToTarget(animateLong)
                    activeControlButton?.moveToTarget2(animateLong)
                    showStaticChooser(animateLong)
                    triggerStaticSoundChooserOnUp = false
                } else if (status != Status.Static) {
                    // activeControlButton?.stopDragging()
                    hideSoundChooser(animateLong)
                    if (volumeSliders.folded)
                        hideBackground(animateLong)
                } else {
//                    Log.v("Metronome", "SoundChooser.onTouchEvent: activeControlbutton.moveToTarget at else")
//                    activeControlButton?.moveToTarget(animateLong)
                    activeControlButton?.moveToTarget2(animateLong)
                }
                return true
            }
        }
        return false
    }

    fun showMoveOnlyState(animationDuration: Long) {
        if (status == Status.MoveNote)
            return
//        Log.v("Metronome", "SoundChooser.showMoveOnlyState")
        status = Status.MoveNote

        if (deleteButton.visibility != VISIBLE)
            deleteButton.alpha = 0f
        deleteButton.animate()
            .setDuration(animationDuration)
            .translationX(0f)
            .translationY(0f)
            .alpha(1f)
            .withStartAction {
                deleteButton.visibility = VISIBLE
            }
            .withEndAction { }
    }

    fun showBackground(animationDuration: Long) {
//        Log.v("Metronome", "SoundChooser.showBackground")
        val alphaEnd = 0.8f
        translationZ = Utilities.dp2px(6f)
        AnimateView.emerge(backgroundSurface, animationDuration, alphaEnd, name = "backgroundSurface")
        AnimateView.hide(clearAllButton, animationDuration, name = "clearAllButton")
    }

    fun hideBackground(animationDuration: Long) {
//        Log.v(
//            "Metronome",
//            "SoundChooser.hideBackground (d=$animationDuration, bS.visibility=${backgroundSurface.visibility} VISIBLE=$VISIBLE)"
//        )
        AnimateView.emerge(clearAllButton, animationDuration, name = "clearAllButton")
        val animationDurationCorrected =
            if (backgroundSurface.visibility == VISIBLE) animationDuration else 0L
//        Log.v("Metronome", "SoundChooser.hideBackground: backgroundSurface")
        backgroundSurface.animate()
            .setDuration(animationDurationCorrected)
            .alpha(0f)
            .withEndAction {
//                Log.v(
//                    "Metronome",
//                    "SoundChooser.hideBackground: backgroundSurface.ENDACTION (d=$animationDuration)"
//                )
                backgroundSurface.visibility = GONE
                translationZ = 0f // background also defines the viewgroup translation
            }
    }

    private fun showDynamicChooser(animationDuration: Long) {
        if (status == Status.Dynamic)
            return
//        Log.v("Metronome", "SoundChooser.showDynamicSoundChooser")
        status = Status.Dynamic
        showBackground(animationDuration)
        dynamicSelection.setActiveButton(0, 0L)

        activeControlButton?.let {
            dynamicSelection.translationX = it.x + it.width + viewSpacing
            if (it.noteId != 0)
                stateChangedListener?.changeNoteId(it.uid, 0, status)
        }
        dynamicSelection.emerge(animationDuration)
        AnimateView.hide(openVolumeSlidersButton, animationDuration)
        //volumeSliders.hideOpenButton(animationDuration)
    }

    private fun updateDynamicChooser(animationDuration: Long) {
//        Log.v("Metronome", "SoundChooser.updateDynamicSoundChooser")
        activeControlButton?.let { button ->
            dynamicSelection.translationX = button.x + button.width + viewSpacing
            val dynamicIndex = dynamicSelection.getButtonIndex(button.centerY)
            if (dynamicIndex != -1 && dynamicIndex != dynamicSelection.activeButtonIndex)
                dynamicSelection.setActiveButton(dynamicIndex, animationDuration)

            if (button.noteId != dynamicIndex)
                stateChangedListener?.changeNoteId(button.uid, dynamicIndex, status)
        }
    }

    fun showStaticChooser(animationDuration: Long) {
        if (status == Status.Static)
            return
//        Log.v("Metronome", "SoundChooser.showStaticSoundChooser")
        status = Status.Static
        volumeSliders.fold(animateLong)
        showBackground(animateLong)

        val deleteButtonTranslationX: Float =
            (soundChooserViewMeasures.delete.left - soundChooserViewMeasures.plus.left).toFloat()
        val deleteButtonTranslationY: Float =
            (soundChooserViewMeasures.delete.top - soundChooserViewMeasures.plus.top).toFloat()
        val doneButtonTranslationX: Float =
            (soundChooserViewMeasures.done.left - soundChooserViewMeasures.plus.left).toFloat()
        val doneButtonTranslationY: Float =
            (soundChooserViewMeasures.done.top - soundChooserViewMeasures.plus.top).toFloat()

        if (doneButton.visibility != VISIBLE)
            doneButton.alpha = 0f
        if (deleteButton.visibility != VISIBLE)
            deleteButton.alpha = 0f
        if (volumeControl.visibility != VISIBLE)
            volumeControl.alpha = 0f

        doneButton.animate()
            .setDuration(animationDuration)
            .translationX(doneButtonTranslationX)
            .translationY(doneButtonTranslationY)
            .alpha(1.0f)
            .withStartAction { doneButton.visibility = VISIBLE }
            .withEndAction {  }

        deleteButton.animate()
            .setDuration(animationDuration)
            .translationX(deleteButtonTranslationX)
            .translationY(deleteButtonTranslationY)
            .alpha(1.0f)
            .withStartAction { deleteButton.visibility = VISIBLE }
            .withEndAction {  }

        volumeControl.animate()
            .setDuration(animationDuration)
            .alpha(1.0f)
            .withStartAction { volumeControl.visibility = VISIBLE }
            .withEndAction {  }

        AnimateView.hide(openVolumeSlidersButton, animationDuration, name = "openVolumeSlidersButton")
        //volumeSliders.hideOpenButton(animationDuration)
        noteSelection.emerge(animationDuration)
        noteDuration.emerge(animationDuration)
        tuplets.emerge(animationDuration)
    }

    fun hideSoundChooser(animationDuration: Long) {
        if (status == Status.Off)
            return
//        Log.v("Metronome", "SoundChooser.hideSoundChooser")
        status = Status.Off

        val animationDurationDone = if (doneButton.visibility == VISIBLE) animationDuration else 0L
        doneButton.animate()
            .setDuration(animationDurationDone)
            .alpha(0f)
            .withEndAction {
                doneButton.translationX = 0f
                doneButton.translationY = 0f
                doneButton.visibility = GONE
            }

        val animationDurationDelete = if (deleteButton.visibility == VISIBLE) animationDuration else 0L
        deleteButton.animate()
            .setDuration(animationDurationDelete)
            .alpha(0f)
            .withEndAction {
                deleteButton.translationX = 0f
                deleteButton.translationY = 0f
                deleteButton.visibility = GONE
            }

        val animationDurationVolumeControl = if (volumeControl.visibility == VISIBLE) animationDuration else 0L
        volumeControl.animate()
            .setDuration(animationDurationVolumeControl)
            .alpha(0f)
            .withEndAction {
                deleteButton.visibility = GONE
            }

        noteSelection.disappear(animationDuration)
        noteDuration.disappear(animationDuration)
        tuplets.disappear(animationDuration)
        dynamicSelection.disappear(animationDuration)
        if (volumeSliders.folded) {
            AnimateView.emerge(openVolumeSlidersButton, animationDuration, name = "openVolumeSlidersButton")
            //volumeSliders.showOpenButton(animationDuration)
        }
        setActiveControlButton(null, animationDuration)
    }

    fun setNoteList(noteList: ArrayList<NoteListItem>, animationDuration: Long) {
//        Log.v("Metronome", "SoundChooser.setNoteList: noteList[0] = ${noteList[0].duration}")
        noteView.setNoteList(noteList, animationDuration)
        volumeSliders.setNoteList(this, noteList, animationDuration)
//        Log.v("Metronome", "SoundChooser.setNoteList: uid=${activeControlButton?.uid}, activeControlButton.visibility==VISIBLE=${activeControlButton?.visibility == VISIBLE}, activeControlButton.alpha=${activeControlButton?.alpha}, VISIBLE=$VISIBLE, GONE=$GONE, INVISIBLE=$INVISIBLE")
        val noteDurationBefore = activeControlButton?.noteDuration

        // update control buttons if uid did not change
        if (areNoteListUidEqualToControlButtonUid(noteList)) {
            noteList.zip(controlButtons) { source, target ->
                target.set(source)
            }
        } else { // reorder renew, ... control button list
            val map = controlButtons.associateBy { it.uid }.toMutableMap()
            controlButtons.clear()
            for (note in noteList) {
                val n = map.remove(note.uid)
                if (n != null) {
                    n.set(note)
                    controlButtons.add(n)
                } else {
                    val s = SoundChooserControlButton(
                        context,
                        note,
                        volumeColor,
                        noteColor,
                        noteColor //  noteHighlightColor
                    ).apply { showTuplets = false }
                    s.translationZ = Utilities.dp2px(8f)
                    //s.visibility = View.GONE
                    controlButtons.add(s)
                    addView(s)
                }
            }
            // remove remaining control buttons
            map.forEach {
                val s = it.value
                if (s.visibility == VISIBLE && animationDuration > 0L && s.moveToTargetOnDelete) {
                    val translationXTarget =
                        deleteButton.x + 0.5f * (deleteButton.width - s.width) - s.left
                    val translationYTarget =
                        deleteButton.y + 0.5f * (deleteButton.height - s.height) - s.top
                    s.setTargetTranslation(translationXTarget, translationYTarget)
//                    Log.v("Metronome", "SoundChooser.setNoteList: s.moveToTarget and remove")
                    //s.moveToTarget(animationDuration, true) { removeView(s) }
                    s.moveToTarget2AndDisappear(animationDuration) {removeView(s)}
                } else {
                    removeView(s)
                }
                // if note next active control button is set and active note is removed, set the last control button as active
                if (s === activeControlButton && nextActiveControlButtonUidOnNoteListChange == null)
                    nextActiveControlButtonUidOnNoteListChange = controlButtons.lastOrNull()?.uid

            }

            controlButtons.forEachIndexed { index, button -> button.numberOffset = index }
            setControlButtonTargetTranslations(
                noteView.x.roundToInt(),
                (noteView.y + (1.0 - NoteView.NOTE_IMAGE_HEIGHT_SCALING) * noteView.height).roundToInt()
            )
            //controlButtons.forEach { it.moveToTarget(0L) }
            //requestLayout()
        }

        if (status == Status.Static && nextActiveControlButtonUidOnNoteListChange != null) {
            controlButtons.find { it.uid == nextActiveControlButtonUidOnNoteListChange }?.let {
                setActiveControlButton(it, 0L)
            }
        }
        nextActiveControlButtonUidOnNoteListChange = null

        activeControlButton?.noteDuration?.let { noteDuration ->
            if (noteDuration != noteDurationBefore)
                setNoteSelectionNotes(noteDuration)
        }
    }

    private fun setActiveControlButton(
        button: SoundChooserControlButton?,
        animationDuration: Long
    ) {
        if (activeControlButton === button)
            return
        activeControlButton = button

        controlButtons.forEach {
            noteView.setNoteAlpha(it.uid, if (it.uid == button?.uid) 0.3f else 1f)

            if (it === button) {
                //AnimateView.emerge(it, animationDuration)
                it.emerge(animationDuration)
            } else if (!(it === button) && it.visibility == VISIBLE) {
//                Log.v("Metronome", "SoundChooser.setActiveControlButton: it.moveToTarget and fade")
                //it.moveToTarget(animationDuration, true) { it.visibility = GONE }
                it.moveToTarget2AndDisappear(animationDuration)
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
        if (activeControlButton?.uid == uid && status == Status.Static) {
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
            val translationXTarget =
                measureRect.centerX() - 0.5f * button.measuredWidth + noteViewLeft
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
            val centerDist =
                (button.translationXTarget + 0.5f * button.measuredWidth - xCenter).absoluteValue
            if (button.coordinateXWithinBoundsToKeepPosition(xCenter) && centerDist < minCenterDist) {
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
//        Log.v(
//            "Metronome",
//            "SoundChooser.getSelectedNoteDuration: indexDuration: $indexDuration, indexTuplets: $indexTuplets"
//        )
        return when (indexTuplets) {
            1 -> when (indexDuration) {
                1 -> NoteDuration.EighthTriplet
                2 -> NoteDuration.SixteenthTriplet
                else -> NoteDuration.QuarterTriplet
            }
            2 -> when (indexDuration) {
                1 -> NoteDuration.EighthQuintuplet
                2 -> NoteDuration.SixteenthQuintuplet
                else -> NoteDuration.QuarterQuintuplet
            }
            else -> when (indexDuration) {
                1 -> NoteDuration.Eighth
                2 -> NoteDuration.Sixteenth
                else -> NoteDuration.Quarter
            }
        }
    }

    private fun getNoteDurationIndex(noteDuration: NoteDuration): Int {
        return when (noteDuration) {
            NoteDuration.Quarter, NoteDuration.QuarterTriplet, NoteDuration.QuarterQuintuplet -> 0
            NoteDuration.Eighth, NoteDuration.EighthTriplet, NoteDuration.EighthQuintuplet -> 1
            NoteDuration.Sixteenth, NoteDuration.SixteenthTriplet, NoteDuration.SixteenthQuintuplet -> 2
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
        for (note in 0 until getNumAvailableNotes()) {
            val drawableId = getNoteDrawableResourceID(note, noteDuration)
            noteSelection.setButtonDrawable(note, drawableId)
            dynamicSelection.setButtonDrawable(note, drawableId)
        }
    }

    enum class Orientation { Landscape, Portrait }
    enum class Status { Off, MoveNote, Dynamic, Static }

    companion object {
        const val animateShort = 50L // 100L
        const val animateLong = 100L // 200L

        private fun disableSwipeForClickableButton(view: View) {
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN)
                    view.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
}
