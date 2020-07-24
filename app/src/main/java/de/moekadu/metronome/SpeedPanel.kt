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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.*


class SpeedPanel(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : ControlPanel(context, attrs, defStyleAttr) {


    private val circlePaint = Paint()

    private var integratedDistance = 0f
    private var previousX = 0f
    private var previousY = 0f

    private val pathOuterCircle = Path()
    private val tapInPath = Path()
    private val plusStepPath = Path()
    private val minusStepPath = Path()

    private var changingSpeed = false

    var sensitivity = InitialValues.speedSensitivity // steps per cm

    private val tapInAnimation = ValueAnimator.ofFloat(0f, 1f)
    private var tapInAnimationValue = 1.0f

    private val backToZeroAnimation = ValueAnimator.ofFloat(1.0f, 0.0f)
    private var backToZeroAnimationValue = 1.0f

    private var lastTap = 0L
    private var predictNextTap = 0L
    private var nTapSamples = 0
    private val facTapInfty = 0.15f
    private val maxTapErr = 0.3f
    private val tapDelay = 10L
    private var dt = 0f

    private val tapInAngleStart = 60f
    private val tapInAngleEnd = 120f

    private val plusStepAngleStart = -90f
    private val plusStepAngleEnd = 0f
    private val minusStepAngleStart = -180f
    private val minusStepAngleEnd = -90f

    private var plusStepInitiated = false
    private var minusStepInitiated = false

    private var stepCounter = 0f
    private val stepCounterMax = 5f

    var speedIncrement = Utilities.speedIncrements[InitialValues.speedIncrementIndex]
        set(value) {
            field = value
            invalidate()
        }

    interface SpeedChangedListener {
        fun onSpeedChanged(dSpeed: Float)
        fun onAbsoluteSpeedChanged(newSpeed: Float, nextKlickTimeInMillis: Long)
    }

    var speedChangedListener: SpeedChangedListener? = null

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, R.attr.controlPanelStyle)

    init {

        circlePaint.isAntiAlias = true

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view : View, outline : Outline) {
                outline.setOval(centerX - radius, centerY - radius,
                        centerX + radius, centerY + radius)
            }
        }
        outlineProvider = outlineProvider

        tapInAnimation.duration = 500
        tapInAnimation.addUpdateListener { animation ->
            tapInAnimationValue = animation.animatedValue as Float
            invalidate()
        }

        backToZeroAnimation.duration = 100
        backToZeroAnimation.addUpdateListener { animation ->
            backToZeroAnimationValue = animation.animatedValue as Float
            invalidate()
        }
    }

    override fun onDraw(canvas : Canvas) {
        super.onDraw(canvas)
//        Log.v("Metronome", "SpeedPanel:onDraw : ")
        val maxShift = 10f // degree
        val shiftSpeedStrokesAngle = maxShift * stepCounter / stepCounterMax * backToZeroAnimationValue

        //circlePaint.setColor(normalColor);
        circlePaint.color = backgroundTintList?.defaultColor ?: Color.WHITE

        circlePaint.style = Paint.Style.FILL
        pathOuterCircle.fillType = Path.FillType.EVEN_ODD

        pathOuterCircle.rewind()
        pathOuterCircle.addCircle(centerX.toFloat(), centerY.toFloat(), radius.toFloat(), Path.Direction.CW)

        canvas.drawPath(pathOuterCircle, circlePaint)
        pathOuterCircle.rewind()

        if(changingSpeed) {
            circlePaint.color = highlightColor
        }
        else {
            circlePaint.color = labelColor
        }
        circlePaint.style = Paint.Style.STROKE
        val growthFactor = 1.1f
        val speedRad = 0.5f* (radius + innerRadius)
        val strokeWidth = 0.3f * (radius - innerRadius)
        circlePaint.strokeWidth = strokeWidth

        val angleMax  = -90.0f + 40.0f + shiftSpeedStrokesAngle
        val angleMin = -90.0f - 40.0f + shiftSpeedStrokesAngle
        var dAngle = 3.0f

        var angle = angleMax - dAngle

        while (angle >= angleMin) {
            canvas.drawArc(centerX - speedRad, centerY - speedRad,
                    centerX + speedRad, centerY + speedRad,
                    angle, -2f, false, circlePaint)

            dAngle *= growthFactor
            angle -= dAngle
        }

        circlePaint.style = Paint.Style.FILL

        val radArrI = speedRad - 0.5f * strokeWidth
        val radArrO = speedRad + 0.5f * strokeWidth
        val angleMinRad = angleMin * PI.toFloat() / 180.0f
        val dArrAngle = strokeWidth / speedRad
        pathOuterCircle.moveTo(centerX + radArrI * cos(angleMinRad), centerY + radArrI * sin(angleMinRad))
        pathOuterCircle.lineTo(centerX + radArrO * cos(angleMinRad), centerY + radArrO * sin(angleMinRad))
        pathOuterCircle.lineTo(centerX + speedRad * cos(angleMinRad-dArrAngle), centerY + speedRad * sin(angleMinRad-dArrAngle))
        canvas.drawPath(pathOuterCircle, circlePaint)
        pathOuterCircle.rewind()

        val angleMaxRad = angleMax * PI.toFloat() / 180.0f
        pathOuterCircle.moveTo(centerX + radArrI * cos(angleMaxRad), centerY + radArrI * sin(angleMaxRad))
        pathOuterCircle.lineTo(centerX + radArrO * cos(angleMaxRad), centerY + radArrO * sin(angleMaxRad))
        pathOuterCircle.lineTo(centerX + speedRad * cos(angleMaxRad+dArrAngle), centerY + speedRad * sin(angleMaxRad+dArrAngle))
        canvas.drawPath(pathOuterCircle, circlePaint)

        tapInPath.rewind()
        tapInPath.addArc(centerX - speedRad, centerY - speedRad, centerX + speedRad,
                centerY + speedRad, 265f, -350f)

        circlePaint.style = Paint.Style.FILL
        circlePaint.textAlign = Paint.Align.CENTER
        circlePaint.textSize = strokeWidth

        circlePaint.color = textColor

        canvas.drawTextOnPath(context.getString(R.string.tap_in), tapInPath, 0f, strokeWidth /2.0f, circlePaint)

        plusStepPath.rewind()
        plusStepPath.addArc(centerX - speedRad, centerY - speedRad,
                centerX + speedRad, centerY + speedRad, angleMax + 20, 180f)
        circlePaint.textAlign = Paint.Align.LEFT
        if(plusStepInitiated)
            circlePaint.color = highlightColor
        else
            circlePaint.color = textColor
        canvas.drawTextOnPath("+ "+Utilities.getBpmString(speedIncrement, speedIncrement),
                plusStepPath, 0f, strokeWidth /2.0f, circlePaint)

        minusStepPath.rewind()
        minusStepPath.addArc(centerX - speedRad, centerY - speedRad,
                centerX + speedRad, centerY + speedRad, angleMin - 20 - 180, 180f)
        circlePaint.textAlign = Paint.Align.RIGHT
        if(minusStepInitiated)
            circlePaint.color = highlightColor
        else
            circlePaint.color = textColor
        canvas.drawTextOnPath("- "+Utilities.getBpmString(speedIncrement, speedIncrement),
                minusStepPath, 0f, strokeWidth /2.0f, circlePaint)

        if(tapInAnimationValue <= 0.99999) {
            circlePaint.textAlign = Paint.Align.CENTER
            circlePaint.color = highlightColor
            circlePaint.alpha = (255*(1-tapInAnimationValue)).roundToInt()
            val highlightTextSize = strokeWidth * (1 + 10 * tapInAnimationValue)
            circlePaint.textSize = highlightTextSize
            canvas.drawTextOnPath(context.getString(R.string.tap_in), tapInPath,
                    0f, strokeWidth / 2.0f, circlePaint)
        }
    }

    override fun onTouchEvent(event : MotionEvent) : Boolean {

        val action = event.actionMasked
        val x = event.x - centerX
        val y = event.y - centerY

        val radiusXY = (sqrt(x*x + y*y)).roundToInt()

        when(action) {
            MotionEvent.ACTION_DOWN -> {
                if (radiusXY > radius * 1.1) {
                    return false
                }

                val angle = 180.0 * atan2(y, x) / PI
                plusStepInitiated = false
                minusStepInitiated = false

                if (angle > tapInAngleStart && angle < tapInAngleEnd) {
                    evaluateTapInTimes()
                    tapInAnimation.start()
                }
                else if (angle > plusStepAngleStart && angle < plusStepAngleEnd) {
                    plusStepInitiated = true
                }
                else if (angle > minusStepAngleStart && angle < minusStepAngleEnd) {
                    minusStepInitiated = true
                }
                else {
                    changingSpeed = true
                }

                backToZeroAnimation.cancel()
                backToZeroAnimationValue = 1.0f
                stepCounter = 0f
                integratedDistance = 0.0f
                previousX = x
                previousY = y

                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x -previousX
                val dy = y -previousY

                val dSpeed = - (dx * y - dy * x) / sqrt(x * x + y * y)
                integratedDistance += dSpeed
                previousX = x
                previousY = y
                val speedSteps = sensitivity * Utilities.px2cm(integratedDistance)

                if (changingSpeed) {
//                    Log.v("Metronome", "SpeedPanel:onTouchEvent: integratedDistance="+integratedDistance + "  " + Utilities.px2cm(integratedDistance));
                    if (abs(speedSteps) >= 1 && speedChangedListener != null) {
                        speedChangedListener?.onSpeedChanged(speedSteps * speedIncrement)
                        integratedDistance = 0.0f
                        stepCounter += speedSteps
                        stepCounter = min(stepCounter, stepCounterMax)
                        stepCounter = max(stepCounter, -stepCounterMax)
                        invalidate()
                    }
                }
                else if (abs(speedSteps) >= 1) {
                    changingSpeed = true
                    plusStepInitiated = false
                    minusStepInitiated = false
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (plusStepInitiated) {
                    speedChangedListener?.onSpeedChanged(speedIncrement)
                }
                else if (minusStepInitiated) {
                    speedChangedListener?.onSpeedChanged(-speedIncrement)
                }

                changingSpeed = false
                plusStepInitiated = false
                minusStepInitiated = false
                backToZeroAnimation.start()
                invalidate()
            }
        }
        return true
    }

    private fun evaluateTapInTimes() {

        val currentTap = SystemClock.uptimeMillis()
        nTapSamples += 1

        val currentDt = currentTap - lastTap
        lastTap = currentTap

        if(nTapSamples == 1) {
            dt = currentDt.toFloat()
            return
        }

//        Log.v("Metronome", "SpeedPanel:computeSpeedFromTapIn:  err=" + ((currentDt - dt) / dt));
        if(abs(currentDt - dt) / dt > maxTapErr) {
            nTapSamples = 2
        }

        val fac = facTapInfty + (1.0f - facTapInfty) / (nTapSamples - 1)
        dt = fac * currentDt + (1.0f - fac) * dt

        predictNextTap = (fac * (currentTap - predictNextTap) + predictNextTap + dt).roundToLong()
//        Log.v("Metronome", "Speedpanel:computeSpeedFromTapIn:  fac=" + fac + "  dt=" + dt);

        if(nTapSamples >= 3) {
            speedChangedListener?.onAbsoluteSpeedChanged(
                    Utilities.dt2speed(dt.roundToLong()), (predictNextTap + tapDelay))
        }
    }
}