package de.moekadu.metronome

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class SoundChooserDoneButton(context : Context) : androidx.appcompat.widget.AppCompatButton(context) {

    private var centerX = 0.0f
    private var centerY = 0.0f
    private var sizeXStart = 0
    private var sizeYStart = 0
    private var sizeXEnd = 0
    private var sizeYEnd = 0

    private var sizeXCurrent = 0
    private var sizeYCurrent = 0

    private var isEmerging = true

    private val animator = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
        addUpdateListener {
            val percentage = it.animatedValue as Float
            val newWidth = (1.0f - percentage) * sizeXStart + percentage * sizeXEnd
            val newHeight = (1.0f - percentage) * sizeYStart + percentage * sizeYEnd
            setSize(newWidth.roundToInt(), newHeight.roundToInt())
        }

        addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator?) {
                if(isEmerging)
                    visibility = View.VISIBLE
            }
            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationEnd(animation: Animator?) {
                if(!isEmerging)
                    visibility = View.GONE
            }
            override fun onAnimationRepeat(animation: Animator?) {}
        })
    }

    init {
        text = context.getString(R.string.done)
        setBackgroundResource(R.drawable.done_button_background)
        //setBackgroundResource(R.drawable.delete_button_background)
        setTextColor(ContextCompat.getColorStateList(context, R.color.done_button_text))
        visibility = View.GONE
    }

    fun setSize(newWidth : Int, newHeight : Int) {
        val params = layoutParams
        sizeXCurrent = newWidth
        sizeYCurrent = newHeight
        setCenter(centerX, centerY)

        params.height = newHeight
        params.width = newWidth
        layoutParams = params
    }

    fun setCenter(cX : Float, cY : Float) {
        centerX = cX
        centerY = cY
        translationX = cX - 0.5f * sizeXCurrent
        translationY = cY - 0.5f * sizeYCurrent
    }

    fun emerge(targetWidth : Int, targetHeight : Int, duration : Long) {
        animator.pause()
        isEmerging = true
        sizeXStart = sizeXCurrent
        sizeYStart = sizeYCurrent
        sizeXEnd = targetWidth
        sizeYEnd = targetHeight

        animator.duration = duration
        animator.start()
    }

    fun disappear(duration: Long) {
        animator.pause()
        isEmerging = false
        sizeXStart = width
        sizeYStart = height
        sizeXEnd = 0
        sizeYEnd = 0
        if(visibility == View.GONE) {
            sizeXCurrent = 0
            sizeYCurrent = 0
        }
        else {
            animator.duration = duration
            animator.start()
        }
    }
}