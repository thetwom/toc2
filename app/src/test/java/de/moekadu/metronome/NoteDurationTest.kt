package de.moekadu.metronome

import de.moekadu.metronome.audio.lengthInAudioFrames
import de.moekadu.metronome.audio.nextNoteOnTrackGrid
import de.moekadu.metronome.metronomeproperties.NoteDuration
import de.moekadu.metronome.metronomeproperties.durationInSeconds
import de.moekadu.metronome.metronomeproperties.stringToTracks
import de.moekadu.metronome.metronomeproperties.tracksToString
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteDurationTest {
    @Test
    fun theTwoEditorSelectorsAddressAllSixteenDurations() {
        val choices = listOf(1, 2, 4, 8).flatMap { subdivision ->
            listOf(1, 3, 5, 7).map { tupletSize -> NoteDuration.of(subdivision, tupletSize) }
        }
        assertEquals(NoteDuration.entries.toSet(), choices.toSet())
        assertEquals(NoteDuration.ThirtySecondSeptuplet, choices.last())
    }

    @Test
    fun sevenNotesTakeTheTimeOfFourAtEachBaseDuration() {
        val groupsInSecondsAt120Bpm = mapOf(
            "QuarterSeptuplet" to 2f,
            "EighthSeptuplet" to 1f,
            "SixteenthSeptuplet" to 0.5f,
            "ThirtySecondSeptuplet" to 0.25f
        )
        for ((name, groupSeconds) in groupsInSecondsAt120Bpm) {
            val duration = NoteDuration.entries.find { it.name == name }
            assertEquals(name, groupSeconds, (duration?.durationInSeconds(120f) ?: 0f) * 7, 1e-7f)
        }
    }

    @Test
    fun thirtySecondNotesUseTheSelectedTupletRatio() {
        val cases = mapOf(
            "ThirtySecond" to 0.0625f,
            "ThirtySecondTriplet" to 0.041666667f,
            "ThirtySecondQuintuplet" to 0.05f,
            "ThirtySecondSeptuplet" to 0.035714286f
        )
        for ((name, seconds) in cases) {
            val duration = NoteDuration.entries.find { it.name == name }
            assertEquals(name, seconds, duration?.durationInSeconds(120f) ?: 0f, 1e-7f)
        }
    }

    @Test
    fun aSavedMultitrackScenePreservesNewAndExistingDurations() {
        val scene = "#2 2 0.8 0 5 0 1.0 ThirtySecond 1 0.75 ThirtySecondTriplet " +
            "2 0.5 ThirtySecondQuintuplet 3 1.0 ThirtySecondSeptuplet 7 1.0 EighthSeptuplet " +
            "0.5 1 2 4 1.0 QuarterTriplet 6 0.25 SixteenthQuintuplet"
        assertEquals(scene, tracksToString(stringToTracks(scene)))
    }

    @Test
    fun legacyScenesKeepTheirDurationsAndTrackSettings() {
        val legacyScene = "#2 2 0.8 0 2 0 1.0 Quarter 3 0.7 QuarterQuintuplet " +
            "0.5 1 4 1 1.0 EighthTriplet 4 0.6 Eighth 6 0.25 SixteenthTriplet 7 1.0 Sixteenth"
        assertEquals(legacyScene, tracksToString(stringToTracks(legacyScene)))
        assertEquals("#2 1 1.0 0 1 3 0.6 Quarter", tracksToString(stringToTracks("3 0.6")))
        assertEquals(
            "#2 1 1.0 0 1 5 0.7 EighthQuintuplet",
            tracksToString(stringToTracks("#1 5 0.7 EighthQuintuplet"))
        )
    }

    @Test
    fun theTrackGridFindsRoundedNoteBoundariesWithoutCumulativeDrift() {
        val frames = NoteDuration.SixteenthSeptuplet.lengthInAudioFrames(120f, 48_000, useExactNoteFrames = true)
        val lengths = DoubleArray(7) { frames }
        assertEquals(24_000.0, lengths.sum(), 1e-9)

        val atBoundary = nextNoteOnTrackGrid(lengths, 3_429)
        assertEquals(1, atBoundary.nextNoteIndex)
        assertEquals(3_429, atBoundary.nextNoteFrame)
        val afterBoundary = nextNoteOnTrackGrid(lengths, 3_430)
        assertEquals(2, afterBoundary.nextNoteIndex)
        assertEquals(6_857, afterBoundary.nextNoteFrame)

        val afterOneThousandCycles = nextNoteOnTrackGrid(lengths, 24_000_000)
        assertEquals(0, afterOneThousandCycles.nextNoteIndex)
        assertEquals(24_000_000, afterOneThousandCycles.nextNoteFrame)
    }

    @Test
    fun existingDurationsKeepTheirOriginalAudioFrameRounding() {
        val cases = mapOf(
            NoteDuration.Quarter to 23_415.0,
            NoteDuration.Eighth to 11_707.0,
            NoteDuration.SixteenthTriplet to 3_902.0,
            NoteDuration.QuarterQuintuplet to 18_732.0
        )
        for ((duration, frames) in cases)
            assertEquals(duration.name, frames, duration.lengthInAudioFrames(123f, 48_000, useExactNoteFrames = false), 0.0)
    }

    @Test
    fun mixedTracksShareTheSameBeatAtNonIntegralSampleRates() {
        val quarter = NoteDuration.Quarter.lengthInAudioFrames(123f, 48_000, useExactNoteFrames = true)
        val septuplet = NoteDuration.EighthSeptuplet.lengthInAudioFrames(123f, 48_000, useExactNoteFrames = true)
        val thirtySecond = NoteDuration.ThirtySecond.lengthInAudioFrames(123f, 48_000, useExactNoteFrames = true)
        assertEquals(2 * quarter, 7 * septuplet, 1e-8)
        assertEquals(quarter, 8 * thirtySecond, 1e-8)
    }
}
