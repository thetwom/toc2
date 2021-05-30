package de.moekadu.metronome

import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

class SoundChooser2(val view: View, val viewModel: MetronomeViewModel, val viewLifecycleOwner: LifecycleOwner) {

    private val constraintLayout: ConstraintLayout = view.findViewById(R.id.metronome_layout)

    private val background = view.findViewById<View>(R.id.sound_chooser_background)

    private val noteChoices = Array<AppCompatImageButton>(8) {
        when (it) {
            0 -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_note_a)
            1 -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_note_c)
            2 -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_note_c_rim)
            3 -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_note_ep)
            4 -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_note_gp)
            5 -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_note_hihat)
            6 -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_note_pause)
            7 -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_note_free)
            else -> throw RuntimeException("Not all notes defined")
        }
    }
    private val noteDurationChoices = Array<AppCompatImageButton>(3) {
        when (it) {
            DURATION_QUARTER -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_duration_quarter)
            DURATION_EIGHTH -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_duration_eighth)
            DURATION_SIXTEENTH -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_duration_sixteenth)
            else -> throw RuntimeException("Not all note durations defined")
        }
    }
    private val tupleChoices = Array<AppCompatImageButton>(3) {
        when (it) {
            TUPLE_OFF -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_duration_normal)
            TUPLE_TRIPLET -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_duration_triplet)
            TUPLE_QUNITUPLET -> view.findViewById<AppCompatImageButton>(R.id.sound_chooser_duration_quintuplet)
            else -> throw RuntimeException("Not all note tuples defined")
        }
    }

    private val volumeControl = view.findViewById<VolumeControl>(R.id.sound_chooser_volume_control)

    private val deleteButton = view.findViewById<ImageButton>(R.id.sound_chooser_delete_button2)
    private val deleteButtonSpacer = view.findViewById<View>(R.id.sound_chooser_delete_button_bottom_spaceholder)

    private val doneButton = view.findViewById<ImageButton>(R.id.sound_chooser_done_button).apply {
        setOnClickListener { deactivate() }
    }

    init {
        noteChoices[0].isActivated = true
    }

    private fun activateStatic(animationDuration: Long = 200L) {
        if (animationDuration > 0) {
            TransitionManager.beginDelayedTransition(constraintLayout,
                AutoTransition().apply {
                    duration = animationDuration
                })
        }
        background.visibility = View.VISIBLE
        doneButton.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE
        deleteButtonSpacer.visibility = View.INVISIBLE // since this is just a spaceholder, invisibility is enough
        volumeControl.visibility = View.VISIBLE

        tupleChoices.forEach { it.visibility = View.VISIBLE }
        noteDurationChoices.forEach { it.visibility = View.VISIBLE }
        noteChoices.forEach { it.visibility = View.VISIBLE }
    }

    private fun deactivate(animationDuration: Long = 200L) {
        if (animationDuration > 0) {
            TransitionManager.beginDelayedTransition(constraintLayout,
                AutoTransition().apply {
                    duration = animationDuration
                })
        }
        background.visibility = View.GONE
        doneButton.visibility = View.GONE
        deleteButton.visibility = View.GONE
        deleteButtonSpacer.visibility = View.GONE
        volumeControl.visibility = View.GONE

        tupleChoices.forEach { it.visibility = View.GONE }
        noteDurationChoices.forEach { it.visibility = View.GONE }
        noteChoices.forEach { it.visibility = View.GONE }
    }

    companion object {
        const val NO_ANIMATION = 0L

        const val TUPLE_OFF = 0
        const val TUPLE_TRIPLET = 1
        const val TUPLE_QUNITUPLET = 2

        const val DURATION_QUARTER = 0
        const val DURATION_EIGHTH = 1
        const val DURATION_SIXTEENTH = 2
    }
}