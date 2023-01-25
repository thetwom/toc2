package de.moekadu.metronome

import de.moekadu.metronome.misc.TapInEvaluator
import org.junit.Assert.assertEquals
import org.junit.Test

class TapInEvaluatorTest{

    @Test
    fun tapIn() {
        val tapInEvaluator = TapInEvaluator(4, 0L, 1000_000_000L)

        tapInEvaluator.tap(10L)
        assertEquals(TapInEvaluator.NOT_AVAILABLE, tapInEvaluator.dtNanos)
        tapInEvaluator.tap(20L)
        assertEquals(10L, tapInEvaluator.dtNanos)
        assertEquals(30L, tapInEvaluator.predictedNextTapNanos)
        tapInEvaluator.tap(30L)
        assertEquals(10L, tapInEvaluator.dtNanos)
        assertEquals(40L, tapInEvaluator.predictedNextTapNanos)
        tapInEvaluator.tap(40L)
        assertEquals(10L, tapInEvaluator.dtNanos)
        assertEquals(50L, tapInEvaluator.predictedNextTapNanos)
        tapInEvaluator.tap(50L)
        assertEquals(10L, tapInEvaluator.dtNanos)
        assertEquals(60L, tapInEvaluator.predictedNextTapNanos)

    }
}