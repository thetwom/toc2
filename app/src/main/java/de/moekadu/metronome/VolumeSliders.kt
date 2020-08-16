/*
 * Copyright 2019 Michael Moessner
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
import android.graphics.Color
import android.graphics.Rect
import android.transition.*
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
//import androidx.core.view.setPadding
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class VolumeSliders(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : ViewGroup(context, attrs, defStyleAttr){

    private val tunerHeightPercent = 0.7f
    private val buttonTunerSpacing = Utilities.dp2px(8f)
    private val tunerSpacing = Utilities.dp2px(4f)
    private val elementPadding = Utilities.dp2px(8f)
    private var activeTranslationZ = 20f
    private val minimumButtonHeight = (Utilities.dp2px(40f)).roundToInt()
    private val minimumButtonWidth = (Utilities.dp2px(70f)).roundToInt()
    private val buttonAspectRatio = 3.0f
    var folded = true
        private set

    /// Bounding box of corresponding NoteView (in absolute coordinates).
    private val noteViewBoundingBox = Rect()
    private val volumeControls = ArrayList<VolumeControl>()

    private val boundingBox = Rect()

    private val button = ImageButton(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        background = AppCompatResources.getDrawable(context, R.drawable.volumeslider_unfold_button_background)
        setImageResource(R.drawable.volumeslider_unfold_button_icon)
        imageTintList =  AppCompatResources.getColorStateList(context, R.color.volumeslider_unfold_button_icon)
        setPadding(0, 0, 0, 0)
        elevation = Utilities.dp2px(2f)
        setOnClickListener {
            if(folded)
                unfold()
            else
                fold()
        }
    }
    val background = ImageButton(context)

    private var sliderColor = Color.BLACK
    private var iconColor = Color.WHITE
    private var backgroundSurfaceColor = Color.WHITE
    private var surfaceBackgroundColor = Color.WHITE
    private var belowSliderColor =  Color.WHITE

    private val noteListChangedListener = object: NoteList.NoteListChangedListener {
        override fun onNoteAdded(note: NoteListItem, index: Int) {
            noteList?.let { notes ->
                TransitionManager.beginDelayedTransition(
                        this@VolumeSliders,
                        AutoTransition().apply { duration = 300L }
                )

                while (volumeControls.size < notes.size) {
                    val vC = createVolumeControl()
                    addView(vC)
                    volumeControls.add(vC)
                }

                for (i in volumeControls.indices) {
                    volumeControls[i].setVolume(notes[i].volume, 300L)
                }
            }
        }

        override fun onNoteRemoved(note: NoteListItem, index: Int) {
            noteList?.let { notes ->
                TransitionManager.beginDelayedTransition(
                        this@VolumeSliders,
                        AutoTransition().apply { duration = 300L }
                )

                while (volumeControls.size > notes.size) {
                    val vC = volumeControls.last()
                    removeView(vC)
                    volumeControls.remove(vC)
                }

                for (i in volumeControls.indices) {
                    volumeControls[i].setVolume(notes[i].volume, 300L)
                }
            }
        }

        override fun onNoteMoved(note: NoteListItem, fromIndex: Int, toIndex: Int) {
            if (volumeControls.size != noteList?.size)
                return
            noteList?.let { notes ->
                for (i in notes.indices)
                    volumeControls[i].setVolume(notes[i].volume, 300L)
            }
        }

        override fun onVolumeChanged(note: NoteListItem, index: Int) {
            if (index in 0 until volumeControls.size)
                volumeControls[index].setVolume(note.volume, 300L)
        }

        override fun onNoteIdChanged(note: NoteListItem, index: Int) { }
        override fun onDurationChanged(note: NoteListItem, index: Int) { }
    }

    var noteList: NoteList? = null
        set(value){
            field?.unregisterNoteListChangedListener(noteListChangedListener)
            field = value
            field?.registerNoteListChangedListener(noteListChangedListener)
            for (vC in volumeControls)
                removeView(vC)
            volumeControls.clear()
            value?.let { notes ->
                for (n in notes) {
                    val vC = createVolumeControl()
                    vC.setVolume(n.volume)
                    addView(vC)
                    volumeControls.add(vC)
                }
            }
        }

    constructor(context: Context, attrs: AttributeSet? = null)
            :this(context, attrs, R.attr.volumeSlidersStyle)

    init {
//        Log.v("Metronome", "VolumeSliders" + getLeft());
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.VolumeSliders, defStyleAttr, R.style.Widget_AppTheme_VolumeSlidersStyle)
            sliderColor = ta.getColor(R.styleable.VolumeSliders_sliderColor, sliderColor)
            iconColor = ta.getColor(R.styleable.VolumeSliders_iconColor, iconColor)
            backgroundSurfaceColor = ta.getColor(R.styleable.VolumeSliders_backgroundSurfaceColor, backgroundSurfaceColor)
            surfaceBackgroundColor = ta.getColor(R.styleable.VolumeSliders_backgroundColor, surfaceBackgroundColor)
            belowSliderColor = ta.getColor(R.styleable.VolumeSliders_belowSliderColor, belowSliderColor)
            activeTranslationZ = ta.getDimension(R.styleable.VolumeSliders_activeTranslationZ, activeTranslationZ)
            ta.recycle()
        }

        addView(button)
        background.setBackgroundColor(surfaceBackgroundColor)
        background.alpha = 0.7f
        addView(background)
        background.visibility = View.GONE
    }

    override fun onMeasure(widthMeasureSpec : Int, heightMeasureSpec : Int) {
        Log.v("Metronome", "VolumeSliders.onMeasure")
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        background.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )

        val buttonWidthFolded = max(minimumButtonWidth.toFloat(), measuredWidth / 5.0f).roundToInt()
        val buttonWidthUnfolded = measuredWidth - paddingLeft - paddingRight
        val buttonWidth = if (folded) buttonWidthFolded else buttonWidthUnfolded
        val buttonHeight = max(minimumButtonHeight, (buttonWidthFolded/ buttonAspectRatio).roundToInt())

        button.measure(
                MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(buttonHeight, MeasureSpec.EXACTLY)
        )

        val maxHeight = measuredHeight - elementPadding - buttonHeight
        val defaultHeight = measuredHeight * tunerHeightPercent
        val tunerHeight =  (min(maxHeight, defaultHeight) - elementPadding - buttonTunerSpacing).toInt()

        var tunerWidth = noteViewBoundingBox.width()
        noteList?.let { notes ->
            for (i in notes.indices) {
                NoteView.computeBoundingBox(i, notes.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)
                tunerWidth = min((boundingBox.width() - tunerSpacing).toInt(), tunerWidth)
            }
        }

        tunerWidth = min((tunerHeight / 7f).toInt(), tunerWidth)

        val volumeControlWidthSpec = MeasureSpec.makeMeasureSpec(tunerWidth, MeasureSpec.EXACTLY)
        val volumeControlHeightSpec = MeasureSpec.makeMeasureSpec(tunerHeight, MeasureSpec.EXACTLY)
        for(v in volumeControls)
            v.measure(volumeControlWidthSpec, volumeControlHeightSpec)

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        background.layout(0, 0, background.measuredWidth, background.measuredHeight)
        val h = b - t
        Log.v("Metronome", "VolumeSliders.onLayout: folded=$folded, volumeControls.size=${volumeControls.size}")
        val buttonTop = if(folded || volumeControls.size == 0) {
            h - (elementPadding + button.measuredHeight).toInt()
        }
        else {
            val tunerHeight = volumeControls[0].measuredHeight
            h - (elementPadding + tunerHeight + buttonTunerSpacing + button.measuredHeight).toInt()
        }
        Log.v("Metronome", "VolumeSliders.onLayout: button.layout($paddingLeft, $buttonTop, ${paddingLeft + button.measuredWidth}, ${buttonTop + button.measuredHeight})")
        button.layout(paddingLeft, buttonTop, paddingLeft + button.measuredWidth, buttonTop + button.measuredHeight
        )

        val notes = noteList
        if (volumeControls.size  > 0 && notes != null && noteViewBoundingBox.width() > 0) {
            val tunerHeight = volumeControls[0].measuredHeight
            val vT = (h - (elementPadding + tunerHeight)).toInt()
            for (i in volumeControls.indices) {
                if (i < notes.size) {
                    NoteView.computeBoundingBox(i, notes.size, noteViewBoundingBox.width(), noteViewBoundingBox.height(), boundingBox)
                    boundingBox.offset(noteViewBoundingBox.left - l, noteViewBoundingBox.top - t)
                    val vL = (boundingBox.centerX() - l - translationX - 0.5f * volumeControls[i].measuredWidth).toInt()
//                    val vL = (boundingBoxes[i].centerX() - l - translationX - 0.5f * volumeControls[i].measuredWidth).toInt()
                    volumeControls[i].layout(vL, vT, vL + volumeControls[i].measuredWidth, vT + volumeControls[i].measuredHeight)
                }
            }
        }
    }

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

    private fun createVolumeControl() : VolumeControl {
        val volumeControl = VolumeControl(context, null)
        volumeControl.elevation = Utilities.dp2px(2f)
        volumeControl.vertical = true
        volumeControl.setPadding(0,0,0,0)

        volumeControl.onVolumeChangedListener = object : VolumeControl.OnVolumeChangedListener {
            override fun onVolumeChanged(volume: Float) {
                val index = volumeControls.indexOf(volumeControl)
                noteList?.setVolume(index, volume)
            }
        }

          if (folded)
              volumeControl.visibility = View.GONE
          else
              volumeControl.visibility = View.VISIBLE

        return volumeControl
    }

    private fun fold(animationDuration : Long = 300L) {
        folded = true
        if(animationDuration > 0L) {

            val transition = TransitionSet().apply {
                duration = animationDuration
                addTransition(Slide().apply { slideEdge = Gravity.BOTTOM })
                addTransition(ChangeBounds())

                addListener(object: Transition.TransitionListener {
                    override fun onTransitionEnd(transition: Transition) {
                        translationZ = 0f
                    }
                    override fun onTransitionResume(transition: Transition) { }
                    override fun onTransitionPause(transition: Transition) { }
                    override fun onTransitionCancel(transition: Transition) { }
                    override fun onTransitionStart(transition: Transition) { }
                })

            }

            TransitionManager.beginDelayedTransition(this, transition)
        }
        else {
            translationZ = 0f
        }
        button.isSelected = false
        background.visibility = View.GONE
        for (v in volumeControls)
            v.visibility = View.GONE
    }

    fun unfold(animationDuration : Long = 300L) {
        folded = false
        if(animationDuration > 0L) {
            Log.v("Metronome", "VolumeSliders.unfold: with animation")
            val transition = TransitionSet().apply {
                duration = animationDuration
                addTransition(ChangeBounds())
                addTransition(Slide().apply { slideEdge = Gravity.BOTTOM })
            }
            TransitionManager.beginDelayedTransition(this, transition)
        }
        translationZ = activeTranslationZ
        button.isSelected = true
        background.visibility = View.VISIBLE
        for (v in volumeControls)
            v.visibility = View.VISIBLE
    }
}
