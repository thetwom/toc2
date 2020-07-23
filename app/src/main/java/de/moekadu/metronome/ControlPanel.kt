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
import android.util.AttributeSet
import android.view.View

import kotlin.math.*

open class ControlPanel(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
   : View(context, attrs, defStyleAttr) {

    private val innerRadiusRatio = 0.62f

    val radius : Int
        get() {
            val widthNoPadding = width - paddingRight - paddingLeft
            val heightNoPadding = height - paddingTop - paddingBottom
            return min(widthNoPadding, heightNoPadding) / 2
        }

    val innerRadius : Int
        get() {
            return (radius * innerRadiusRatio).roundToInt()
        }

    val centerX : Int
        get() {
            return width / 2
        }
    val centerY : Int
        get() {
            return height / 2
        }

    var textColor = Color.BLACK
    var labelColor = Color.BLACK
    var highlightColor = Color.RED

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, R.attr.controlPanelStyle)

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.ControlPanel, defStyleAttr, R.style.Widget_AppTheme_ControlPanelStyle)
            textColor = ta.getColor(R.styleable.ControlPanel_textColor, textColor)
            labelColor = ta.getColor(R.styleable.ControlPanel_labelColor, labelColor)
            highlightColor = ta.getColor(R.styleable.ControlPanel_highlightColor, highlightColor)

            ta.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val desiredSize = (Utilities.dp2px(200f) + (max(paddingBottom + paddingTop,
                paddingLeft + paddingRight))).roundToInt()

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height: Int
        val width: Int

        if(widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY){
            width = widthSize
            height = heightSize
        }
        else if(widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.AT_MOST){
            width = widthSize
            height = min(heightSize, widthSize)
        }
        else if(widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.EXACTLY){
            width = min(widthSize, heightSize)
            height = heightSize
        }
        else if(widthMode == MeasureSpec.EXACTLY){
            width = widthSize
            //noinspection SuspiciousNameCombination
            height = widthSize
        }
        else if(heightMode == MeasureSpec.EXACTLY){
            //noinspection SuspiciousNameCombination
            width = heightSize
            height = heightSize
        }
        else if(widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST){
            val size = min(desiredSize, min(widthSize, heightSize))
            width = size
            height = size
        }
        else if(widthMode == MeasureSpec.AT_MOST){
            val size = min(desiredSize, widthSize)
            width = size
            height = size
        }
        else if(heightMode == MeasureSpec.AT_MOST){
            val size = min(desiredSize, heightSize)
            width = size
            height = size
        }
        else{
            width = desiredSize
            height = desiredSize
        }

        setMeasuredDimension(width, height)
    }

    fun pTX(phi : Double, rad : Double) : Float {
        return ((rad * cos(phi)) + centerX).toFloat()
    }
    fun pTY(phi : Double, rad : Double) : Float {
        return ((rad * sin(phi)) + centerY).toFloat()
    }
}
