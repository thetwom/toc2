package de.moekadu.metronome

import android.animation.Animator
import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.View
import kotlin.math.roundToInt

class SoundChooserChoiceButton(context : Context, noteID : Int) {

    val button = NoteView(context).apply {
        val buttonNoteList = NoteList()
        buttonNoteList.add(NoteListItem(noteID, 0.0f, 0.0f))
        noteList = buttonNoteList
    }

    private val startBounds = Rect()
    private val targetBounds = Rect()
    private val currentBounds = Rect()
    private val animator = ValueAnimator.ofFloat(0f, 1f)
    private val rectEvaluator = RectEvaluator(currentBounds)

    var doNotAnimateX = false

    val noteId = button.noteList?.get(0)?.id ?: defaultNote

    var left = 0
        set(value) {
            field = value
            targetBounds.left = value
            targetBounds.right = value + width
        }
    var top = 0
        set(value) {
            field = value
            targetBounds.top = value
            targetBounds.bottom = value + height
        }
    var width = 1
        set(value) {
            field = value
            targetBounds.right = targetBounds.left + field
        }
    var height = 1
        set(value) {
            field = value
            targetBounds.bottom = targetBounds.top + field
        }
    val centerY
        get() = (top + 0.5f * height).roundToInt()

    init {
        button.setBackgroundResource(R.drawable.choice_button_background)
        animator.addUpdateListener {
//                Log.v("Notes", "value = " + (it.animatedValue as Float))
            rectEvaluator.evaluate(it.animatedValue as Float, startBounds, targetBounds)
            setPositionAndSize(currentBounds)
        }
    }

    private fun setPositionAndSize(rect : Rect) {
        val params = button.layoutParams
        params.height = rect.height()
        params.width = rect.width()
        button.layoutParams = params
        if(!doNotAnimateX)
            button.translationX = rect.left.toFloat()
        else
            button.translationX = targetBounds.left.toFloat()
        button.translationY = rect.top.toFloat()
    }

    fun dissolve(duration : Long) {
        animator.pause()
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {}

            override fun onAnimationEnd(animation: Animator?) {
                button.visibility = View.GONE
                animator.removeListener(this)
            }

            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationStart(animation: Animator?) {}
        })
        width = 0
        height = 0
        animator.duration = duration
        animator.start()
    }

    fun animateToTarget(duration : Long) {
        animator.pause()

        if (duration == 0L)
        {
            setPositionAndSize(targetBounds)
        }
        else {
            animator.duration = duration
            startBounds.left = button.translationX.roundToInt()
            startBounds.top = button.translationY.roundToInt()
            startBounds.right = startBounds.left + button.width
            startBounds.bottom = startBounds.top + button.height

            animator.start()
        }
    }
}