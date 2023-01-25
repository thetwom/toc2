package de.moekadu.metronome.misc

import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class TapInEvaluator(val numHistoryValues: Int, val minimumAllowedDtInMillis: Long, val maximumAllowedDtInMillis: Long) {
    /** List of tap-in values obtained withe System.nanoTime(), the first one is the oldest, the
     * last one the newest. The actual number of values, which have valid values is not the size
     * of this array but saved by the property numValues.
     */
    private val values = LongArray(numHistoryValues)

    /** Number of valid values within the values-array. */
    private var numValues = 0

    /** Max relative deviation between two succeeding values, to include them in the averaging. */
    private val maxDeviation = 0.5f

    /** Additional delay in millis for predicted next tap for more natural feel. */
    private val predictionDelayInMillis = 0L

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
        // If the new value is extremely quick after the last registered value, ignore it
        // we use 700_000L instead of 1000_000L to make the limit a bit smaller otherwise it would be hard
        // to actually reach the minimum value.
        if (numValues > 0 && timeInNanos - values[numValues - 1] < minimumAllowedDtInMillis * 700_000L) { // 700_000L = 0.7f * 1000_000L
//            Log.v("Metronome", "TapInEvaluator: ignoring value")
            return
        } else if (numValues == values.size) { // add new value to the end of the array
            values.copyInto(values, 0, 1)
            values[values.size - 1] = timeInNanos
        } else {
            values[numValues] = timeInNanos
            ++numValues
        }

        // "delete" all values if the last two taps are too far apart
        // increase the maximumAllowedDt a bit (use 1500_000L instead of 1000_000L as millis to nanos
        // conversion), such that we can safely reach this value.
        // (otherwise it would be hard to reach the maximum value since either we are below or
        //  or the values will be killed due to exceeding it.)
        if (numValues >= 2 && values[numValues - 1] - values[numValues - 2] > 1500_000L * maximumAllowedDtInMillis) {
            values[0] = timeInNanos
            numValues = 1
        }

        val iBegin = determineFirstValidValue()
        val iEnd = numValues
        val numDts = max(0, iEnd - iBegin - 1)
        dtNanos = if (numDts == 0)
            NOT_AVAILABLE
        else
            (values[iEnd - 1] - values[iBegin]) / numDts

        predictedNextTapNanos = if (dtNanos == NOT_AVAILABLE) {
            NOT_AVAILABLE
        } else {
//            // for the predicted tap we use a weighted average with a linear weight drop
//            // The predicted tap based on the last tap gets the maxWeight
//            val maxWeight = 100L
//            val minWeight = 1L
//            var sum = 0L
//            var weightSum = 0L
//            val numValid = iEnd - iBegin
//            for (i in iBegin until iEnd) {
//                val relativeDistToFirstValue = (i - iBegin).toFloat() / (numValid - 1)
//                // the weight and sum must be with Integer/Long types to avoid errors for the large absolute sum values.
//                val w = (minWeight * relativeDistToFirstValue + maxWeight * (1 - relativeDistToFirstValue)).roundToLong()
//                weightSum += w
//                sum += w * (values[i] + (iEnd - i) * dtNanos)
//            }
//            // values[numValues - 1] + dt + predictionDelayInMillis
//            sum / weightSum + 1000_000L * predictionDelayInMillis
            values[iEnd - 1] + dtNanos
        }
    }

    private fun determineFirstValidValue(): Int {
        if (numValues < 2)
            return numValues - 1

        val lastDt = (values[numValues - 1] - values[numValues - 2])

        // if we have 3 values or more (i.e. 2 dts or more), and the last recent two
        // deviate, we are uncertain, if this is an accident or not and tell that the
        // there is only one valid value (so no dt available).
        // This helps, that we don't accidentally get speeds close to the minimum, where the
        // user actually just wants to start a new row ...
        if (numValues >= 3) {
            val dtBeforeLastDt = (values[numValues - 2] - values[numValues - 3])
            if ((dtBeforeLastDt - lastDt).absoluteValue / min(lastDt, dtBeforeLastDt).toFloat() > maxDeviation)
                return numValues - 1
        }

        // accept so many dts in a row as long a the deviation of a dt does not deviate from
        // the most recent dt too much.
        var iBegin = numValues - 2
        for (i in numValues - 2 downTo 1) {
            val dt = values[i] - values[i - 1]
            if (((lastDt - dt) / lastDt.toFloat()).absoluteValue < maxDeviation)
                iBegin = i - 1
            else
                break
        }

        return iBegin
    }

    companion object {
        const val NOT_AVAILABLE = Long.MAX_VALUE
    }
}