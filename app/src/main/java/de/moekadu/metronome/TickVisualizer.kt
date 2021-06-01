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

@file:Suppress("MemberVisibilityCanBePrivate")

package de.moekadu.metronome

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.*


class TickVisualizer(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : View(context, attrs, defStyleAttr) {

    var color = Color.RED
        set(value) {
            if (field != value) {
                paint.color = value
                paintExplode.color = value
                field = value
                invalidate()
            }
        }

    var vertical = false
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint().apply{
        color = this@TickVisualizer.color
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val paintExplode = Paint().apply{
        color = this@TickVisualizer.color
        style = Paint.Style.FILL
    }

    //private val animator1 = ValueAnimator.ofFloat(0f, PI.toFloat()).apply {
    private val animator1 = ValueAnimator.ofFloat(1f, 0f).apply {
        interpolator = LinearInterpolator()
       // interpolator = AccelerateInterpolator()
        addUpdateListener { invalidate() }
    }

    private val animator2 = ValueAnimator.ofFloat(0f, PI.toFloat()).apply {
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    //private val alphaAnimator = ObjectAnimator.ofObject(this, "backgroundColor", ArgbEvaluator(), Color.GREEN, Color.WHITE)
    private val alphaAnimator = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f)

    private var nextPoint = 1

    private var numBars = 2
    private var currentBar = 0

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, R.attr.tickVisualizerStyle)

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.TickVisualizer, defStyleAttr, R.style.Widget_AppTheme_TickVisualizerStyle)
            color = ta.getColor(R.styleable.TickVisualizer_color, color)
            vertical = ta.getBoolean(R.styleable.TickVisualizer_vertical, vertical)
            ta.recycle()
        }
        //setBackgroundColor(color)
        //alpha = 0f
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (vertical) {
            val lengthTot = (height - paddingTop - paddingBottom).toFloat()
            val lengthBar = lengthTot / numBars
            val yEnd = height - paddingBottom - currentBar * lengthBar
            val yStart = yEnd - lengthBar
            canvas?.drawRect(paddingLeft.toFloat(), yStart, (width - paddingRight).toFloat(), yEnd, paint)
        } else {
            val lengthTot = (width - paddingTop - paddingBottom).toFloat()
            val lengthBar = lengthTot / numBars
            val xStart = paddingLeft + currentBar * lengthBar
            val xEnd = xStart + lengthBar
            canvas?.drawRect(xStart, paddingTop.toFloat(), xEnd, (height - paddingBottom).toFloat(), paint)
        }

//        val markerThickness: Float
//        val pos0: Float
//        val length: Float
//        val lengthTot: Float

//
//        val progress = animator1.animatedValue as Float
//
//        if (vertical) {
//            lengthTot = (height - paddingTop - paddingBottom).toFloat()
//            length = 0.5f * lengthTot * progress
//            markerThickness = (width - paddingLeft - paddingRight).toFloat()
//            pos0 = paddingTop + 0.5f * lengthTot
//        }
//        else {
//            lengthTot = (width - paddingLeft - paddingRight).toFloat()
//            length = 0.5f * lengthTot * progress
//            markerThickness = (height - paddingBottom - paddingTop).toFloat()
//            pos0 = paddingLeft + 0.5f * lengthTot
//        }
//
//        val duration140 = Utilities.bpm2ms(120f)
//        val x = if (animator1.duration < duration140) {
//            paint.alpha = (255 * min(1f, max(0f, 2.0f * progress - 1f))).toInt()
//            0.5f * lengthTot
//        } else {
//            paint.alpha = 255
//            length
//        }
//
//        if (vertical) {
//            canvas?.drawRect(paddingLeft.toFloat(), pos0 - x, paddingLeft + markerThickness, pos0 + x, paint)
//        }
//        else {
//            canvas?.drawRect(pos0 - x, paddingTop.toFloat(), pos0 + x, paddingTop + markerThickness, paint)
//        }



