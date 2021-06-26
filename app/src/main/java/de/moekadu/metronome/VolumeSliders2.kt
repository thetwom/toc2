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
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import kotlin.math.min
import kotlin.math.roundToInt


class VolumeSliders2(context: Context) {

    private val buttonTunerSpacing = Utilities.dp2px(8f)
    private val tunerSpacing = Utilities.dp2px(4f)

    var folded = true
        private set

    /// Bounding box of corresponding NoteView (in absolute coordinates).
    private val volumeControls = ArrayList<VolumeControl>()

    private val boundingBox = Rect()

    private val openButton = ImageButton(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        background = AppCompatResources.getDrawable(context, R.drawable.plus_button_background)
        setImageResource(R.drawable.ic_tune)
        imageTintList =  AppCompatResources.getColorStateList(context, R.color.volumeslider_unfold_button_icon)
        setPadding(0, 0, 0, 0)
        elevation = Utilities.dp2px(2f)
        setOnClickListener {
            volumeChangedListener?.unfold()
        }
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                parent.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    private val closeButton = ImageButton(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        background = AppCompatResources.getDrawable(context, R.drawable.plus_button_background)
        setImageResource(R.drawable.ic_close)
        imageTintList =  AppCompatResources.getColorStateList(context, R.color.volumeslider_unfold_button_icon)
        setPadding(0, 0, 0, 0)
        elevation = Utilities.dp2px(2f)
        visibility = View.GONE
        setOnClickListener {
            volumeChangedListener?.fold()
        }
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                parent.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    interface VolumeChangedListener {
        fun onVolumeChanged(index: Int, volume: Float)
        fun fold()
        fun unfold()
    }

    var volumeChangedListener: VolumeChangedListener? = null


    fun addButtons(viewGroup: ViewGroup) {
        viewGroup.addView(openButton)
        viewGroup.addView(closeButton)
    }

    fun measure(noteViewWidth: Int, noteViewHeight: Int, viewHeight: Int, foldingButtonHeight: Int) {
//        Log.v("Metronome", "VolumeSliders.onMeasure")

        val buttonSpec = View.MeasureSpec.makeMeasureSpec(foldingButtonHeight, View.MeasureSpec.EXACTLY)
        closeButton.measure(buttonSpec, buttonSpec)
        openButton.measure(buttonSpec, buttonSpec)

        val maxHeight = viewHeight - buttonTunerSpacing - foldingButtonHeight
        val defaultHeight = 2f * noteViewHeight
        val tunerHeight =  min(maxHeight, defaultHeight).toInt()

        var tunerWidth = (0.35f * noteViewHeight).roundToInt()
        for (i in volumeControls.indices) {
            NoteView.computeBoundingBox(i, volumeControls.size, noteViewWidth, noteViewHeight, boundingBox)
            tunerWidth = min((boundingBox.width() - tunerSpacing).toInt(), tunerWidth)
        }

        tunerWidth = min((tunerHeight / 4f).toInt(), tunerWidth)

        val volumeControlWidthSpec = View.MeasureSpec.makeMeasureSpec(tunerWidth, View.MeasureSpec.EXACTLY)
        val volumeControlHeightSpec = View.MeasureSpec.makeMeasureSpec(tunerHeight, View.MeasureSpec.EXACTLY)
        volumeControls.forEach {
            it.measure(volumeControlWidthSpec, volumeControlHeightSpec)
        }
    }

    fun layout(l: Int, b: Int, noteViewWidth: Int, noteViewHeight: Int) {
        openButton.layout(l, b - openButton.measuredHeight, l + openButton.measuredWidth, b)
        val closeButtonBottom = if (volumeControls.size == 0) {
            b
        } else {
            (b - volumeControls[0].measuredHeight - buttonTunerSpacing).roundToInt()
        }
        closeButton.layout(l,
            closeButtonBottom - closeButton.measuredHeight,
            l + closeButton.measuredWidth,
            closeButtonBottom
        )

        if (volumeControls.size > 0) {
            val tunerHeight = volumeControls[0].measuredHeight
            val volumeControlsTop = b -  tunerHeight
            volumeControls.forEachIndexed { index, volumeControl ->
                NoteView.computeBoundingBox(index, volumeControls.size, noteViewWidth, noteViewHeight, boundingBox)
                val volumeControlLeft = l + (boundingBox.centerX() - 0.5f * volumeControl.measuredWidth).roundToInt()
                volumeControl.layout(volumeControlLeft, volumeControlsTop,
                    volumeControlLeft + volumeControl.measuredWidth,
                    volumeControlsTop + volumeControl.measuredHeight)
            }
        }
    }

    fun setNoteList(viewGroup: ViewGroup, noteList: ArrayList<NoteListItem>, animationDuration: Long) {
        // delete unneeded volume controls
        for (i in noteList.size until volumeControls.size)
            viewGroup.removeView(volumeControls[i])
        if (volumeControls.size > noteList.size)
            volumeControls.subList(noteList.size, volumeControls.size).clear()

        // add missing volume controls
        val numVolumeControlsOld = volumeControls.size
        for (i in numVolumeControlsOld until noteList.size) {
            val volumeControl = createVolumeControl(viewGroup.context)
            viewGroup.addView(volumeControl)
            volumeControls.add(volumeControl)
        }

        volumeControls.forEachIndexed { index, volumeControl ->
            volumeControl.setVolume(noteList[index].volume, if (folded) 0L else animationDuration)
        }
    }

    private fun createVolumeControl(context: Context) : VolumeControl {
        val volumeControl = VolumeControl(context)
        volumeControl.elevation = Utilities.dp2px(2f)
        volumeControl.vertical = true
        volumeControl.setPadding(0,0,0,0)

        volumeControl.onVolumeChangedListener = object : VolumeControl.OnVolumeChangedListener {
            override fun onVolumeChanged(volume: Float) {
                val index = volumeControls.indexOf(volumeControl)
                if (index >= 0)
                    volumeChangedListener?.onVolumeChanged(index, volume)
            }

            override fun onDown() {

            }

            override fun onUp(volume: Float) {

            }
        }

          if (folded)
              volumeControl.visibility = View.GONE
          else
              volumeControl.visibility = View.VISIBLE

        return volumeControl
    }

    fun fold(animationDuration: Long) {
        folded = true

        if(animationDuration > 0L) {
            volumeControls.forEach {
                if (it.visibility == View.VISIBLE)
                    it.animate().alpha(0.0f).withEndAction { it.visibility = View.GONE }
            }
            if (closeButton.visibility == View.VISIBLE)
                closeButton.animate().alpha(0.0f).withEndAction { closeButton.visibility = View.GONE }

            if (openButton.visibility != View.VISIBLE)
                openButton.alpha = 0f
            openButton.visibility = View.VISIBLE
            openButton.animate().alpha(1.0f)
        } else {
            openButton.alpha = 1f
            openButton.visibility = View.VISIBLE
            closeButton.visibility = View.GONE
            volumeControls.forEach {
                it.alpha = 1f
                it.visibility = View.GONE
            }
        }
    }

    fun unfold(animationDuration: Long) {
        folded = false

        if(animationDuration > 0L) {
            volumeControls.forEach {
                if (it.visibility != View.VISIBLE)
                    it.alpha = 0f
                it.animate().alpha(1.0f).withStartAction { it.visibility = View.VISIBLE }
            }
            if (openButton.visibility == View.VISIBLE)
                openButton.animate().alpha(0.0f).withEndAction { openButton.visibility = View.GONE }

            if (closeButton.visibility != View.VISIBLE)
                closeButton.alpha = 0f
            closeButton.visibility = View.VISIBLE
            closeButton.animate().alpha(1.0f)

        } else {
            volumeControls.forEach {
                it.alpha = 1f
                it.visibility = View.VISIBLE
            }
            closeButton.alpha = 1f
            closeButton.visibility = View.VISIBLE
            openButton.visibility = View.GONE
        }
    }
}
