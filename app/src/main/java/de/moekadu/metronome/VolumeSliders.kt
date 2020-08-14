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
import android.graphics.Rect
import android.transition.*
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
//import androidx.core.view.setPadding
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class VolumeSliders(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : ViewGroup(context, attrs, defStyleAttr){

//    private val defaultHeight = Utilities.dp2px(300f)
    private val tunerHeightPercent = 0.7f
//    private val defaultTunerWidth = Utilities.dp2px(35f)
//    private val tunerWidthPercent = 0.1f
    private val buttonTunerSpacing = Utilities.dp2px(8f)
    private val tunerSpacing = Utilities.dp2px(4f)
    private val elementPadding = Utilities.dp2px(8f)
    private var activeTranslationZ = 20f
    private val minimumButtonHeight = (Utilities.dp2px(40f)).roundToInt()
    private val minimumButtonWidth = (Utilities.dp2px(70f)).roundToInt()
    private val buttonAspectRatio = 3.0f
    var folded = true
        private set

    private var boundingBoxes = ArrayList<Rect>(0)
    private val volumeControls = ArrayList<VolumeControl>()

    private val button = ImageButton(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        background = AppCompatResources.getDrawable(context, R.drawable.volumeslider_unfold_button_background)
        setImageResource(R.drawable.volumeslider_unfold_button_icon)
        imageTintList =  AppCompatResources.getColorStateList(context, R.color.volumeslider_unfold_button_icon)
        setPadding(0, 0, 0, 0)
        elevation = Utilities.dp2px(2f)
        setOnClickListener {
            if(folded)
                unfold()
            else
                fold()
        }
    }
    val background = ImageButton(context)

    private var sliderColor = Color.BLACK
    private var iconColor = Color.WHITE
    private var backgrounSurfaceColor = Color.WHITE
    private var surfaceBackgroundColor = Color.WHITE
    private var belowSliderColor =  Color.WHITE

    private val noteListChangedListener = object: NoteList.NoteListChangedListener {
        override fun onNoteAdded(note: NoteListItem) {
            noteList?.let { notes ->
                TransitionManager.beginDelayedTransition(
                        this@VolumeSliders,
                        AutoTransition().apply { duration = 300L }
                )

                while (volumeControls.size < notes.size) {
                    val vC = createVolumeControl()
                    addView(vC)
                    volumeControls.add(vC)
                }

                for (i in volumeControls.indices) {
                    volumeControls[i].setVolume(notes[i].volume, 300L)
                }
            }
        }

        override fun onNoteRemoved(note: NoteListItem) {
            noteList?.let { notes ->
                TransitionManager.beginDelayedTransition(
                        this@VolumeSliders,
                        AutoTransition().apply { duration = 300L }
                )

                while (volumeControls.size > notes.size) {
                    val vC = volumeControls.last()
                    removeView(vC)
                    volumeControls.remove(vC)
                }

                for (i in volumeControls.indices) {
                    volumeControls[i].setVolume(notes[i].volume, 300L)
                }
            }
        }

        override fun onNoteMoved(note: NoteListItem) {
            if (volumeControls.size != noteList?.size)
                return
            noteList?.let { notes ->
                for (i in notes.indices)
                    volumeControls[i].setVolume(notes[i].volume, 300L)
            }
        }

        override fun onVolumeChanged(note: NoteListItem) {
            noteList?.let { notes ->
                val index = notes.indexOf(note)
                if (index in 0 until volumeControls.size)
                    volumeControls[index].setVolume(note.volume, 300L)
            }
        }

        override fun onNoteIdChanged(note: NoteListItem) { }
        override fun onDurationChanged(note: NoteListItem) { }
    }

    var noteList: NoteList? = null
        set(value){
            field?.unregisterNoteListChangedListener(noteListChangedListener)
            field = value
            field?.registerNoteListChangedListener(noteListChangedListener)
            for (vC in volumeControls)
                removeView(vC)
            volumeControls.clear()
            value?.let { notes ->
                for (n in notes) {
                    val vC = createVolumeControl()
                    vC.setVolume(n.volume)
                    addView(vC)
                    volumeControls.add(vC)
                }
            }
        }

//    private val unfoldAnimator = ValueAnimator.ofFloat(0f, 1f)
//
//    private val unfoldedButtonTop : Float
//        get() {
//            return bottom - (elementPadding + tunerHeight + buttonTunerSpacing + buttonHeight)
//        }
//
//    private val foldedButtonTop : Float
//        get() {
//            return bottom - elementPadding - buttonHeight
//        }
//
//    private val tunerHeight : Float
//        get() {
//            val maxHeight = height - elementPadding - elementPadding - buttonHeight - buttonTunerSpacing
//            val defaultHeight = min(maxHeight, height * tunerHeightPercent)
//            return min(maxHeight, defaultHeight)
//        }
//
//    private val unfoldedTunerTop : Float
//        get() {
//            return bottom - (elementPadding + tunerHeight)
//        }
//
//    private val foldedTunerTop : Float
//        get() {
//            return max(foldedButtonTop + buttonHeight + buttonTunerSpacing, bottom.toFloat())
//        }
//
//    private val tunerTop : Float
//        get() {
//            return foldingValue * unfoldedTunerTop + (1 - foldingValue) * foldedTunerTop
//        }

    var volumeChangedListener: VolumeChangedListener? = null

    interface VolumeChangedListener {
        fun onVolumeChanged(sliderIdx: Int, volume: Float)
    }

    constructor(context: Context, attrs: AttributeSet? = null)
            :this(context, attrs, R.attr.volumeSlidersStyle)

    init {
//        Log.v("Metronome", "VolumeSliders" + getLeft());
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.VolumeSliders, defStyleAttr, R.style.Widget_AppTheme_VolumeSlidersStyle)
            sliderColor = ta.getColor(R.styleable.VolumeSliders_sliderColor, sliderColor)
            iconColor = ta.getColor(R.styleable.VolumeSliders_iconColor, iconColor)
            backgrounSurfaceColor = ta.getColor(R.styleable.VolumeSliders_backgroundSurfaceColor, backgrounSurfaceColor)
            surfaceBackgroundColor = ta.getColor(R.styleable.VolumeSliders_backgroundColor, surfaceBackgroundColor)
            belowSliderColor = ta.getColor(R.styleable.VolumeSliders_belowSliderColor, belowSliderColor)
            activeTranslationZ = ta.getDimension(R.styleable.VolumeSliders_activeTranslationZ, activeTranslationZ)
            ta.recycle()
        }

//        button = ImageButton(context, attrs)
        addView(button)
        background.setBackgroundColor(surfaceBackgroundColor)
        background.alpha = 0.7f
        addView(background)
        background.visibility = View.GONE
    }

    override fun onMeasure(widthMeasureSpec : Int, heightMeasureSpec : Int) {
        Log.v("Metronome", "VolumeSliders.onMeasure")
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)

        background.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )

        val buttonWidthFolded = max(minimumButtonWidth.toFloat(), measuredWidth / 5.0f).roundToInt()
        val buttonWidthUnfolded = measuredWidth - paddingLeft - paddingRight
        val buttonWidth = if (folded) buttonWidthFolded else buttonWidthUnfolded
        val buttonHeight = max(minimumButtonHeight, (buttonWidthFolded/ buttonAspectRatio).roundToInt())

        button.measure(
                MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(buttonHeight, MeasureSpec.EXACTLY)
        )

        val maxHeight = measuredHeight - elementPadding - buttonHeight
        val defaultHeight = measuredHeight * tunerHeightPercent
        val tunerHeight =  (min(maxHeight, defaultHeight) - elementPadding - buttonTunerSpacing).toInt()

        var tunerWidth = Int.MAX_VALUE
        for(box in boundingBoxes)
            tunerWidth = min((box.width() - tunerSpacing).toInt(), tunerWidth)

        tunerWidth = min((tunerHeight / 7f).toInt(), tunerWidth)


        val volumeControlWidthSpec = MeasureSpec.makeMeasureSpec(tunerWidth, MeasureSpec.EXACTLY)
        val volumeControlHeightSpec = MeasureSpec.makeMeasureSpec(tunerHeight, MeasureSpec.EXACTLY)
        for(v in volumeControls){
            v.measure(volumeControlWidthSpec, volumeControlHeightSpec)
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        background.layout(0, 0, background.measuredWidth, background.measuredHeight)
        val h = b - t
        Log.v("Metronome", "VolumeSliders.onLayout: folded=$folded, volumeControls.size=${volumeControls.size}")
        val buttonTop = if(folded || volumeControls.size == 0) {
            h - (elementPadding + button.measuredHeight).toInt()
        }
        else {
            val tunerHeight = volumeControls[0].measuredHeight
            h - (elementPadding + tunerHeight + buttonTunerSpacing + button.measuredHeight).toInt()
        }
        Log.v("Metronome", "VolumeSliders.onLayout: button.layout($paddingLeft, $buttonTop, ${paddingLeft + button.measuredWidth}, ${buttonTop + button.measuredHeight})")
        button.layout(paddingLeft, buttonTop, paddingLeft + button.measuredWidth, buttonTop + button.measuredHeight
        )

        if (volumeControls.size  > 0) {
            val tunerHeight = volumeControls[0].measuredHeight
            val vT = (h - (elementPadding + tunerHeight)).toInt()
            for (i in volumeControls.indices) {
                if (i < boundingBoxes.size) {
                    val vL = (boundingBoxes[i].centerX() - l - translationX - 0.5f * volumeControls[i].measuredWidth).toInt()
                    volumeControls[i].layout(vL, vT, vL + volumeControls[i].measuredWidth, vT + volumeControls[i].measuredHeight)
                }
            }
        }
    }