//        val markerLength = 1.5f * markerThickness
//
//        val duration250 = Utilities.bpm2ms(250f)
//        val ampMax = 0.5f * length - markerLength
//        var amp1 = Utilities.dp2px(20f) * max(animator1.duration, duration250) / duration250
//        amp1 = min(ampMax, amp1)
//        var amp2 = Utilities.dp2px(20f) * max(animator2.duration, duration250) / duration250
//        amp2 = min(ampMax, amp2)
//
//        val a1 = amp1 * sin(animator1.animatedValue as Float)
//        val a2 = amp2 * sin(animator2.animatedValue as Float)
//
//        if (vertical) {
//            canvas?.drawRect(paddingLeft.toFloat(), pos0 + a2, paddingLeft + markerThickness, pos0 + a2 + markerLength, paint)
//            canvas?.drawRect(paddingLeft.toFloat(), pos0 - a1 - markerLength, paddingLeft + markerThickness, pos0 - a1, paint)
//        }
//        else {
//            canvas?.drawRect(pos0 + a2, paddingTop.toFloat(), pos0 + a2 + markerLength, paddingTop + markerThickness, paint)
//            canvas?.drawRect(pos0 - a1 - markerLength, paddingTop.toFloat(), pos0 - a1, paddingTop + markerThickness, paint)
//        }
//
//        val maxActiveWidth = 0.5f * length
//        val activeWidth = min(2 * if(nextPoint == 1) amp2 else amp1, maxActiveWidth)
//
//        val activeAnimatorVal = if(nextPoint == 1) animator2.animatedFraction else animator1.animatedFraction
//        val animationDuration = if(nextPoint == 1) animator2.duration else animator1.duration
//        val duration100 = Utilities.bpm2ms(100f)
//
//        if (true) {
//            val activeAnimatorValMod = activeAnimatorVal * animationDuration.toFloat() / min(animationDuration / 2, duration100)
//            paintExplode.alpha = (255 * max(0f, 1f-activeAnimatorValMod)).toInt() //max(0f, (1.0f - activeAnimatorValMod)).toInt())
//            canvas?.drawRect(paddingLeft.toFloat(), paddingTop.toFloat(), (width - paddingRight).toFloat(),
//                (height - paddingTop).toFloat(), paintExplode)
//        } else {
//        val activeAnimatorValMod = activeAnimatorVal * animationDuration.toFloat() / min(animationDuration, duration100)
//
//        val explodeLength = activeWidth * 2f / PI.toFloat() * atan(8 * activeAnimatorValMod)
//        paintExplode.alpha = (255 * max(0f, 1f-activeAnimatorValMod)).toInt() //max(0f, (1.0f - activeAnimatorValMod)).toInt())
////        Log.v("Metronome", "TickVisualizer2:onDraw: explodeWidth=$explodeLength, alpha=${paintExplode.alpha}")
//        if (vertical) {
//            if (nextPoint == 1)
//                canvas?.drawRect(paddingLeft.toFloat(), pos0, paddingLeft + markerThickness, pos0 + explodeLength, paintExplode)
//            else
//                canvas?.drawRect(paddingLeft.toFloat(), pos0 - explodeLength, paddingLeft + markerThickness, pos0, paintExplode)
//        }
//        else {
//            if (nextPoint == 1)
//                canvas?.drawRect(pos0, paddingTop.toFloat(), pos0 + explodeLength, paddingTop + markerThickness, paintExplode)
//            else
//                canvas?.drawRect(pos0 - explodeLength, paddingTop.toFloat(), pos0, paddingTop + markerThickness, paintExplode)
//        }
//      }
    }

    fun tick(duration : Long) {
        currentBar += 1
        if (currentBar == numBars)
            currentBar = 0
        invalidate()
//        animator1.end()
//        animator1.duration = duration
//        animator1.start()

//        alphaAnimator.end()
//        alphaAnimator.duration = duration
//        alphaAnimator.start()

//        if (nextPoint == 1) {
//            animator1.end()
//            animator1.duration = duration
//            animator1.start()
//            nextPoint = 2
//        } else {
//            animator2.end()
//            animator2.duration = duration
//            animator2.start()
//            nextPoint = 1
//        }
    }

    fun stop( ) {
//        nextPoint = 1
    }
}