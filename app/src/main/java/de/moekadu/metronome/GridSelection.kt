package de.moekadu.metronome

import android.content.Context
import android.content.res.Resources
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import kotlin.math.roundToInt

class GridSelection(context : Context, attrs : AttributeSet?, defStyleAttr: Int)
    : ViewGroup(context, attrs, defStyleAttr) {
    // TODO: store and load state, deactivate buttons
    interface ActiveButtonChangedListener {
        fun onActiveButtonChanged(index: Int)
    }

    var activeButtonChangedListener: ActiveButtonChangedListener? = null

    private var drawableTopLeft = 0
    private var drawableTopRight = 0
    private var drawableBottomLeft = 0
    private var drawableBottomRight = 0
    private var drawableLeft = 0
    private var drawableRight = 0
    private var drawableCenter = 0

    private var numRows = 1
    private var numCols = 2

    private var buttonSpacing = Utilities.dp2px(1f)

    private val buttons = ArrayList<ImageButton>()

//    private val outlineProvider = object : ViewOutlineProvider() {
//        private val rectInt = Rect()
//        override fun getOutline(view: View?, outline: Outline) {
//            val cornerRad = Utilities.dp2px(3f)
//            rectInt.set(paddingLeft, paddingTop, measuredWidth - paddingRight, measuredHeight - paddingRight)
//            outline.setRoundRect(rectInt, cornerRad)
//        }
//    }
    constructor(context: Context, attrs: AttributeSet? = null) : this(
        context,
        attrs,
        R.attr.gridSelectionStyle
    )

    init {
        //setOutlineProvider(outlineProvider)
        attrs?.let {
            val ta = context.obtainStyledAttributes(
                attrs, R.styleable.GridSelection,
                defStyleAttr, R.style.Widget_AppTheme_SoundChooserStyle3
            )

            drawableTopLeft = ta.getResourceId(R.styleable.GridSelection_backgroundTopLeft, 0)
            drawableTopRight = ta.getResourceId(R.styleable.GridSelection_backgroundTopRight, 0)
            drawableBottomLeft = ta.getResourceId(R.styleable.GridSelection_backgroundBottomLeft, 0)
            drawableBottomRight = ta.getResourceId(R.styleable.GridSelection_backgroundBottomRight, 0)
            drawableLeft = ta.getResourceId(R.styleable.GridSelection_backgroundLeft, 0)
            drawableRight = ta.getResourceId(R.styleable.GridSelection_backgroundRight, 0)
            drawableCenter = ta.getResourceId(R.styleable.GridSelection_backgroundCenter, 0)

            numRows = ta.getInteger(R.styleable.GridSelection_numRows, numRows)
            numCols = ta.getInteger(R.styleable.GridSelection_numCols, numCols)
            buttonSpacing = ta.getDimension(R.styleable.GridSelection_buttonSpacing, buttonSpacing)
            ta.recycle()
        }

        //setBackgroundResource(R.drawable.grid_background_center)

        for (col in 0 until numCols) {
            for (row in 0 until numRows) {
                val index = buttons.size
                val button = ImageButton(context).apply {
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
                }
                buttons.add(button)
                addView(button)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        val buttonWidth = ((measuredWidth - paddingLeft - paddingRight - (numCols - 1) * buttonSpacing) / numCols).roundToInt()
        val buttonHeight = ((measuredHeight - paddingTop - paddingBottom - (numRows - 1) * buttonSpacing) / numRows).roundToInt()
        val buttonWidthSpec = MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY)
        val buttonHeightSpec = MeasureSpec.makeMeasureSpec(buttonHeight, MeasureSpec.EXACTLY)
        buttons.forEach { it.measure(buttonWidthSpec, buttonHeightSpec) }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val buttonWidth = buttons[0].measuredWidth
        val buttonHeight = buttons[0].measuredHeight

        for (iCol in 0 until numCols) {
            for (iRow in 0 until numRows) {
                val button = buttons[iCol * numRows + iRow]
                val buttonLeft = (paddingLeft + iCol * (buttonWidth + buttonSpacing)).roundToInt()
                val buttonTop = (paddingTop + iRow * (buttonHeight + buttonSpacing)).roundToInt()
                button.layout(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight)
            }
        }
    }

    fun setButtonDrawable(index: Int, ressourceId: Int) {
        buttons[index].setImageResource(ressourceId)
    }

    fun setActiveButton(index: Int) {
        buttons.forEachIndexed { i, button ->
            button.isActivated = (i == index)
        }
    }
}