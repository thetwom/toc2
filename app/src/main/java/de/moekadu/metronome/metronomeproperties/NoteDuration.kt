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

enum class NoteDuration {
    SixteenthTriplet,  // Duration = Sixteenth * 2.0 / 3.0
    SixteenthQuintuplet, // Duration = Sixteenth * 4.0 / 5.0
    SixteenthSeptuplet, // Duration = Sixteenth * 6.0 / 7.0
    Sixteenth,
    EighthTriplet,  // Duration = Eighth * 2.0 / 3.0
    EighthQuintuplet, // Duration = Eighth * 4.0 / 5.0
    EighthSeptuplet, // Duration = Eighth * 6.0 / 7.0
    Eighth,
    QuarterTriplet, // Duration = Quarter * 2.0 / 3.0
    QuarterQuintuplet, // Duration = Quarter * 4.0 / 5.0
    QuarterSeptuplet, // Duration = Quarter * 6.0 / 7.0
    Quarter,
    ThirtySecondTriplet, // Duration = ThirtySecond * 2.0 / 3.0
    ThirtySecondQuintuplet, // Duration = ThirtySecond * 4.0 / 5.0
    ThirtySecondSeptuplet, // Duration = ThirtySecond * 6.0 / 7.0
    ThirtySecond,
}

private fun bpmToSeconds(bpm: Float) = (60.0f / bpm)

/** Length of one note of the given duration in quarter notes. */
private fun NoteDuration.lengthInQuarter() =
    when(this) {
        NoteDuration.Quarter -> 1.0f
        NoteDuration.QuarterTriplet -> 2.0f / 3.0f
        NoteDuration.QuarterQuintuplet -> 4.0f / 5.0f
        NoteDuration.QuarterSeptuplet -> 6.0f / 7.0f
        NoteDuration.Eighth -> 1.0f / 2.0f
        NoteDuration.EighthTriplet -> 1.0f / 2.0f * 2.0f / 3.0f
        NoteDuration.EighthQuintuplet -> 1.0f / 2.0f * 4.0f / 5.0f
        NoteDuration.EighthSeptuplet -> 1.0f / 2.0f * 6.0f / 7.0f
        NoteDuration.Sixteenth -> 1.0f / 4.0f
        NoteDuration.SixteenthTriplet -> 1.0f / 4.0f * 2.0f / 3.0f
        NoteDuration.SixteenthQuintuplet -> 1.0f / 4.0f * 4.0f / 5.0f
        NoteDuration.SixteenthSeptuplet -> 1.0f / 4.0f * 6.0f / 7.0f
        NoteDuration.ThirtySecond -> 1.0f / 8.0f
        NoteDuration.ThirtySecondTriplet -> 1.0f / 8.0f * 2.0f / 3.0f
        NoteDuration.ThirtySecondQuintuplet -> 1.0f / 8.0f * 4.0f / 5.0f
        NoteDuration.ThirtySecondSeptuplet -> 1.0f / 8.0f * 6.0f / 7.0f
    }

fun NoteDuration.durationInSeconds(bpmQuarter: Float) = bpmToSeconds(bpmQuarter) * lengthInQuarter()

fun NoteDuration.durationInMillis(bpmQuarter: Float) = (1000f * durationInSeconds(bpmQuarter)).roundToLong()
fun NoteDuration.durationInNanos(bpmQuarter: Float) = (1e9f * durationInSeconds(bpmQuarter)).roundToLong()

/// Given beats per minute for specific NoteDuration, convert the values into quarters notes per minute.
/**
 * E.g. if you 200 beats per minute for sixteenth notes, you get 200/4 = 50 quarter notes per minute
 * @param bpm: Beats per minute for the given note duration
 */
fun NoteDuration.bpmQuarter(bpm: Float) = bpm * lengthInQuarter()

fun NoteDuration.isNoTuplet() = (
        this == NoteDuration.Quarter ||
        this == NoteDuration.Eighth ||
        this == NoteDuration.Sixteenth ||
        this == NoteDuration.ThirtySecond
        )

fun NoteDuration.isTriplet() = (
        this == NoteDuration.QuarterTriplet ||
        this == NoteDuration.EighthTriplet ||
        this == NoteDuration.SixteenthTriplet ||
        this == NoteDuration.ThirtySecondTriplet
        )

fun NoteDuration.isQuintuplet() = (
        this == NoteDuration.QuarterQuintuplet ||
        this == NoteDuration.EighthQuintuplet ||
        this == NoteDuration.SixteenthQuintuplet ||
        this == NoteDuration.ThirtySecondQuintuplet
        )

fun NoteDuration.isSeptuplet() = (
        this == NoteDuration.QuarterSeptuplet ||
        this == NoteDuration.EighthSeptuplet ||
        this == NoteDuration.SixteenthSeptuplet ||
        this == NoteDuration.ThirtySecondSeptuplet
        )

fun NoteDuration.hasBaseTypeQuarter() = (
        this == NoteDuration.Quarter ||
        this == NoteDuration.QuarterTriplet ||
        this == NoteDuration.QuarterQuintuplet ||
        this == NoteDuration.QuarterSeptuplet
        )

fun NoteDuration.hasBaseTypeEighth() = (
        this == NoteDuration.Eighth ||
        this == NoteDuration.EighthTriplet ||
        this == NoteDuration.EighthQuintuplet ||
        this == NoteDuration.EighthSeptuplet
        )

fun NoteDuration.hasBaseTypeSixteenth() = (
        this == NoteDuration.Sixteenth ||
        this == NoteDuration.SixteenthTriplet ||
        this == NoteDuration.SixteenthQuintuplet ||
        this == NoteDuration.SixteenthSeptuplet
        )

fun NoteDuration.hasBaseTypeThirtySecond() = (
        this == NoteDuration.ThirtySecond ||
        this == NoteDuration.ThirtySecondTriplet ||
        this == NoteDuration.ThirtySecondQuintuplet ||
        this == NoteDuration.ThirtySecondSeptuplet
        )
