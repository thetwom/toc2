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

package de.moekadu.metronome.viewmanagers

import android.view.View
import androidx.appcompat.widget.AppCompatImageButton
import de.moekadu.metronome.metronomeproperties.NoteDuration
import de.moekadu.metronome.R
import de.moekadu.metronome.misc.Utilities

class BeatDurationManager(parent: View) {

    private val beatDuration = parent.findViewById<AppCompatImageButton>(R.id.beat_duration)
    private val sixteenthView = parent.findViewById<AppCompatImageButton>(R.id.beat_duration_choice_sixteenth)
    private val eighthView = parent.findViewById<AppCompatImageButton>(R.id.beat_duration_choice_eighth)
    private val quarterView = parent.findViewById<AppCompatImageButton>(R.id.beat_duration_choice_quarter)
    private val background = parent.findViewById<View>(R.id.beat_duration_background)
    private var dialogVisible = false

    fun interface BeatDurationChangedListener {
        fun changeBeatDuration(duration: NoteDuration)
    }

    var beatDurationChangedListener: BeatDurationChangedListener? = null

    init {
        beatDuration.setOnClickListener {
            if (dialogVisible)
                hideDialog(150L)
            else
                showDialog(150L)
        }
        sixteenthView.setOnClickListener {
            beatDurationChangedListener?.changeBeatDuration(NoteDuration.Sixteenth)
            hideDialog(150L)
        }
        eighthView.setOnClickListener {
            beatDurationChangedListener?.changeBeatDuration(NoteDuration.Eighth)
            hideDialog(150L)
        }
        quarterView.setOnClickListener {
            beatDurationChangedListener?.changeBeatDuration(NoteDuration.Quarter)
            hideDialog(150L)
        }
        background.setOnClickListener {
            hideDialog(150L)
        }
    }

    fun setBeatDuration(duration: NoteDuration) {
        when(duration) {
            NoteDuration.Quarter -> beatDuration.setImageResource(R.drawable.ic_note_duration_quarter)
            NoteDuration.Eighth -> beatDuration.setImageResource(R.drawable.ic_note_duration_eighth)
            NoteDuration.Sixteenth -> beatDuration.setImageResource(R.drawable.ic_note_duration_sixteenth)
            else -> throw RuntimeException("Cannot handle beat duration: $duration")
        }
        quarterView.isActivated = duration == NoteDuration.Quarter
        eighthView.isActivated = duration == NoteDuration.Eighth
        sixteenthView.isActivated = duration == NoteDuration.Sixteenth
    }

    fun showDialog(animationDuration: Long) {
        dialogVisible = true
        val alphaEnd = 0.8f
        AnimateView.emerge(sixteenthView, animationDuration)
        AnimateView.emerge(eighthView, animationDuration)
        AnimateView.emerge(quarterView, animationDuration)

        val animationDurationCorrected = if (background.visibility == View.VISIBLE) 0L else animationDuration
        if (background.visibility != View.VISIBLE)
            background.alpha = 0f
        background.animate()
            .setDuration(animationDurationCorrected)
            .alpha(alphaEnd)
            .withStartAction {
                background.visibility = View.VISIBLE
                beatDuration.translationZ = background.elevation + Utilities.dp2px(1f)
            }
            .withEndAction {  }
    }

    fun hideDialog(animationDuration: Long) {
        dialogVisible = false
        AnimateView.hide(sixteenthView, animationDuration)
        AnimateView.hide(eighthView, animationDuration)
        AnimateView.hide(quarterView, animationDuration)

        val animationDurationCorrected = if (background.visibility != View.VISIBLE) 0L else animationDuration
        background.animate()
            .setDuration(animationDurationCorrected)
            .alpha(0f)
            .withEndAction {
                background.visibility = View.GONE
                beatDuration.translationZ = 0f
            }

    }
}