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

package de.moekadu.metronome.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import de.moekadu.metronome.R
import kotlin.math.*

class PlayButton(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : View(context, attrs, defStyleAttr) {
    companion object {
        const val STATUS_PLAYING = 1
        const val STATUS_PAUSED = 2
    }

    constructor(context : Context, attrs : AttributeSet? = null) : this(context, attrs,
        R.attr.playButtonStyle
    )

    private var buttonStatus = STATUS_PAUSED

    private val paint = Paint()
            .apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = labelColor
            }

    private val pathPlayButton = Path()
            .apply {
                fillType = Path.FillType.EVEN_ODD
            }

    private var playPercentage = 0.0

    private val centerX
        get() = (0.5f * width).toInt()

    private val centerY
        get() = (0.5f * height).toInt()

    interface ButtonClickedListener {
        fun onPlay()
        fun onPause()

        fun onDown() {}
        fun onUp() {}
    }

    var buttonClickedListener : ButtonClickedListener? = null

    private val animateToPlay = ValueAnimator.ofFloat(0.0f, 1.0f)
    private val animateToPause = ValueAnimator.ofFloat(1.0f, 0.0f)
    private var labelColor = Color.BLACK
        set(value) {
            paint.color = value
            field = value
        }

    init {
         attrs?.let {
            val ta = context.obtainStyledAttributes(attrs,
                R.styleable.PlayButton, defStyleAttr,
                R.style.Widget_AppTheme_PlayButtonStyle
            )
            labelColor = ta.getColor(R.styleable.PlayButton_labelColor, labelColor)
            ta.recycle()
        }

        background = AppCompatResources.getDrawable(context, R.drawable.play_button_background)

        animateToPause.duration = 200
        animateToPlay.duration = 200

        animateToPause.addUpdateListener { animation ->
            playPercentage = (animation.animatedValue as Float).toDouble()
            invalidate()
        }

        animateToPlay.addUpdateListener { animation ->
            playPercentage = (animation.animatedValue as Float).toDouble()
            invalidate()
        }
    }

    @Override
    override fun onDraw(canvas : Canvas) {
        super.onDraw(canvas)

        val radius = 0.5f * width
        val triRad = radius * 0.7f

        val xShift = radius * 0.1f
        val rectWidth = radius * 0.4f
        val rectHeight = radius * 1f

        val phiPauseOuter = atan((0.5f * rectHeight) / (xShift + rectWidth))
        val phiPauseInner = atan((0.5f * rectHeight) / xShift)
        val radPauseOuter = sqrt((xShift + rectWidth).pow(2) + (0.5f * rectHeight).pow(2))
        val radPauseInner = sqrt(xShift.pow(2) + (0.5f * rectHeight).pow(2))

        var phi = playPercentage * phiPauseOuter + (1 - playPercentage) * 2.0 * PI
        var r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad
        pathPlayButton.moveTo(pTX(phi, r), pTY(phi, r))

        phi = playPercentage * phiPauseInner + (1 - playPercentage) * 2.0 * PI
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r))

        phi = playPercentage * (-phiPauseInner) + (1 - playPercentage) * 2.0 * PI / 2.0
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad * cos(2.0 * PI / 3.0 + PI)
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r))

        phi = playPercentage * (-phiPauseOuter) + (1 - playPercentage) * 4.0 * PI / 3.0
        r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r))

        pathPlayButton.close()

        phi = playPercentage * (PI - phiPauseOuter) + (1 - playPercentage) * 2.0 * PI
        r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad
        pathPlayButton.moveTo(pTX(phi, r), pTY(phi, r))

        phi = playPercentage * (PI - phiPauseInner) + (1 - playPercentage) * 2.0 * PI
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r))

        phi = playPercentage * (phiPauseInner - PI) + (1 - playPercentage) * 4.0 * PI / 4.0
        r = playPercentage * radPauseInner + (1 - playPercentage) * triRad * cos(4.0 * PI / 3.0 + PI)
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r))

        phi = playPercentage * (phiPauseOuter - PI) + (1 - playPercentage) * 2.0 * PI / 3.0
        r = playPercentage * radPauseOuter + (1 - playPercentage) * triRad
        pathPlayButton.lineTo(pTX(phi, r), pTY(phi, r))

        pathPlayButton.close()

        canvas.drawPath(pathPlayButton, paint)
        pathPlayButton.rewind()
    }

    override fun onTouchEvent(event : MotionEvent) : Boolean {

        val action = event.actionMasked
        val x = event.x - centerX
        val y = event.y - centerY

        val radiusXY = sqrt(x*x + y*y).roundToInt()
        val radius = 0.5f * width
        val bg = background as RippleDrawable?
        bg?.setHotspot(event.x, event.y)

        when(action) {
            MotionEvent.ACTION_DOWN -> {
                return if (radiusXY <= radius) {
                    isPressed = true
                    buttonClickedListener?.onDown()
                    true
                } else {
                    isPressed = false
                    false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                return if (radiusXY <= radius && isPressed) {
                    true
                } else {
                    isPressed = false
                    buttonClickedListener?.onUp()
                    false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (radiusXY <= radius && isPressed) {
                    isPressed = false
                    buttonClickedListener?.onUp()
                    performClick()
                }
                else {
                    isPressed = false
                    return false
                }
            }
        }
        return true
    }

    override fun performClick() : Boolean {
        if (buttonStatus == STATUS_PAUSED) {
            // Log.v("Metronome", "PlayButton:GestureTap:onSingleTapConfirmed() : trigger onPlay");
            buttonClickedListener?.onPlay()
        }
        else {
            // Log.v("Metronome", "PlayButton:GestureTap:onSingleTapConfirmed() : trigger onPause");
            buttonClickedListener?.onPause()
         }
         return super.performClick()
    }


    fun changeStatus(status : Int, animate : Boolean){
        if(buttonStatus == status)
            return

        // Log.v("Metronome", "changeStatus: changing button status");
        buttonStatus = status
        if(status == STATUS_PAUSED) {
            //playPercentage = 0.0
            if(animate) {
                animateToPause.start()
            }
            else {
                playPercentage = 0.0
            }
        }
        else if(status == STATUS_PLAYING) {
            // playPercentage = 1.0
            if(animate) {
                animateToPlay.start()
            }
            else {
                playPercentage = 1.0
            }
        }
        invalidate()
    }

    private fun pTX(phi : Double, rad : Double) : Float {
        return ((rad * cos(phi)) + centerX).toFloat()
    }

    private fun pTY(phi : Double, rad : Double) : Float {
        return ((rad * sin(phi)) + centerY).toFloat()
    }
}