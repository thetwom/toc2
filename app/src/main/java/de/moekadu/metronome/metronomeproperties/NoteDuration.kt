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

package de.moekadu.metronome.metronomeproperties

import kotlin.math.roundToLong

enum class NoteDuration(val baseSubdivision: Int, val tupletSize: Int) {
    SixteenthTriplet(4, 3),
    SixteenthQuintuplet(4, 5),
    Sixteenth(4, 1),
    EighthTriplet(2, 3),
    EighthQuintuplet(2, 5),
    Eighth(2, 1),
    QuarterTriplet(1, 3),
    QuarterQuintuplet(1, 5),
    Quarter(1, 1),
    QuarterSeptuplet(1, 7),
    EighthSeptuplet(2, 7),
    SixteenthSeptuplet(4, 7),
    ThirtySecond(8, 1),
    ThirtySecondTriplet(8, 3),
    ThirtySecondQuintuplet(8, 5),
    ThirtySecondSeptuplet(8, 7);

    /** Number of ordinary notes that take the same time as the whole tuplet. */
    internal val tupletSpan: Int
        get() = when (tupletSize) {
            1 -> 1
            3 -> 2
            else -> 4
        }

    companion object {
        fun of(baseSubdivision: Int, tupletSize: Int): NoteDuration =
            entries.first { it.baseSubdivision == baseSubdivision && it.tupletSize == tupletSize }
    }
}

private fun NoteDuration.scaleQuarter(quarter: Float): Float =
    quarter / baseSubdivision * tupletSpan / tupletSize

fun NoteDuration.durationInSeconds(bpmQuarter: Float) = scaleQuarter(60f / bpmQuarter)
fun NoteDuration.durationInMillis(bpmQuarter: Float) = (1000f * durationInSeconds(bpmQuarter)).roundToLong()
fun NoteDuration.durationInNanos(bpmQuarter: Float) = (1e9f * durationInSeconds(bpmQuarter)).roundToLong()

/** Converts the beats per minute of this duration to quarter notes per minute. */
fun NoteDuration.bpmQuarter(bpm: Float) = scaleQuarter(bpm)
