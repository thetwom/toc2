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
import android.graphics.drawable.Drawable
import android.text.TextUtils
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
    private var layoutColumns = numCols

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
                    // the button below has an elevation of 3dp, the label must be drawn above it
                    elevation = Utilities.dp2px(4f)
                    textSize = 9f
                    gravity = Gravity.BOTTOM or Gravity.END
                    setPadding(0, 0, Utilities.dp2px(3f).roundToInt(), Utilities.dp2px(1f).roundToInt())
                    setTextColor(tint?.defaultColor ?: Color.BLACK)
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
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
        updateBackgrounds()
    }

    private fun updateBackgrounds() {
        val rows = size / layoutColumns
        buttons.forEachIndexed { index, button ->
            val col = index % layoutColumns
            val row = index / layoutColumns
            val background = when {
                col == 0 && rows == 1 -> drawableLeft
                col == 0 && row == 0 -> drawableTopLeft
                col == 0 && row == rows - 1 -> drawableBottomLeft
                col == layoutColumns - 1 && rows == 1 -> drawableRight
                col == layoutColumns - 1 && row == 0 -> drawableTopRight
                col == layoutColumns - 1 && row == rows - 1 -> drawableBottomRight
                else -> drawableCenter
            }
            button.setBackgroundResource(background)
            button.setPadding(0, 0, 0, 0)
        }
    }

    fun measure(measuredWidth: Int, measuredHeight: Int, columns: Int = numCols) {
        require(columns > 0 && size % columns == 0)
        if (layoutColumns != columns) {
            layoutColumns = columns
            updateBackgrounds()
        }
        val rows = size / columns
        val buttonHorizontalSpace = (measuredWidth - (columns - 1) * buttonSpacing).coerceAtLeast(0)
        val buttonVerticalSpace = (measuredHeight - (rows - 1) * buttonSpacing).coerceAtLeast(0)
        buttons.forEachIndexed { index, button ->
            val col = index % columns
            val row = index / columns
            val width = buttonHorizontalSpace / columns + if (col < buttonHorizontalSpace % columns) 1 else 0
            val height = buttonVerticalSpace / rows + if (row < buttonVerticalSpace % rows) 1 else 0
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            button.measure(widthSpec, heightSpec)
            labels[index].measure(widthSpec, heightSpec)
        }
    }

    fun layout(l: Int, t: Int) {
        var posY = t
        for (row in 0 until size / layoutColumns) {
            var posX = l
            val first = row * layoutColumns
            for (index in first until first + layoutColumns) {
                val button = buttons[index]
                button.layout(posX, posY, posX + button.measuredWidth, posY + button.measuredHeight)
                labels[index].layout(
                    posX, posY, posX + button.measuredWidth, posY + button.measuredHeight
                )
                posX += button.measuredWidth + buttonSpacing
            }
            posY += buttons[first].measuredHeight + buttonSpacing
        }
    }

    fun setButtonDrawable(index: Int, resourceId: Int) {
        buttons[index].setImageResource(resourceId)
    }

    fun setButtonDrawable(index: Int, drawable: Drawable?) {
        buttons[index].setImageDrawable(drawable)
    }

    fun setButtonDescription(index: Int, description: CharSequence) {
        buttons[index].contentDescription = description
    }

    /** Small text in the lower right corner of the button, to tell the different notes apart. */
    fun setButtonLabel(index: Int, text: String?) {
        val label = labels.getOrNull(index) ?: return
        if (text.isNullOrEmpty()) {
            label.text = null
            label.visibility = View.GONE
        } else {
            label.text = text
            // in case the label is created after the grid was shown already
            if (buttons[index].visibility == View.VISIBLE)
                label.visibility = View.VISIBLE
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
