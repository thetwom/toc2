package de.moekadu.metronome

import android.animation.*
import android.content.Context
import android.graphics.Rect
import kotlin.math.roundToInt

class SoundChooserControlButton(context: Context) : NoteView(context) {

    private val positionAnimatorListener = object : Animator.AnimatorListener {
        override fun onAnimationEnd(animation: Animator?) {
            if(!vanishAnimator.isRunning && !animateZ.isRunning)
                allAnimationsEndListener?.onAnimationEnd()
        }
        override fun onAnimationCancel(animation: Animator?) {}
        override fun onAnimationStart(animation: Animator?) {}
        override fun onAnimationRepeat(animation: Animator?) {}
    }

    private val animateZAnimatorListener = object : Animator.AnimatorListener {
        override fun onAnimationEnd(animation: Animator?) {
            if(!vanishAnimator.isRunning && !positionAnimator.isRunning)
                allAnimationsEndListener?.onAnimationEnd()
        }
        override fun onAnimationCancel(animation: Animator?) {}
        override fun onAnimationStart(animation: Animator?) {}
        override fun onAnimationRepeat(animation: Animator?) {}
    }

    private val vanishAnimatorListener = object : Animator.AnimatorListener {
        override fun onAnimationEnd(animation: Animator?) {
            if(!animateZ.isRunning && !positionAnimator.isRunning)
                allAnimationsEndListener?.onAnimationEnd()
        }
        override fun onAnimationCancel(animation: Animator?) {}
        override fun onAnimationStart(animation: Animator?) {}
        override fun onAnimationRepeat(animation: Animator?) {}
    }

    private val animateX = ObjectAnimator.ofFloat(this, "translationX", 0f, 0f)
    private val animateY = ObjectAnimator.ofFloat(this, "translationY", 0f, 0f)
    private val positionAnimator = AnimatorSet().apply {
        playTogether(animateX, animateY)
        duration = 300L
    }

    private val animateZ = ObjectAnimator.ofFloat(this, "translationZ", 0f, 10f).apply {
        duration = 200L
    }

    private val currentBounds = Rect()
    private val startBounds = Rect()
    private val targetBounds = Rect()
    private val vanishAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300L
    }
    private val vanishRectEvaluator = RectEvaluator(currentBounds)

    interface AllAnimationsEndListener {
        fun onAnimationEnd()
    }

    var allAnimationsEndListener : AllAnimationsEndListener ?= null

    init {
        setBackgroundResource(R.drawable.control_button_background)

        vanishAnimator.addUpdateListener {
            vanishRectEvaluator.evaluate(it.animatedValue as Float, startBounds, targetBounds)
            setSize(currentBounds.width(), currentBounds.height())
            translationX = currentBounds.left.toFloat()
            translationY = currentBounds.top.toFloat()
        }

        vanishAnimator.addListener(vanishAnimatorListener)
        positionAnimator.addListener(positionAnimatorListener)
        animateZ.addListener(animateZAnimatorListener)
    }

    fun setSize(newWidth : Int, newHeight : Int) {
        val params = layoutParams
        params.height = newHeight
        params.width = newWidth
        layoutParams = params
    }

    fun animateTo(targetLeft : Float, targetTop : Float) {
        vanishAnimator.pause()
        positionAnimator.pause()
        animateX.setFloatValues(translationX, targetLeft)
        animateY.setFloatValues(translationY, targetTop)
        positionAnimator.start()
    }

    fun vanish() {
        vanishAnimator.pause()
        positionAnimator.pause()
        val lp = layoutParams
        startBounds.left = translationX.roundToInt()
        startBounds.top = translationY.roundToInt()
        startBounds.right = startBounds.left + width
        startBounds.bottom = startBounds.top + height
        targetBounds.left = startBounds.centerX()
        targetBounds.right = startBounds.centerX()
        targetBounds.top = startBounds.centerY()
        targetBounds.bottom = startBounds.centerY()
        vanishAnimator.start()
    }

    fun elevate(value : Float) {
        animateZ.pause()
        animateZ.setFloatValues(translationZ, value)
        animateZ.start()
    }

    fun stopAllAnimations() {
        vanishAnimator.pause()
        positionAnimator.pause()
        animateZ.pause()
    }
}