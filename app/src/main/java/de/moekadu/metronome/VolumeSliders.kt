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
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class VolumeSliders(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : FrameLayout(context, attrs, defStyleAttr){

    private val defaultHeight = Utilities.dp2px(300f)
    private val tunerHeightPercent = 0.7f
    private val defaultTunerWidth = Utilities.dp2px(35f)
    private val tunerWidthPercent = 0.1f
    private var tunerWidth = defaultTunerWidth
    private val buttonTunerSpacing = Utilities.dp2px(8f)
    private val tunerSpacing = Utilities.dp2px(4f)
    private val elementPadding = Utilities.dp2px(4f)
    private val minimumButtonHeight = (Utilities.dp2px(40f)).roundToInt()
    private var buttonHeight = minimumButtonHeight
    private val minimumButtonWidth = (Utilities.dp2px(70f)).roundToInt()
    private val buttonAspectRatio = 3.0f
    private var folded = true
    private var foldingValue = 0.0f

    private val volumeControls = ArrayList<VolumeControl>()

    var button : ImageButton? = null
    var background : ImageButton? = null

    private var interactiveColor = Color.BLACK
    private var onInteractiveColor = Color.WHITE
    private var elementBackgroundColor = Color.WHITE
    private var surfaceBackgroundColor = Color.WHITE

    private val unfoldAnimator = ValueAnimator.ofFloat(0f, 1f)


    private val unfoldedButtonTop : Float
        get() {
            return bottom - (elementPadding + tunerHeight + buttonTunerSpacing + buttonHeight)
        }

    private val foldedButtonTop : Float
        get() {
            return bottom - elementPadding - buttonHeight
        }

    private val tunerHeight : Float
        get() {
            val maxHeight = height - elementPadding - elementPadding - buttonHeight - buttonTunerSpacing
            val defaultHeight = min(maxHeight, height * tunerHeightPercent)
            return min(maxHeight, defaultHeight)
        }

    private val unfoldedTunerTop : Float
        get() {
            return bottom - (elementPadding + tunerHeight)
        }

    private val foldedTunerTop : Float
        get() {
            return max(foldedButtonTop + buttonHeight + buttonTunerSpacing, bottom.toFloat())
        }

    private val tunerTop : Float
        get() {
            return foldingValue * unfoldedTunerTop + (1 - foldingValue) * foldedTunerTop
        }

    var volumeChangedListener: VolumeChangedListener? = null

    interface VolumeChangedListener {
        fun onVolumeChanged(sliderIdx: Int, volume: Float)
    }

    constructor(context: Context, attrs: AttributeSet? = null)
            :this(context, attrs, R.attr.volumeSlidersStyle)

    init {
//        Log.v("Metronome", "VolumeSliders" + getLeft());
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.VolumeSliders, defStyleAttr, R.style.VolumeSlidersStyle)
            interactiveColor = ta.getColor(R.styleable.VolumeSliders_interactiveColor, interactiveColor)
            onInteractiveColor = ta.getColor(R.styleable.VolumeSliders_onInteractiveColor, onInteractiveColor)
            elementBackgroundColor = ta.getColor(R.styleable.VolumeSliders_elementBackgroundColor, elementBackgroundColor)
            surfaceBackgroundColor = ta.getColor(R.styleable.VolumeSliders_backgroundColor, surfaceBackgroundColor)
            ta.recycle()
        }
        button = ImageButton(context, attrs)
    }

    override fun onMeasure(widthMeasureSpec : Int, heightMeasureSpec : Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val layoutParams = layoutParams as MarginLayoutParams

        val desiredWidth = Integer.MAX_VALUE
        val desiredHeight = (layoutParams.topMargin + layoutParams.bottomMargin + defaultHeight).roundToInt()

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        post { init() }
    }

    override fun onSaveInstanceState() : Parcelable {
//        Log.v("Metronome", "VolumeSliders:onSaveInstanceState");
        val bundle = Bundle()
        bundle.putParcelable("superState", super.onSaveInstanceState())
        bundle.putBoolean("foldedState", folded)
        return bundle
    }

    override fun onRestoreInstanceState(state : Parcelable) {
        var superState = state
        when(state) {
            is Bundle -> {
                folded = state.getBoolean("foldedState")
//            Log.v("Metronome", "Folded: " + folded);
                foldingValue = if(folded) 0.0f else 1.0f
                superState = state.getParcelable("superState") ?: state
            }
        }
        super.onRestoreInstanceState(superState)
    }

    override fun dispatchSaveInstanceState(container : SparseArray<Parcelable>) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container : SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }

    fun init() {
        translationZ = Utilities.dp2px(24f)

        unfoldAnimator.addUpdateListener { animation ->
            foldingValue = animation.animatedValue as Float
            button?.translationY = foldingValue * unfoldedButtonTop + (1 - foldingValue) * foldedButtonTop

            for(v in volumeControls)
                v.translationY = tunerTop
            background?.translationY = foldingValue * top + (1-foldingValue) * bottom
        }

//        MarginLayoutParams params = new MarginLayoutParams(Math.round(Utilities.dp_to_px(90)), buttonHeight);
        val actualButtonWidth = max(minimumButtonWidth.toFloat(), width / 4.0f)
        buttonHeight = max(minimumButtonHeight, (actualButtonWidth / buttonAspectRatio).roundToInt())
        val params = MarginLayoutParams(actualButtonWidth.roundToInt(), buttonHeight)
        button?.layoutParams = params
        val pad = (Utilities.dp2px(0f)).roundToInt()
        button?.setPadding(pad, pad, pad, pad)
        button?.elevation = Utilities.dp2px(2f)
        button?.translationX = Utilities.dp2px(4f)
        button?.translationY = if(folded) foldedButtonTop else unfoldedButtonTop
        button?.scaleType = ImageView.ScaleType.CENTER_INSIDE
        button?.setImageResource(if(folded) R.drawable.ic_tune_arrow2 else R.drawable.ic_tune_arrow_down2)
        button?.setColorFilter(onInteractiveColor)

        button?.setOnClickListener {
            folded = if (folded) {
                unfoldAnimator.start()
                button?.setImageResource(R.drawable.ic_tune_arrow_down2)
                false
            } else {
                unfoldAnimator.reverse()
                button?.setImageResource(R.drawable.ic_tune_arrow2)
                true
            }
        }

        addView(button)

        background = ImageButton(context)
        background?.setBackgroundColor(surfaceBackgroundColor)
        params.height = height
        params.width = width
        background?.layoutParams = params
        background?.translationY = if(folded) bottom.toFloat() else top.toFloat()
        background?.alpha = 0.7f
        addView(background)
    }

    fun setTunersAt(positions : Vector<Float>, volume : Vector<Float>) {
        tunerWidth = if(positions.size < 2) {
            width * tunerWidthPercent
        }
        else {
            min(width * tunerWidthPercent, positions[1] - positions[0] - tunerSpacing)
        }
        val params = LayoutParams(tunerWidth.roundToInt(), tunerHeight.roundToInt())

        if(BuildConfig.DEBUG && (positions.size != volume.size))
            throw AssertionError("positions and volumeControls have different size")

        for(i in positions.size until volumeControls.size) {
            val vC = volumeControls[i]
            vC.visibility = GONE
        }

        for(i in 0 until positions.size) {
            var vC : VolumeControl?
            var addToView = false
            if(volumeControls.size <= i) {
                val newVC = createVolumeControl()
                volumeControls.add(newVC)
                newVC.translationY = tunerTop
                val currentIdx = volumeControls.size - 1

                newVC.onVolumeChangedListener = object : VolumeControl.OnVolumeChangedListener {
                    override fun onVolumeChanged(volume: Float) {
                        volumeChangedListener?.onVolumeChanged(currentIdx, volume)
                    }
                }

                vC = newVC
                addToView = true
            }
            else {
                vC = volumeControls[i]
            }

            vC.translationX = positions[i] - tunerWidth/2.0f
            vC.visibility = VISIBLE
            vC.volume = volume[i]
            vC.layoutParams = params

            if(addToView)
                addView(vC)
        }

    }

    private fun createVolumeControl() : VolumeControl {

        val volumeControl = VolumeControl(context, null)
        volumeControl.translationY = tunerTop
        volumeControl.elevation = Utilities.dp2px(2f)
        volumeControl.vertical = true
        volumeControl.backgroundSurfaceColor = elementBackgroundColor
        volumeControl.sliderColor = interactiveColor
        volumeControl.iconColor = onInteractiveColor
        val params = MarginLayoutParams(tunerWidth.roundToInt(), tunerHeight.roundToInt())
        volumeControl.setPadding(0,0,0,0)
        volumeControl.layoutParams = params
        return volumeControl
    }
}
