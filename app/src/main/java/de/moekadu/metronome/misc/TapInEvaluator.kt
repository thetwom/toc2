package de.moekadu.metronome.misc

import android.util.Log
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToLong

class TapInEvaluator(numHistoryValues: Int, val maximumAllowedDtInMs: Long) {
    /** List of tap in values, the first one is the oldest, the last one the newest.
     * The actual number of values, which have valid values is not the size of this array
     * but saved by the property numValues.
     */
    private val values = LongArray(numHistoryValues)

    /** Number of valid values within the values-array. */
    private var numValues = 0

    private val maxDeviation = 0.5f

    fun tap(timeInMs: Long) {
        if (numValues == values.size) {
            values.copyInto(values, 0, 1)
            values[values.size - 1] = timeInMs
        } else {
            values[numValues] = timeInMs
            ++numValues
        }
//        if (numValues >= 2)
//            Log.v("Metronome", "TapInEvaluator.tap: values[numValues-1] = ${values[numValues-1]}, values[numValues-21] = ${values[numValues-2]}, diff = ${values[numValues - 1] - values[numValues - 2]}")
        // "delete" all values if the last two taps are too far apart
        if (numValues >= 2 && values[numValues - 1] - values[numValues - 2] > maximumAllowedDtInMs) {
            values[0] = timeInMs
            numValues = 1
        }
    }

    fun dtInMs(): Long {
        return if (numValues < 2) {
            NOT_AVAILABLE
        } else {
            val iBegin = determineFirstValidValue()
            val iEnd = numValues
            val numDts = iEnd - iBegin - 1
//            Log.v("Metronome", "TapInEvaluator.dtInMs: iBegin = $iBegin, iEnd = $iEnd, numDts = $numDts")
            if (numDts == 0)
                NOT_AVAILABLE
            else
                ((values[iEnd - 1] - values[iBegin]).toFloat() / numDts.toFloat()).roundToLong()
        }
    }

    fun predictNextTap(): Long {
        val dt = dtInMs()
        if (dt == NOT_AVAILABLE)
            return NOT_AVAILABLE

//        val iBegin = determineFirstValidValue()
//        val iEnd = numValues
//        var tSum = 0L
//        for (i in iBegin until iEnd)
//            tSum += values[i] + dt * (numValues - i)
//        Log.v("Metronome", "TapInEvaluator.predictNextTap: lastTapTime = ${values[numValues-1]}, predicted: ${(tSum.toFloat() / (iEnd - iBegin).toFloat()).roundToLong()}")
//        return (tSum.toFloat() / (iEnd - iBegin).toFloat()).roundToLong()
        return values[numValues - 1] + dt + 5
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