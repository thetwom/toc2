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