//    override fun onFinishInflate() {
//        super.onFinishInflate()
//        post { init() }
//    }

//    override fun onSaveInstanceState() : Parcelable {
////        Log.v("Metronome", "VolumeSliders:onSaveInstanceState");
//        val bundle = Bundle()
//        bundle.putParcelable("superState", super.onSaveInstanceState())
//        bundle.putBoolean("foldedState", folded)
//        return bundle
//    }
//
//    override fun onRestoreInstanceState(state : Parcelable) {
//        var superState = state
//        when(state) {
//            is Bundle -> {
//                folded = state.getBoolean("foldedState")
////            Log.v("Metronome", "Folded: " + folded);
//                foldingValue = if(folded) 0.0f else 1.0f
//                superState = state.getParcelable("superState") ?: state
//            }
//        }
//        super.onRestoreInstanceState(superState)
//    }

//        override fun dispatchSaveInstanceState(container : SparseArray<Parcelable>) {
//            dispatchFreezeSelfOnly(container)
//        }
//
//        override fun dispatchRestoreInstanceState(container : SparseArray<Parcelable>) {
//            dispatchThawSelfOnly(container)
//        }

//        fun init() {
//            translationZ = Utilities.dp2px(24f)
//
//        unfoldAnimator.addUpdateListener { animation ->
//            foldingValue = animation.animatedValue as Float
//            button?.translationY = foldingValue * unfoldedButtonTop + (1 - foldingValue) * foldedButtonTop
//
//            for(v in volumeControls)
//                v.translationY = tunerTop
//            background?.translationY = foldingValue * top + (1-foldingValue) * bottom
//        }

