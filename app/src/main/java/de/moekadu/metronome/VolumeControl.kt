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
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class VolumeControl(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : View(context, attrs, defStyleAttr) {

    private val defaultWidth = (Utilities.dp2px(60f)).roundToInt()
    private val defaultLength = (Utilities.dp2px(300f)).roundToInt()
    private val rectInt = Rect()
    private val rect = RectF()
    private val rectSlider = RectF()
    private val rectZeroToSlider = RectF()

    private val sliderDrawable = ContextCompat.getDrawable(context, R.drawable.volume_control_slider)
   // private val sliderDrawable = GradientDrawable()
   // private var sliderColorStateList = ContextCompat.getColorStateList(context, R.color.done_button_background) as ColorStateList

    private val volMute = ContextCompat.getDrawable(context, R.drawable.ic_volume_mute)
    private var volDown = ContextCompat.getDrawable(context, R.drawable.ic_volume_down)
    private var volUp = ContextCompat.getDrawable(context, R.drawable.ic_volume_up)

    var backgroundSurfaceColor = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var sliderColor: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            volDown?.colorFilter = PorterDuffColorFilter(value, PorterDuff.Mode.SRC_ATOP)
            volUp?.colorFilter = PorterDuffColorFilter(value, PorterDuff.Mode.SRC_ATOP)
            volMute?.colorFilter = PorterDuffColorFilter(value, PorterDuff.Mode.SRC_ATOP)
            invalidate()
        }

    var belowSliderColor : Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var vertical : Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val centerX : Float
        get() {
            return if(vertical) {
                paddingLeft + iSpace + movableHeight / 2.0f
            } else {
                val pos0 = paddingLeft + iSpace
                val pos1 = width - paddingRight - movableLength - iSpace
                pos0 + volume * (pos1 - pos0) + movableLength / 2.0f
            }
        }

    private val centerY : Float
        get() {
            return if(vertical) {
                val pos0 = paddingTop + iSpace
                val pos1 = height - paddingTop - movableLength - iSpace
                pos0 + (1.0f - volume) * (pos1 - pos0) + movableLength / 2.0f
            } else {
                paddingTop + iSpace + movableHeight / 2.0f
            }
        }

    private val iSpace = Utilities.dp2px(2f)
    var volume : Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val contourPaint = Paint()

    interface OnVolumeChangedListener {
        fun onVolumeChanged(volume: Float)
    }

    var onVolumeChangedListener: OnVolumeChangedListener? = null

    private val outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View?, outline: Outline) {
            val iWid = movableHeight
            val cornerRad = 0.5f * iWid
            rectInt.set(paddingLeft, paddingTop, width - paddingRight,height - paddingBottom)
            outline.setRoundRect(rectInt, cornerRad)
        }
    }

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, R.attr.volumeControlStyle)

    init {
        setOutlineProvider(outlineProvider)

        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.VolumeControl,
                    defStyleAttr, R.style.Widget_AppTheme_VolumeControlStyle)
            iconColor = ta.getColor(R.styleable.VolumeControl_iconColor, iconColor)
            sliderColor = ta.getColor(R.styleable.VolumeControl_sliderColor, sliderColor)
            backgroundSurfaceColor = ta.getColor(R.styleable.VolumeControl_backgroundColor, backgroundSurfaceColor)
            belowSliderColor = ta.getColor(R.styleable.VolumeControl_belowSliderColor, belowSliderColor)
            vertical = ta.getBoolean(R.styleable.VolumeControl_vertical, vertical)
            ta.recycle()
        }

        volMute?.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)
        volDown?.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)
        volUp?.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)
        foreground = sliderDrawable
    }

