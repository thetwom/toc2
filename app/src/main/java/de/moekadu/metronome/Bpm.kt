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