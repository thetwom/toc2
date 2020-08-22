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
import android.graphics.Outline
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import kotlin.math.*


class VolumeControl(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    private val rectInt = Rect()

    private val sliderButton = ImageButton(context).apply {
        background = AppCompatResources.getDrawable(context, R.drawable.volume_control_slider)?.mutate()
        imageTintList = AppCompatResources.getColorStateList(context, R.color.volumecontrol_icon)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(0, 0, 0, 0)
        setImageDrawable(volMute)
    }

    private val belowSliderView1 = ImageView(context).apply {
        background = AppCompatResources.getDrawable(context, R.drawable.volume_control_below_slider1)
    }

    private val belowSliderView2 = ImageView(context).apply {
        background = AppCompatResources.getDrawable(context, R.drawable.volume_control_below_slider2)
    }

    private val backgroundView = ImageView(context).apply {
        background = AppCompatResources.getDrawable(context, R.drawable.volumecontrol_background)
    }

    private val volMute = AppCompatResources.getDrawable(context, R.drawable.ic_volume_mute)
    private var volDown = AppCompatResources.getDrawable(context, R.drawable.ic_volume_down)
    private var volUp = AppCompatResources.getDrawable(context, R.drawable.ic_volume_up)

    var vertical: Boolean = false
        set(value) {
            field = value
            if (value) {
                sliderButton.translationX = 0f
                belowSliderView2.translationX = 0f
            }
            else {
                sliderButton.translationY = 0f
                belowSliderView2.translationY = 0f
            }
            requestLayout()
        }

    private val iSpace = Utilities.dp2px(2f)
    var volume: Float = 0f
        private set

    fun setVolume(newVolume: Float, animationDuration: Long = 0L) {
        if (volume == newVolume)
            return

        volume = newVolume
        setSliderToMatchVolume(animationDuration)
        resetSliderButtonIcon()
    }

    private fun setSliderToMatchVolume(animationDuration: Long = 0L) {
//        Log.v("Metronome", "VolumeControl.setSliderToMatchVolume")
        val sliderTranslation : Float

        if(measuredHeight == 0 || measuredWidth == 0)
            return
        if (vertical) {
            val sliderMovingSpace = backgroundView.measuredHeight - 2 * iSpace - sliderButton.measuredHeight
            sliderTranslation = sliderMovingSpace - (volume * sliderMovingSpace)

            if (animationDuration == 0L || visibility != View.VISIBLE) {
//                Log.v("Metronome", "VolumeControl.setVolume: no animation, sliderTranslation = $sliderTranslation, old translation = ${sliderButton.translationY}")
                sliderButton.translationY = sliderTranslation
            }
            else {
//                Log.v("Metronome", "VolumeControl.setVolume: with animation, sliderTranslation = $sliderTranslation, old translation = ${sliderButton.translationY}")
                sliderButton.animate()
                        .translationY(sliderTranslation)
                        .setDuration(animationDuration)
                        .start()
            }
        }
        else {
            val sliderMovingSpace = backgroundView.measuredWidth - 2 * iSpace - sliderButton.measuredWidth
            sliderTranslation = volume * sliderMovingSpace
            if (animationDuration == 0L || visibility != View.VISIBLE) {
                sliderButton.translationX = sliderTranslation
            }
            else {
                sliderButton.animate()
                        .translationX(sliderTranslation)
                        .setDuration(animationDuration)
                        .start()
            }
        }

        scaleBelowSliderView(sliderTranslation, animationDuration)
    }

    interface OnVolumeChangedListener {
        fun onVolumeChanged(volume: Float)
    }

    var onVolumeChangedListener: OnVolumeChangedListener? = null

    private val outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View?, outline: Outline) {
            val cornerRad = 0.5f * (if (vertical) backgroundView.measuredWidth else backgroundView.measuredHeight)
            rectInt.set(paddingLeft, paddingTop, paddingLeft + backgroundView.measuredWidth, paddingTop + backgroundView.measuredHeight)
            outline.setRoundRect(rectInt, cornerRad)
        }
    }

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, R.attr.volumeControlStyle)

    init {
        setOutlineProvider(outlineProvider)

        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.VolumeControl,
                    defStyleAttr, R.style.Widget_AppTheme_VolumeControlStyle)

            vertical = ta.getBoolean(R.styleable.VolumeControl_vertical, vertical)
            ta.recycle()
        }

        addView(backgroundView)
        addView(belowSliderView1)
        addView(belowSliderView2)
        addView(sliderButton)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        Log.v("Metronome", "VolumeControl.onMeasure")
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        val backgroundWidth = measuredWidth - paddingLeft - paddingRight
        val backgroundHeight = measuredHeight - paddingTop - paddingBottom
        backgroundView.measure(
                MeasureSpec.makeMeasureSpec(backgroundWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(backgroundHeight, MeasureSpec.EXACTLY)
        )

        val sliderThickness = if (vertical)
            measuredWidth - paddingLeft - paddingRight - 2 * iSpace
        else
            measuredHeight - paddingBottom - paddingTop - 2 * iSpace
        val sliderLength = 3f * sliderThickness

        val sliderWidth = if (vertical) sliderThickness else sliderLength
        val sliderHeight = if (vertical) sliderLength else sliderThickness

        sliderButton.measure(
                MeasureSpec.makeMeasureSpec(sliderWidth.toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(sliderHeight.toInt(), MeasureSpec.EXACTLY)
        )

        val belowSliderWidth1 = if (vertical) sliderThickness else sliderLength - 5
        val belowSliderHeight1 = if (vertical) sliderLength - 5 else sliderThickness

        belowSliderView1.measure(
                MeasureSpec.makeMeasureSpec(belowSliderWidth1.toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(belowSliderHeight1.toInt(), MeasureSpec.EXACTLY)
        )

        val belowSliderWidth2 = if (vertical) sliderThickness.toInt() else (backgroundWidth - sliderLength - 2 * iSpace).toInt()
        val belowSliderHeight2 = if (vertical) (backgroundHeight - sliderLength - 2 * iSpace).toInt() else sliderThickness.toInt()

        belowSliderView2.measure(
                MeasureSpec.makeMeasureSpec(belowSliderWidth2, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(belowSliderHeight2, MeasureSpec.EXACTLY)
        )

        setSliderToMatchVolume()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val h = b - t
//        Log.v("Metronome", "VolumeControl.onLayout: h = $h")
        backgroundView.layout(paddingLeft, paddingTop, backgroundView.measuredWidth, backgroundView.measuredHeight)
        val belowSliderVertical = if (vertical) (h - paddingBottom - iSpace - belowSliderView1.measuredHeight).toInt() - 1 else (paddingLeft + iSpace).toInt()
        val belowSliderHorizontal = if (vertical) (paddingLeft + iSpace).toInt() else (paddingLeft + iSpace).toInt()
        belowSliderView1.layout(
                belowSliderHorizontal,
                belowSliderVertical,
                belowSliderHorizontal + belowSliderView1.measuredWidth,
                belowSliderVertical + belowSliderView1.measuredHeight
        )
        val belowSlider2Horizontal = if (vertical) (paddingLeft + iSpace).toInt() else (paddingLeft + 0.5f * sliderButton.measuredWidth).toInt()
        val belowSlider2Vertical = if (vertical) (paddingTop + 0.5f * sliderButton.measuredHeight).toInt() else (paddingTop + iSpace).toInt()
        belowSliderView2.layout(
                belowSlider2Horizontal,
                belowSlider2Vertical,
                belowSlider2Horizontal + belowSliderView2.measuredWidth,
                belowSlider2Vertical + belowSliderView2.measuredHeight
        )
        if (vertical)
            belowSliderView2.pivotY = belowSliderView2.measuredHeight.toFloat()
        else
            belowSliderView2.pivotX = 0f

        val sliderHorizontal = (paddingLeft + iSpace).toInt()
        val sliderVertical = (paddingTop + iSpace).toInt()
        sliderButton.layout(
                sliderHorizontal,
                sliderVertical,
                sliderHorizontal + sliderButton.measuredWidth,
                sliderVertical + sliderButton.measuredHeight
        )
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
//        Log.v("Metronome", "VolumeControl.onTouchEvent")
        val action = event.actionMasked
        val x = event.x
        val y = event.y

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                sliderButton.isPressed = true
            }
            MotionEvent.ACTION_UP -> {
                sliderButton.isPressed = false
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                Log.v("Metronome", "VolumeControl.onTouchEvent : ACTION_MOVE")
                val sliderLength = if (vertical) sliderButton.height else sliderButton.width
                if (vertical) {
                    var newTranslation = y - 0.5f * sliderLength
                    newTranslation = max(newTranslation, 0f)
                    newTranslation = min(newTranslation, backgroundView.height - 2 * iSpace - sliderLength)
                    sliderButton.translationY = newTranslation
                    scaleBelowSliderView(newTranslation, 0L)
                    setVolumeFromSliderTranslation(newTranslation)
                } else {
                    var newTranslation = x - 0.5f * sliderLength
                    newTranslation = max(newTranslation, 0f)
                    newTranslation = min(newTranslation, backgroundView.width - 2 * iSpace - sliderLength)
                    sliderButton.translationX = newTranslation
                    scaleBelowSliderView(newTranslation, 0L)
                    setVolumeFromSliderTranslation(newTranslation)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
//                Log.v("Metronome", "VolumeControl.onTouchEvent : ACTION_UP")
                onVolumeChangedListener?.onVolumeChanged(volume)
                isPressed = false
                return true
            }
        }
        return false
    }

    private fun scaleBelowSliderView(sliderTranslation: Float, animationDuration: Long = 0L) {
        if (vertical) {
            val sliderMovingSpace = backgroundView.measuredHeight - 2 * iSpace - sliderButton.measuredHeight
            val sY = (sliderMovingSpace - sliderTranslation) / sliderMovingSpace
            if(animationDuration == 0L)
                belowSliderView2.scaleY = sY
            else
                belowSliderView2.animate().scaleY(sY).setDuration(animationDuration).start()
        } else {
            val sliderMovingSpace = backgroundView.measuredWidth - 2 * iSpace - sliderButton.measuredWidth
            val sX = sliderTranslation / sliderMovingSpace
            if(animationDuration == 0L)
                belowSliderView2.scaleX = sX
            else
                belowSliderView2.animate().scaleX(sX).setDuration(animationDuration).start()
        }
    }

    private fun setVolumeFromSliderTranslation(sliderTranslation: Float) {
        val newVolume = if (vertical) {
            val sliderMovingSpace = backgroundView.measuredHeight - 2 * iSpace - sliderButton.measuredHeight
            (sliderMovingSpace - sliderTranslation) / sliderMovingSpace
        } else {
            val sliderMovingSpace = backgroundView.measuredWidth - 2 * iSpace - sliderButton.measuredWidth
            sliderTranslation / sliderMovingSpace
        }

        if (volume == newVolume)
            return

        // We only change the volume if it one hundredth away from the previous value
        if (abs(volume - newVolume) > 0.01f || newVolume == 0f || newVolume == 1.0f) {
            volume = newVolume
            onVolumeChangedListener?.onVolumeChanged(volume)
            resetSliderButtonIcon()
        }
    }

    private fun resetSliderButtonIcon() {
        when {
            volume < 0.01f -> {
                sliderButton.setImageDrawable(volMute)
            }
            volume < 0.6f -> {
                sliderButton.setImageDrawable(volDown)
            }
            else -> {
                sliderButton.setImageDrawable(volUp)
            }
        }
    }
}
