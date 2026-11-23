package de.moekadu.metronome.audio

import de.moekadu.metronome.metronomeproperties.NoteDuration
import de.moekadu.metronome.metronomeproperties.durationInSeconds
import kotlin.math.floor
import kotlin.math.roundToInt

internal val NoteDuration.usesExpandedTiming: Boolean
    get() = baseSubdivision == 8 || tupletSize == 7

/** Scenes with expanded rhythms use one fractional frame clock across all tracks. */
internal fun NoteDuration.lengthInAudioFrames(
    bpmQuarter: Float, sampleRate: Int, useExactNoteFrames: Boolean
): Double {
    if (useExactNoteFrames) {
        return (60.0 * sampleRate / bpmQuarter / baseSubdivision * tupletSpan / tupletSize)
            .coerceAtLeast(1.0)
    }
    // Existing scenes keep their original per-note sample rounding.
    return (durationInSeconds(bpmQuarter) * sampleRate).roundToInt().coerceAtLeast(1).toDouble()
}

internal data class NextNoteInfo(
    val nextNoteIndex: Int,
    val exactNextNoteFrame: Double,
    val noteCount: Long
) {
    val nextNoteFrame: Int get() = exactNextNoteFrame.roundToInt()
}

/** First note at or after [fromFrame] on the shared grid anchored at frame zero. */
internal fun nextNoteOnTrackGrid(lengths: DoubleArray, fromFrame: Int): NextNoteInfo {
    val cycleLength = lengths.sum()
    val completedCycles = floor(fromFrame / cycleLength).toLong()
    val cycleStart = completedCycles * cycleLength
    val noteCount = completedCycles * lengths.size
    var frame = cycleStart
    for (index in lengths.indices) {
        if (frame.roundToInt() >= fromFrame)
            return NextNoteInfo(index, frame, noteCount)
        frame += lengths[index]
    }
    return NextNoteInfo(0, cycleStart + cycleLength, noteCount)
}
