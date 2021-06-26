package de.moekadu.metronome

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageButton

class BeatDurationManager(parent: View) {

    private val beatDuration = parent.findViewById<AppCompatImageButton>(R.id.beat_duration)
    private val sixteenthView = parent.findViewById<AppCompatImageButton>(R.id.beat_duration_choice_sixteenth)
    private val eighthView = parent.findViewById<AppCompatImageButton>(R.id.beat_duration_choice_eighth)
    private val quarterView = parent.findViewById<AppCompatImageButton>(R.id.beat_duration_choice_quarter)

    fun interface BeatDurationChangedListener {
        fun changeBeatDuration(duration: NoteDuration)
    }

    var beatDurationChangedListener: BeatDurationChangedListener? = null

    init {
        beatDuration.setOnClickListener {
            showDialog(200L)
        }
        sixteenthView.setOnClickListener {
            beatDurationChangedListener?.changeBeatDuration(NoteDuration.Sixteenth)
            hideDialog(200L)
        }
        eighthView.setOnClickListener {
            beatDurationChangedListener?.changeBeatDuration(NoteDuration.Eighth)
            hideDialog(200L)
        }
        quarterView.setOnClickListener {
            beatDurationChangedListener?.changeBeatDuration(NoteDuration.Quarter)
            hideDialog(200L)
        }
    }

    fun setBeatDuration(duration: NoteDuration) {
        when(duration) {
            NoteDuration.Quarter -> beatDuration.setImageResource(R.drawable.ic_note_duration_quarter)
            NoteDuration.Eighth -> beatDuration.setImageResource(R.drawable.ic_note_duration_eighth)
            NoteDuration.Sixteenth -> beatDuration.setImageResource(R.drawable.ic_note_duration_sixteenth)
            else -> throw RuntimeException("Cannot handle beat duration: $duration")
        }
    }

    fun showDialog(animationDuration: Long) {
        showButton(sixteenthView, animationDuration)
        showButton(eighthView, animationDuration)
        showButton(quarterView, animationDuration)
    }

    fun hideDialog(animationDuration: Long) {
        hideButton(sixteenthView, animationDuration)
        hideButton(eighthView, animationDuration)
        hideButton(quarterView, animationDuration)
    }

    private fun showButton(view: View, animationDuration: Long) {
        if (view.visibility == View.VISIBLE && view.alpha == 1f)
            return
        if (animationDuration > 0L) {
            if (view.visibility != View.VISIBLE)
                view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate().setDuration(animationDuration).alpha(1f)
        } else {
            view.visibility = View.VISIBLE
            view.alpha = 1f
        }
    }

    private fun hideButton(view: View, animationDuration: Long) {
        if (view.visibility != View.VISIBLE)
            return
        if (animationDuration > 0L) {
            view.animate().setDuration(animationDuration)
                .alpha(0f)
                .withEndAction{view.visibility = View.GONE}
        } else {
            view.visibility = View.GONE
        }
    }
}