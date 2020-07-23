package de.moekadu.metronome

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat

class SoundChooserDeleteButton(context : Context) : androidx.appcompat.widget.AppCompatImageButton(context) {

    private var isEmerging = false

    private val animatorY = ObjectAnimator.ofFloat(this, "translationY", 0f)
    init {
        setImageResource(R.drawable.delete_button_icon)
        setBackgroundResource(R.drawable.delete_button_background)
        imageTintList = ContextCompat.getColorStateList(context, R.color.delete_button_icon)
        scaleType = ScaleType.FIT_CENTER
        // TODO: set tints
        animatorY.addListener(object : Animator.AnimatorListener {
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

    fun setSize(width : Int, height : Int) {
        val params = layoutParams
        params.width = width
        params.height = height
        layoutParams = params
    }

    fun emerge(from : Float, to : Float, duration : Long) {
      //  visibility = View.VISIBLE
      //  animate().translationY(height.toFloat()).setDuration(duration)
        isEmerging = true
        animate1(from, to, duration)
    }
    fun disappear(from : Float, to : Float, duration : Long) {
        isEmerging = false
        animate1(from, to, duration)
    }
    private fun animate1(from : Float, to : Float, duration : Long) {
        animatorY.pause()
        animatorY.duration = duration
        animatorY.setFloatValues(from, to)
        animatorY.start()
    }
}