//    override fun onLayout(changed : Boolean, l : Int, t : Int, r : Int, b : Int) {
//
//    }

    override fun onMeasure(widthMeasureSpec : Int, heightMeasureSpec : Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        //val layoutParams = layoutParams as MarginLayoutParams

        var desiredWidth = 0 // layoutParams.leftMargin + layoutParams.rightMargin
        var desiredHeight = 0 //layoutParams.topMargin + layoutParams.bottomMargin

        if(vertical) {
            desiredWidth += defaultWidth
            desiredHeight += defaultLength
        }
        else{
            desiredWidth += defaultLength
            // noinspection SuspiciousNameCombination
            desiredHeight += defaultWidth
        }

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    //override fun dispatchDraw(canvas : Canvas) {
    //    super.dispatchDraw(canvas)

    override fun onDrawForeground(canvas: Canvas?) {
        if(canvas == null)
            return

        val iLen = movableLength
        val iWid = movableHeight
        val cornerRad = 0.5f * iWid

        contourPaint.isAntiAlias = true

        rect.set(paddingLeft.toFloat(), paddingTop.toFloat(),
                (width - paddingRight).toFloat(), (height - paddingBottom).toFloat())

        if(vertical) {
            rectSlider.set(centerX - iWid / 2.0f, centerY - iLen / 2.0f, centerX + iWid / 2.0f, centerY + iLen / 2.0f)
            rectZeroToSlider.set(centerX - iWid / 2.0f, centerY, centerX + iWid / 2.0f, height - iSpace - paddingBottom)
        }
        else {
            rectSlider.set(centerX - iLen / 2.0f, centerY - iWid / 2.0f, centerX + iLen / 2.0f, centerY + iWid / 2.0f)
            rectZeroToSlider.set(paddingLeft + iSpace, centerY - iWid / 2.0f, centerX, centerY + iWid / 2.0f)
        }

        contourPaint.color = backgroundSurfaceColor
        contourPaint.style = Paint.Style.FILL

        canvas.drawRoundRect(rect, cornerRad, cornerRad, contourPaint)

        contourPaint.style = Paint.Style.FILL
        contourPaint.color = belowSliderColor
//        contourPaint.alpha = 50
        canvas.drawRoundRect(rectZeroToSlider, cornerRad, cornerRad, contourPaint)

        //sliderDrawable.cornerRadius = cornerRad
        //sliderDrawable.color = sliderColorStateList
        sliderDrawable?.setBounds(rectSlider.left.roundToInt(), rectSlider.top.roundToInt(), rectSlider.right.roundToInt(), rectSlider.bottom.roundToInt())
        //sliderDrawable?.draw(canvas)

        super.onDrawForeground(canvas)

        var icon = volUp
        if(volume < 0.01) {
            icon = volMute
        }
        else if(volume < 0.6){
            icon = volDown
        }

        icon?.setBounds((centerX - iWid/2.0f).roundToInt(), (centerY - iWid/2.0f).roundToInt(),
                (centerX + iWid/2.0f).roundToInt(), (centerY + iWid/2.0f).roundToInt())
        icon?.draw(canvas)
    }

    override fun onTouchEvent(event : MotionEvent) : Boolean {
//        Log.v("Metronome", "VolumeControl.onTouchEvent")
        val action = event.actionMasked
        val x = event.x
        val y = event.y

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                sliderDrawable?.state = intArrayOf(android.R.attr.state_pressed)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                sliderDrawable?.state = intArrayOf()
                invalidate()
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                Log.v("Metronome", "VolumeControl.onTouchEvent : ACTION_MOVE")
                var newVolume = if (vertical)
                    1.0f - dxToDxPos(y - movableLength / 2.0f)
                else
                    dxToDxPos(x - movableLength / 2.0f)
//                Log.v("Metronome", "VolumeControl.onTouchEvent : newVolume = $newVolume")
                newVolume = min(1.0f, newVolume)
                newVolume = max(0.0f, newVolume)
                volume = newVolume
                isPressed = true
                //sliderDrawableRipple.state = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
                return true
            }
            MotionEvent.ACTION_UP -> {
//                Log.v("Metronome", "VolumeControl.onTouchEvent : ACTION_UP")
                onVolumeChangedListener?.onVolumeChanged(volume)
                //sliderDrawableRipple.state = intArrayOf()
                isPressed = false
                return true
            }
        }
        return false
    }

    private val movableLength : Float
        get() {
            return 3.0f * movableHeight
        }

    private val movableHeight : Float
        get() {
            return if(vertical)
                width - paddingLeft - paddingRight - 2 * iSpace
            else
                height - paddingBottom - paddingTop - 2 * iSpace
        }

    private fun dxToDxPos(dx : Float) : Float {
        var pos0 = iSpace
        var pos1 = -movableLength - iSpace
        if(vertical){
            pos0 += paddingTop
            pos1 += height - paddingBottom
        }
        else {
            pos0 += paddingLeft
            pos1 += width - paddingRight
        }
        return dx / (pos1 - pos0)
    }
}
