package de.moekadu.metronome

import kotlin.math.roundToLong

enum class NoteDuration {
    SixteenthTriplet,  // Duration = Sixteenth * 2.0 / 3.0
    SixteenthQuintuplet, // Duration = Sixteenth * 4.0 / 5.0
    Sixteenth,
    EighthTriplet,  // Duration = Eighth * 2.0 / 3.0
    EighthQuintuplet, // Duration = Eighth * 4.0 / 5.0
    Eighth,
    QuarterTriplet, // Duration = Quarter * 2.0 / 3.0
    QuarterQuintuplet, // Duration = Quarter * 4.0 / 5.0
    Quarter,
}

private fun bpmToSeconds(bpm: Float) = (60.0f / bpm)

fun NoteDuration.durationInSeconds(bpmQuarter: Float) =
    when(this) {
        NoteDuration.SixteenthTriplet -> bpmToSeconds(bpmQuarter / 4.0f * 2.0f / 3.0f)
        NoteDuration.SixteenthQuintuplet -> bpmToSeconds(bpmQuarter / 4.0f * 4.0f / 5.0f)
        NoteDuration.Sixteenth -> bpmToSeconds(bpmQuarter / 4.0f)
        NoteDuration.EighthTriplet -> bpmToSeconds(bpmQuarter / 2.0f * 2.0f / 3.0f)
        NoteDuration.EighthQuintuplet -> bpmToSeconds(bpmQuarter / 2.0f * 4.0f / 5.0f)
        NoteDuration.Eighth -> bpmToSeconds(bpmQuarter / 2.0f)
        NoteDuration.QuarterTriplet -> bpmToSeconds(bpmQuarter * 2.0f / 3.0f)
        NoteDuration.QuarterQuintuplet -> bpmToSeconds(bpmQuarter * 4.0f / 5.0f)
        NoteDuration.Quarter -> bpmToSeconds(bpmQuarter)
    }

fun NoteDuration.durationInMillis(bpmQuarter: Float) = (1000f * durationInSeconds(bpmQuarter)).roundToLong()

/// Given beats per minute for specific NoteDuration, convert the values into quarters notes per minute.
/**
 * E.g. if you 200 beats per minute for sixteenth notes, you get 200/4 = 50 quarter notes per minute
 * @param bpm: Beats per minute for the given note duration
 */
fun NoteDuration.bpmQuarter(bpm: Float) =
    when(this) {
        NoteDuration.SixteenthTriplet -> bpm / 4.0f * 2.0f / 3.0f
        NoteDuration.SixteenthQuintuplet -> bpm / 4.0f * 4.0f / 5.0f
        NoteDuration.Sixteenth -> bpm / 4.0f
        NoteDuration.EighthTriplet -> bpm / 2.0f * 2.0f / 3.0f
        NoteDuration.EighthQuintuplet -> bpm / 2.0f * 4.0f / 5.0f
        NoteDuration.Eighth -> bpm / 2.0f
        NoteDuration.QuarterTriplet -> bpm * 2.0f / 3.0f
        NoteDuration.QuarterQuintuplet -> bpm * 4.0f / 5.0f
        NoteDuration.Quarter -> bpm
    }

fun NoteDuration.isNoTuplet() = (
        this == NoteDuration.Quarter ||
        this == NoteDuration.Eighth ||
        this == NoteDuration.Sixteenth
        )

fun NoteDuration.isTriplet() = (
        this == NoteDuration.QuarterTriplet ||
        this == NoteDuration.EighthTriplet ||
        this == NoteDuration.SixteenthTriplet
        )

fun NoteDuration.isQuintuplet() = (
        this == NoteDuration.QuarterQuintuplet ||
        this == NoteDuration.EighthQuintuplet ||
        this == NoteDuration.SixteenthQuintuplet
        )

fun NoteDuration.hasBaseTypeQuarter() = (
        this == NoteDuration.Quarter ||
        this == NoteDuration.QuarterTriplet ||
        this == NoteDuration.QuarterQuintuplet
        )

fun NoteDuration.hasBaseTypeEighth() = (
        this == NoteDuration.Eighth ||
        this == NoteDuration.EighthTriplet ||
        this == NoteDuration.EighthQuintuplet
        )

fun NoteDuration.hasBaseTypeSixteenth() = (
        this == NoteDuration.Sixteenth ||
        this == NoteDuration.SixteenthTriplet ||
        this == NoteDuration.SixteenthQuintuplet
        )
