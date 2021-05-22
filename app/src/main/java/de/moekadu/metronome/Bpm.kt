package de.moekadu.metronome

/// Beats per minute for a specific note duration.
/**
 * @param bpm Beats per minute.
 * @param noteDuration Note duration (quarter, eighth, ...) which refers to a beat.
 */
data class Bpm(val bpm: Float, val noteDuration: NoteDuration) {
    /// Quarter notes per minute.
    val bpmQuarter get() = noteDuration.bpmQuarter(bpm)

    /// Duration of a beat in seconds.
    val beatDurationInSeconds get() = 60.0f / bpm

    /// Duration of a quarter note in seconds.
    val quarterDurationInSeconds get() = 60.0f / bpmQuarter

    operator fun plus(bpm: Float) = this.copy(bpm = this.bpm + bpm)
}