//        MarginLayoutParams params = new MarginLayoutParams(Math.round(Utilities.dp_to_px(90)), buttonHeight);
//        val actualButtonWidth = max(minimumButtonWidth.toFloat(), width / 4.0f)
//        buttonHeight = max(minimumButtonHeight, (actualButtonWidth / buttonAspectRatio).roundToInt())
//        val params = MarginLayoutParams(actualButtonWidth.roundToInt(), buttonHeight)
//        button?.layoutParams = params
//        val pad = (Utilities.dp2px(0f)).roundToInt()
//        button?.setPadding(pad, pad, pad, pad)
//        button?.elevation = Utilities.dp2px(2f)
//        button?.translationX = Utilities.dp2px(4f)
//        button?.translationY = if(folded) foldedButtonTop else unfoldedButtonTop
//        button?.scaleType = ImageView.ScaleType.CENTER_INSIDE
//        button?.setImageResource(if(folded) R.drawable.ic_tune_arrow2 else R.drawable.ic_tune_arrow_down2)
//        button?.setColorFilter(onInteractiveColor)

//        button?.setOnClickListener {
//            folded = if (folded) {
//                unfoldAnimator.start()
//                button?.setImageResource(R.drawable.ic_tune_arrow_down2)
//                false
//            } else {
//                unfoldAnimator.reverse()
//                button?.setImageResource(R.drawable.ic_tune_arrow2)
//                true
//            }
//        }
//
//        addView(button)
//
//
//        background?.setBackgroundColor(surfaceBackgroundColor)
//        params.height = height
//        params.width = width
//        background?.layoutParams = params
//        background?.translationY = if(folded) bottom.toFloat() else top.toFloat()
//        background?.alpha = 0.7f
//        addView(background)
//    }

