package de.moekadu.metronome.misc

import kotlin.math.absoluteValue
import kotlin.math.min

class TapInEvaluator(val maxNumHistoryValues: Int, val minimumAllowedDtInMillis: Long, val maximumAllowedDtInMillis: Long) {
    /** List of tap-in values obtained withe System.nanoTime(), the first one is the oldest, the
     * last one the newest. The actual number of values, which have valid values is not the size
     * of this array but saved by the property numValues.
     */
    private val values = LongArray(maxNumHistoryValues)

    /** Number of valid values within the values-array. */
    var numValues = 0
        private set

    /** Additional delay in millis for predicted next tap for more natural feel. */
    private val predictionDelayInMillis = 0L

    /** If two taps after each other appear extremely quickly, ignore the second one.
     * The given time defines what is "quickly". */
    private val suppressTapDeltaTNanos = 50 * 1000_000L // 50ms

    /** Here we store the evaluated dt in nano seconds. */
    var dtNanos = NOT_AVAILABLE
        private set
    /** Here we store the next predicted tap. */
    var predictedNextTapNanos = NOT_AVAILABLE
        private set

    /** Append a new tap in time.
     * Side effect:
     * dt and predictedNextTap will be updated.
     * @param timeInNanos Time from System.nanoTime() of tap.
     */
    fun tap(timeInNanos: Long) {
        // If the new value is extremely quick after the last registered value, ignore it.
        if (numValues > 0 && timeInNanos - values[numValues - 1] < suppressTapDeltaTNanos) {
//            Log.v("Metronome", "TapInEvaluator: ignoring value")
            return
        } else if (numValues == values.size) { // add new value to the end of the array
            values.copyInto(values, 0, 1)
            values[values.size - 1] = timeInNanos
        } else {
            values[numValues] = timeInNanos
            ++numValues
        }

        if (numValues < 2)
            return

        val lastDt = values[numValues - 1] - values[numValues - 2]

        // check min and max allowed values
        // normally we would multiply with 1_000_000L, but we reduce the minimum value
        // a bit and increase the maximum value a bit such that by tapping, minimum and maximum
        // can safely be achieved ...
        if (lastDt < minimumAllowedDtInMillis * 700_000L || lastDt > maximumAllowedDtInMillis * 1_500_000L) {
            values[0] = values[numValues - 1]
            numValues = 1
            dtNanos = NOT_AVAILABLE
            predictedNextTapNanos = NOT_AVAILABLE
            return
        }

        // check if last two dts are extremely different. If yes, reset ...
        if (numValues >= 3) {
            val secondLastDt = values[numValues - 2] - values[numValues - 3]
            val smallerDt = min(lastDt, secondLastDt)
            val dtDifference = (lastDt - secondLastDt).absoluteValue
            if (dtDifference.toDouble() / smallerDt.toDouble() > 0.4) {
                values[0] = values[numValues - 1]
                numValues = 1
                dtNanos = NOT_AVAILABLE
                predictedNextTapNanos = NOT_AVAILABLE
                return
            }
        }

        // compute dt by averaging ...
        var indexLower = 0
        var indexUpper = numValues - 1
        // Log.v("Metronome", "TapInEvaluator.tap: numUsedValues = ${indexUpper - indexLower + 1}")
        var weightSum = 0L
        var dtSum = 0L
        // now compute a nice dt ...
        while (indexLower < indexUpper) {
            weightSum += indexUpper - indexLower
            dtSum += (values[indexUpper] - values[indexLower])
            indexLower += 1
            indexUpper -= 1
        }
        dtNanos = dtSum / weightSum

        predictedNextTapNanos = values[numValues - 1] + dtNanos
    }

    companion object {
        const val NOT_AVAILABLE = Long.MAX_VALUE
    }
}
