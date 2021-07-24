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

package de.moekadu.metronome

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import kotlin.math.min
import kotlin.math.roundToInt

class DynamicSelection(val buttonSpacing: Int,
                       val backgroundDrawable: Int,
                       val tint: ColorStateList?) {

    private val scaleLargeButton = 2.0f //1.5f
    private val scaleLargeButtonNeighbor = 1.5f //1.2f

    private var lowestButtonBottom = 0

    var translationX = 0f
        set(value) {
            buttons.forEach { it.translationX = value }
            field = value
        }
    private val buttons = ArrayList<ImageButton>()

    val activeButtonIndex get() = buttons.indexOfFirst { it.isActivated }

    fun emerge(animationDuration: Long) {
        buttons.forEach {
            AnimateView.emerge(it, animationDuration)
        }
    }

    fun disappear(animationDuration: Long) {
        buttons.forEach {
            AnimateView.hide(it, animationDuration)
        }
    }

    fun addView(viewGroup: ViewGroup, numViews: Int){

      for (iView in 0 until numViews) {
          val button = ImageButton(viewGroup.context).apply {
              setPadding(0, 0, 0, 0)
              elevation = Utilities.dp2px(3f)
              setBackgroundResource(backgroundDrawable)
              scaleType = ImageView.ScaleType.FIT_CENTER
              imageTintList = tint
              visibility = View.GONE
              pivotX = 0f
              pivotY = 1f
              translationX = this@DynamicSelection.translationX
          }
          buttons.add(button)
          viewGroup.addView(button)
        }
    }

    /// Measure all views contained in the class
    /**
     * @param buttonWidthMax Maximum button width before scaling.
     * @param measuredHeight Vertical space (padding subtracted)
     */
    fun measure(buttonWidthMax: Int, measuredHeight: Int) {
        val buttonHeightMax = ((measuredHeight - (buttons.size - 1) * buttonSpacing)
                / (buttons.size - 3 + scaleLargeButton + 2 * scaleLargeButtonNeighbor)).roundToInt()
        val buttonSize = scaleLargeButton * min(buttonWidthMax, buttonHeightMax)
        val buttonSizeSpec = View.MeasureSpec.makeMeasureSpec(buttonSize.roundToInt(), View.MeasureSpec.EXACTLY)
        buttons.forEach { it.measure(buttonSizeSpec, buttonSizeSpec) }
    }

    fun layout(l: Int, b: Int) {
        buttons.forEach {
            it.layout(l, b, l + it.measuredWidth, b + it.measuredHeight)
        }
        lowestButtonBottom = b

        if (buttons.any{ it.visibility != View.VISIBLE })
            setButtonScalingsAndTranslate(0L)
    }

    private fun setButtonScalingsAndTranslate(animationDuration: Long) {
        var posY = 0f
        buttons.forEachIndexed {  index, button ->
            val scaling = when {
                activeButtonIndex == -1 -> 1f / scaleLargeButton
                index == activeButtonIndex -> 1f
                index == activeButtonIndex - 1 -> scaleLargeButtonNeighbor / scaleLargeButton
                index == activeButtonIndex + 1 -> scaleLargeButtonNeighbor / scaleLargeButton
                else -> 1f / scaleLargeButton
            }
            val buttonHeight = scaling * button.measuredHeight
            posY -= buttonHeight + buttonSpacing
            scaleAndTranslateButton(button, scaling, posY, animationDuration)
        }
    }

    private fun scaleAndTranslateButton(button: View, factor: Float, translation: Float, animationDuration: Long) {
//        Log.v("Metronome", "DynamicSelection.scaleAndTranslateButton: animationDuratio=$animationDuration, visibility=${button.visibility}, VISIBLE=${View.VISIBLE}")
        if (animationDuration == 0L || button.visibility != View.VISIBLE) {
            button.scaleX = factor
            button.scaleY = factor
            button.translationY = translation
        } else {
            button.animate()
                .setInterpolator(OvershootInterpolator())
                .setDuration(animationDuration)
                .scaleX(factor)
                .scaleY(factor)
                .translationY(translation)
        }
    }

    fun setButtonDrawable(index: Int, resourceId: Int) {
        buttons[index].setImageResource(resourceId)
    }

    fun setActiveButton(index: Int, animationDuration: Long) {
        buttons.forEachIndexed { i, button ->
            button.isActivated = (i == index)
        }
        setButtonScalingsAndTranslate(animationDuration)
    }

    fun getButtonIndex(coordinateY: Float): Int {
        var posY = lowestButtonBottom + 0.5 * buttonSpacing
        buttons.forEachIndexed {  index, button ->
            val scaling = when {
                activeButtonIndex == -1 -> 1f / scaleLargeButton
                index == activeButtonIndex -> 1f
                index == activeButtonIndex - 1 -> scaleLargeButtonNeighbor / scaleLargeButton
                index == activeButtonIndex + 1 -> scaleLargeButtonNeighbor / scaleLargeButton
                else -> 1f / scaleLargeButton
            }
            val buttonHeight = scaling * button.measuredHeight
            val bottom = posY
            posY -= buttonHeight + buttonSpacing
            val top = posY
            if ((top <= coordinateY && coordinateY < bottom) ||
                (index == 0 && coordinateY >= top) ||
                (index == buttons.size - 1 && coordinateY < bottom)
            )
                return index
        }
        return -1
    }
}