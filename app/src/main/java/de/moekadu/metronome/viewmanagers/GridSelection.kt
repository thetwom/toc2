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

package de.moekadu.metronome.viewmanagers

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.roundToInt
import de.moekadu.metronome.misc.Utilities

class GridSelection(val numRows: Int, val numCols: Int, val buttonSpacing: Int,
                    val drawableTopLeft: Int, val drawableTopRight: Int,
                    val drawableBottomLeft: Int, val drawableBottomRight: Int,
                    val drawableLeft: Int, val drawableRight: Int,
                    val drawableCenter: Int,
                    val tint: ColorStateList?) {

    fun interface ActiveButtonChangedListener {
        fun onActiveButtonChanged(index: Int)
    }

    var activeButtonChangedListener: ActiveButtonChangedListener? = null

    private val buttons = ArrayList<ImageButton>()
    private val labels = ArrayList<AppCompatTextView>()
    private val deactivatedIndices = ArrayList<Boolean>()

    val size get() = buttons.size

    val activeButtonIndex get() = buttons.indexOfFirst { it.isActivated }

    fun emerge(animationDuration: Long) {
        val alphaDeactivated = 0.5f
        buttons.forEachIndexed { index, button ->
            val alphaEnd = if (deactivatedIndices[index]) alphaDeactivated else 1.0f
            AnimateView.emerge(button, animationDuration, alphaEnd)
            labels.getOrNull(index)?.let { AnimateView.emerge(it, animationDuration, alphaEnd) }
        }
    }

    fun disappear(animationDuration: Long) {
        buttons.forEach {
            AnimateView.hide(it, animationDuration)
        }
        labels.forEach {
            AnimateView.hide(it, animationDuration)
        }
    }

    fun addView(viewGroup: ViewGroup){
      for (row in 0 until numRows) {
          for (col in 0 until numCols) {
                val index = buttons.size
                val button = ImageButton(viewGroup.context).apply {
                    setPadding(0, 0, 0, 0)
                    elevation = Utilities.dp2px(3f)
                    val backgroundId = (when {
                        col == 0 && numRows == 1 -> drawableLeft
                        col == 0 && numRows > 1 && row == 0 -> drawableTopLeft
                        col == 0 && numRows > 1 && row == numRows - 1 -> drawableBottomLeft
                        col == numCols - 1 && numRows == 1 -> drawableRight
                        col == numCols - 1 && numRows > 1 && row == 0 -> drawableTopRight
                        col == numCols - 1 && numRows > 1 && row == numRows - 1 -> drawableBottomRight
                        else -> drawableCenter
                    })
                    setBackgroundResource(backgroundId)
                    setOnClickListener {
                        if (!deactivatedIndices[index]) {
                            setActiveButton(index)
                            activeButtonChangedListener?.onActiveButtonChanged(index)
                        }
                    }
                    setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN)
                            parent.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    imageTintList = tint
                    visibility = View.GONE
                }
                val label = AppCompatTextView(viewGroup.context).apply {
                    textSize = 9f
                    gravity = Gravity.BOTTOM or Gravity.END
                    setPadding(0, 0, Utilities.dp2px(3f).roundToInt(), Utilities.dp2px(1f).roundToInt())
                    setTextColor(tint?.defaultColor ?: Color.BLACK)
                    includeFontPadding = false
                    maxLines = 1
                    isClickable = false
                    isFocusable = false
                    visibility = View.GONE
                }
                buttons.add(button)
                labels.add(label)
                deactivatedIndices.add(false)
                viewGroup.addView(button)
                viewGroup.addView(label)
            }
        }
    }

    fun measure(measuredWidth: Int, measuredHeight: Int) {
        val buttonHorizontalSpace = (measuredWidth - (numCols - 1) * buttonSpacing)
        val buttonVerticalSpace = (measuredHeight - (numRows - 1) * buttonSpacing)
        val buttonWidth = buttonHorizontalSpace / numCols
        val buttonHeight = buttonVerticalSpace / numRows
        val numWiderButtons =  buttonHorizontalSpace % numCols
        val numHigherButtons =  buttonVerticalSpace % numRows
        val buttonWidthSpec = View.MeasureSpec.makeMeasureSpec(buttonWidth, View.MeasureSpec.EXACTLY)
        val buttonHeightSpec = View.MeasureSpec.makeMeasureSpec(buttonHeight, View.MeasureSpec.EXACTLY)
        val buttonWidthSpecWide = View.MeasureSpec.makeMeasureSpec(buttonWidth + 1, View.MeasureSpec.EXACTLY)
        val buttonHeightSpecHigh = View.MeasureSpec.makeMeasureSpec(buttonHeight + 1, View.MeasureSpec.EXACTLY)

        for (iRow in 0 until numRows) {
            val h = if (iRow <= numHigherButtons) buttonHeightSpecHigh else buttonHeightSpec
            for (iCol in 0 until numCols) {
                val w = if (iCol <= numWiderButtons) buttonWidthSpecWide else buttonWidthSpec
                buttons.forEach { it.measure(w, h) }
                labels.forEach { it.measure(w, h) }
            }
        }
    }

    fun layout(l: Int, t: Int) {

        var posY = t
        for (iRow in 0 until numRows) {
            var posX = l
            for (iCol in 0 until numCols) {
                val button = buttons[iRow * numCols + iCol]
                button.layout(posX, posY, posX + button.measuredWidth, posY + button.measuredHeight)
                labels.getOrNull(iRow * numCols + iCol)?.layout(
                    posX, posY, posX + button.measuredWidth, posY + button.measuredHeight
                )
                posX += button.measuredWidth + buttonSpacing
            }
            val button0 = buttons[iRow * numCols]
            posY += button0.measuredHeight + buttonSpacing
        }
    }

    fun setButtonDrawable(index: Int, resourceId: Int) {
        buttons[index].setImageResource(resourceId)
    }

    /** Small text in the lower right corner of the button, to tell the different notes apart. */
    fun setButtonLabel(index: Int, text: String?) {
        val label = labels.getOrNull(index) ?: return
        if (text.isNullOrEmpty()) {
            label.text = null
            label.visibility = View.GONE
        } else {
            label.text = text
        }
    }

    fun deactivateButton(index: Int) {
//        Log.v("Metronome", "GridSelection.deactivateButton")
        deactivatedIndices[index] = true
    }

    fun setActiveButton(index: Int) {
        buttons.forEachIndexed { i, button ->
            button.isActivated = (i == index)
        }
    }
}