package de.moekadu.metronome

import android.content.Context
import android.content.res.ColorStateList
import android.opengl.Visibility
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.ImageView
import kotlin.math.roundToInt

class GridSelection(val numRows: Int, val numCols: Int, val buttonSpacing: Int,
                    val drawableTopLeft: Int, val drawableTopRight: Int,
                    val drawableBottomLeft: Int, val drawableBottomRight: Int,
                    val drawableLeft: Int, val drawableRight: Int,
                    val drawableCenter: Int,
                    val tint: ColorStateList?) {

    // TODO: store and load state, deactivate buttons
    fun interface ActiveButtonChangedListener {
        fun onActiveButtonChanged(index: Int)
    }

    var visibility: Int = View.VISIBLE
        set(value) {
            buttons.forEach { it.visibility = value }
            field = value
        }

    var activeButtonChangedListener: ActiveButtonChangedListener? = null

    private val buttons = ArrayList<ImageButton>()

    val activeButtonIndex get() = buttons.indexOfFirst { it.isActivated }

//    private val outlineProvider = object : ViewOutlineProvider() {
//        private val rectInt = Rect()
//        override fun getOutline(view: View?, outline: Outline) {
//            val cornerRad = Utilities.dp2px(3f)
//            rectInt.set(paddingLeft, paddingTop, measuredWidth - paddingRight, measuredHeight - paddingRight)
//            outline.setRoundRect(rectInt, cornerRad)
//        }
//    }

    fun emerge(animationDuration: Long) {
        if (animationDuration == 0L) {
            visibility = View.VISIBLE
        } else {
            buttons.forEach {
                if (it.visibility != View.VISIBLE)
                    it.alpha = 0f
                it.visibility = View.VISIBLE
                it.animate()
                    .setDuration(animationDuration)
                    .alpha(1.0f)
            }
        }
    }

    fun disappear(animationDuration: Long) {
        if (animationDuration == 0L) {
            visibility = View.GONE
        } else {
            buttons.forEach {
                if (it.visibility == View.VISIBLE) {
                    it.animate()
                        .setDuration(animationDuration)
                        .alpha(0f)
                        .withEndAction {
                            it.visibility = View.GONE
                        }
                } else {
                    it.visibility = View.GONE
                }
            }
        }
    }

    fun addView(viewGroup: ViewGroup){
        //setOutlineProvider(outlineProvider)

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
                        setActiveButton(index)
                        activeButtonChangedListener?.onActiveButtonChanged(index)
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    imageTintList = tint
                    visibility = this@GridSelection.visibility
                }
                buttons.add(button)
                viewGroup.addView(button)
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
            }
        }
    }

    fun layout(l: Int, t: Int, r: Int, b: Int) {

        var posY = t
        for (iRow in 0 until numRows) {
            var posX = l
            for (iCol in 0 until numCols) {
                val button = buttons[iRow * numCols + iCol]
                button.layout(posX, posY, posX + button.measuredWidth, posY + button.measuredHeight)
                posX += button.measuredWidth + buttonSpacing
            }
            val button0 = buttons[iRow * numCols]
            posY += button0.measuredHeight + buttonSpacing
        }
    }

    fun setButtonDrawable(index: Int, resourceId: Int) {
        buttons[index].setImageResource(resourceId)
    }

    fun setActiveButton(index: Int) {
        buttons.forEachIndexed { i, button ->
            button.isActivated = (i == index)
        }
    }
}