//    fun setVolumes(volumes: FloatArray, animationDuration: Long = 0L) {
//        require(volumes.size == volumeControls.size)
//        for (i in volumes.indices) {
//            if (volumeControls[i].volume != volumes[i])
//                volumeControls[i].setVolume(volumes[i], animationDuration)
//        }
//    }
//
//    fun setTunersAt(noteBoundingBoxes : Array<Rect>, volumes : FloatArray, animationDuration: Long = 300L) {
//        Log.v("Metronome", " VolumeSliders.setTunersAt, noteBoundingBoxes.size = ${noteBoundingBoxes.size}")
//        require(noteBoundingBoxes.size == volumes.size)
//
//        if(animationDuration > 0L) {
//            val transition = AutoTransition().apply {
//                duration = animationDuration
//            }
//            TransitionManager.beginDelayedTransition(this, transition)
//        }
//
//        boundingBoxes.clear()
//        for(bb in noteBoundingBoxes) {
//            boundingBoxes.add(Rect(bb.left, bb.top, bb.right, bb.bottom))
//        }
//
//        // Delete volume controls which are not required anymore
//        while (volumeControls.size > boundingBoxes.size) {
//            val vC = volumeControls.last()
//            removeView(vC)
//            volumeControls.remove(vC)
//        }
//
//        // Add volume controls
//        while (volumeControls.size < boundingBoxes.size) {
//            val vC = createVolumeControl()
//            addView(vC)
//            volumeControls.add(vC)
//        }
//
//        for (i in volumeControls.indices) {
//            val vC = volumeControls[i]
//            vC.setVolume(volumes[i], animationDuration)
//            if (folded)
//                vC.visibility = View.GONE
//            else
//                vC.visibility = View.VISIBLE
//        }
//
////        if (!folded) {
////            Log.v("Metronome", " VolumeSliders.setTunersAt: requesting layout, num boxes=${boundingBoxes.size}, num volume controls=${volumeControls.size}")
////            //requestLayout()
////        }
//    }

    fun setBoundingBoxes(noteBoundingBoxes : Array<Rect>) {
        Log.v("Metronome", " VolumeSliders.setBoundingBoxes, noteBoundingBoxes.size = ${noteBoundingBoxes.size}")

        boundingBoxes.clear()
        for(bb in noteBoundingBoxes) {
            boundingBoxes.add(Rect(bb.left, bb.top, bb.right, bb.bottom))
        }

        if (!folded && boundingBoxes.size == volumeControls.size)
            requestLayout()
    }

    private fun createVolumeControl() : VolumeControl {
        val volumeControl = VolumeControl(context, null)
        volumeControl.elevation = Utilities.dp2px(2f)
        volumeControl.vertical = true
//        volumeControl.backgroundSurfaceColor = backgrounSurfaceColor
//        volumeControl.sliderColor = sliderColor
//        volumeControl.iconColor = iconColor
//        volumeControl.belowSliderColor = belowSliderColor
        volumeControl.setPadding(0,0,0,0)

        volumeControl.onVolumeChangedListener = object : VolumeControl.OnVolumeChangedListener {
            override fun onVolumeChanged(volume: Float) {
                val index = volumeControls.indexOf(volumeControl)
                noteList?.setVolume(index, volume)
//                volumeChangedListener?.onVolumeChanged(index, volume)
            }
        }

          if (folded)
              volumeControl.visibility = View.GONE
          else
              volumeControl.visibility = View.VISIBLE

        return volumeControl
    }

    private fun fold(animationDuration : Long = 300L) {
        folded = true
        if(animationDuration > 0L) {

            val transition = TransitionSet().apply {
                duration = animationDuration
                addTransition(Slide().apply { slideEdge = Gravity.BOTTOM })
                addTransition(ChangeBounds())

                addListener(object: Transition.TransitionListener {
                    override fun onTransitionEnd(transition: Transition) {
                        translationZ = 0f
                    }
                    override fun onTransitionResume(transition: Transition) { }
                    override fun onTransitionPause(transition: Transition) { }
                    override fun onTransitionCancel(transition: Transition) { }
                    override fun onTransitionStart(transition: Transition) { }
                })

            }

            TransitionManager.beginDelayedTransition(this, transition)
        }
        else {
            translationZ = 0f
        }
        button.isSelected = false
        background.visibility = View.GONE
        for (v in volumeControls)
            v.visibility = View.GONE
    }

    fun unfold(animationDuration : Long = 300L) {
        folded = false
        if(animationDuration > 0L) {
            Log.v("Metronome", "VolumeSliders.unfold: with animation")
            val transition = TransitionSet().apply {
                duration = animationDuration
                addTransition(ChangeBounds())
                addTransition(Slide().apply { slideEdge = Gravity.BOTTOM })
            }
            TransitionManager.beginDelayedTransition(this, transition)
        }
        translationZ = activeTranslationZ
        button.isSelected = true
        background.visibility = View.VISIBLE
        for (v in volumeControls)
            v.visibility = View.VISIBLE
    }
}
