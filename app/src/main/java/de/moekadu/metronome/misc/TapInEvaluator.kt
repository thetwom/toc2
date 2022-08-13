package de.moekadu.metronome.misc

import android.util.Log
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class TapInEvaluator(val numHistoryValues: Int, val minimumAllowedDtInMs: Long, val maximumAllowedDtInMs: Long) {
    /** List of tap in values, the first one is the oldest, the last one the newest.
     * The actual number of values, which have valid values is not the size of this array
     * but saved by the property numValues.
     */
    private val values = LongArray(numHistoryValues)

    /** Number of valid values within the values-array. */
    private var numValues = 0

    /** Max relative deviation between two succeeding values, to include them in the averaging. */
    private val maxDeviation = 0.5f

    /** Additional delay in millis for predicted next tap for more natural feel. */
    private val predictionDelayInMillis = 5L

    /** Here we store the evaluated dt. */
    var dt = NOT_AVAILABLE
        private set
    /** Here we store the next predicted tap. */
    var predictedNextTap = NOT_AVAILABLE
        private set

    /** Append a new tap in time.
     * Side effect:
     * dt and predictedNextTap will be updated.
     * @param timeInMs Time from System.uptimeMillis() of tap.
     */
    fun tap(timeInMs: Long) {
        // If the new value is extremely quick after the last registerd value, ignore it
        // we use the 0.5-factor to make the limit a bit smaller otherwise it would be hard
        // to actually reach the minimum value.
        if (numValues > 0 && timeInMs - values[numValues - 1] < 0.7f * minimumAllowedDtInMs) {
//            Log.v("Metronome", "TapInEvaluator: ignoring value")
            return
        } else if (numValues == values.size) { // add new value to the end of the array
            values.copyInto(values, 0, 1)
            values[values.size - 1] = timeInMs
        } else {
            values[numValues] = timeInMs
            ++numValues
        }

        // "delete" all values if the last two taps are too far apart
        // increase the maximumAllowedDt a bit, such that we can savely reach this value.
        // (otherwise it would be hard to reach the maximum value since either we are below or
        //  or the values will be killed due to exceeding it.)
        if (numValues >= 2 && values[numValues - 1] - values[numValues - 2] > 1.5f * maximumAllowedDtInMs) {
            values[0] = timeInMs
            numValues = 1
        }

        val iBegin = determineFirstValidValue()
        val iEnd = numValues
        val numDts = max(0, iEnd - iBegin - 1)
        dt = if (numDts == 0)
            NOT_AVAILABLE
        else
            ((values[iEnd - 1] - values[iBegin]).toFloat() / numDts.toFloat()).roundToLong()

        predictedNextTap = if (dt == NOT_AVAILABLE) {
            NOT_AVAILABLE
        } else {
            // for the predicted tap we use a weighted average with a linear weight drop
            // The predicted tap based on the last tap gets the maxWeight
            val maxWeight = 10f
            val minWeight = 1f
            var sum = 0f
            var weightSum = 0f
            val numValid = iEnd - iBegin
            for (i in iBegin until iEnd) {
                val relativeDistToFirstValue = (i - iBegin).toFloat() / (numValid - 1)
                val w = minWeight * relativeDistToFirstValue + maxWeight * (1 - relativeDistToFirstValue)
                weightSum += w
                sum += w * (values[i] + (iEnd - i) * dt)
            }
            // values[numValues - 1] + dt + predictionDelayInMillis
            (sum / weightSum + predictionDelayInMillis).roundToLong()
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