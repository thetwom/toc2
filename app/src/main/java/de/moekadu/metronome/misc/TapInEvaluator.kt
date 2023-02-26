package de.moekadu.metronome.misc

import android.util.Log
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** Class for computing online mean and variance.
 * This uses the incremental algorithm of Welford, see
 *  https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
 * Usage:
 *   1. Create class, and call "update()" with all values which should contribute to
 *      mean and variance.
 *   2. Obtain mean, variance, standard_deviation at any time you need the values, by
 *      the attributes "mean", "variance", "standardDeviation", "standardError".
 */
class UpdatableStatistics {
    /** Current sum of weights. */
    private var count = 0
    /** Intermediate value needed for variance. */
    @Suppress("PrivatePropertyName")
    private var S = 0L

    /** Current mean value. */
    var mean = 0L
        private set

    /** Current variance. */
    val variance
        get() = if (count < 2) 0L else S / (count - 1)

    /** Current standard deviation. */
    val standardDeviation
        get() = variance.toDouble().pow(0.5)

    /** Standard error. */
    val standardError
        get() = standardDeviation / count.toDouble().pow(0.5)

    /** Relative standard error based on mean value. */
    val relativeStandardError
        get() = standardError / mean

    /** Reset to zero. */
    fun clear() {
        count = 0
        S = 0L
        mean = 0L
    }

    /** Update the class with an additional value.
     * @param value Value which should be considered in mean and variance.
     */
    fun update(value: Long) {
        count += 1
        val meanOld = mean
        mean = meanOld + (value - meanOld) / count
        S += (value - meanOld) * (value - mean)
    }
}

class TapInEvaluator(val maxNumHistoryValues: Int, val minimumAllowedDtInMillis: Long, val maximumAllowedDtInMillis: Long) {
    /** List of tap-in values obtained withe System.nanoTime(), the first one is the oldest, the
     * last one the newest. The actual number of values, which have valid values is not the size
     * of this array but saved by the property numValues.
     */
    private val values = LongArray(maxNumHistoryValues)

    /** Number of valid values within the values-array. */
    private var numValues = 0

    /** Temporary array where we can store standard errors. */
    private val standardErrors = DoubleArray(maxNumHistoryValues)

    /** Temporary class for computing updatable statistics. */
    private val updatableStatistics = UpdatableStatistics()

    private val minimumNumValuesForAveraging = 5
    private val errorReductionSpan = 5

    private val maxRelativeStandardError = 0.2

    /** Max relative deviation between two succeeding values, to include them in the averaging. */
    private val maxDeviation = 0.5f

    private val maxRelativeRatioToBeSimilar = 0.3f

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
        // If the new value is extremely quick after the last registered value, ignore it.
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

        var numErrors = 0
        var timesStart = 0
        updatableStatistics.clear()
        for (i in numValues - 1  downTo  1) {
            val dt = values[i] - values[i - 1]
            // use a slightly bigger maximum value (1500_000L than 1000_000L such than one
            // can actually reach the maximum)
            if (dt > maximumAllowedDtInMillis * 1500_000L) {
                Log.v("Metronome", "TapInEvaluator.tap : maximum dt exceeded at index $i, (numValues=$numValues)")
                timesStart = i
                break
            }

            updatableStatistics.update(dt)
            // stop searching for series begin, if the standard error is too big
            if (updatableStatistics.relativeStandardError > maxRelativeStandardError) {
                Log.v("Metronome", "TapInEvaluator.tap : relative error exceeded at index $i, (numValues=$numValues)")
                timesStart = i
                break
            }

            standardErrors[numErrors] = updatableStatistics.standardError
            val secondErrorIndex = numErrors
            val firstErrorIndex = secondErrorIndex - errorReductionSpan + 1

            if (firstErrorIndex >= 0)
                Log.v("Metronome", "TapInEvaluator.tap : numErrors=$numErrors i1 = $firstErrorIndex, i2=$secondErrorIndex, e1 = ${standardErrors[firstErrorIndex]}, e2 = ${standardErrors[secondErrorIndex]}")
            else
                Log.v("Metronome", "TapInEvaluator.tap : numErrors=$numErrors i1 = $firstErrorIndex, i2=$secondErrorIndex")

            if (numErrors >= minimumNumValuesForAveraging + errorReductionSpan
                && standardErrors[secondErrorIndex] > standardErrors[firstErrorIndex]) {
                var minValue = Double.MAX_VALUE
                var minJ = -1
                for (j in firstErrorIndex.. secondErrorIndex) {
                    if (standardErrors[j] < minValue) {
                        minValue = standardErrors[j]
                        minJ = j
                    }
                }

                timesStart = numValues - minJ
                Log.v("Metronome", "TapInEvaluator.tap : minimum error found, numValues=$numValues, numErrors=$numErrors, minJ=$minJ, timeStart=$timesStart")
                break
            }
            numErrors++
        }

        if (timesStart > 0) {
            Log.v("Metronome", "TapInEvaluator.tap : start taking values at index $timesStart")
            values.copyInto(values, 0, timesStart, numValues)
            numValues -= timesStart
        }

        if (numValues <= 2) {
            dtNanos = NOT_AVAILABLE
            predictedNextTapNanos = NOT_AVAILABLE
            return
        }

//        Log.v("Metronome", "TapInEvaluator.tap: numValues = $numValues")
        var indexLower = 0
        var indexUpper = numValues - 1
        Log.v("Metronome", "TapInEvaluator.tap: numUsedValues = ${indexUpper - indexLower + 1}")
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