/*
 * Copyright 2019 Michael Moessner
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

package de.moekadu.metronome.misc

import android.content.res.Resources
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class Utilities {
    companion object {
        val bpmIncrements = floatArrayOf(0.125f, 0.25f, 0.5f, 1f, 2f, 5f, 10f)
        private const val sensitivityMin = 0.5f // steps per cm
        private const val sensitivityMax = 10.0f // steps per cm
        private val bpmFormat = DecimalFormat()

        fun dp2px(dp: Float): Float {
            return dp * Resources.getSystem().displayMetrics.density
        }

        fun px2dp(px: Float): Float {
            return px / Resources.getSystem().displayMetrics.density
        }

//        fun sp2px(sp: Int): Int {
//            return (sp * Resources.getSystem().displayMetrics.scaledDensity).toInt()
//        }


        fun sensitivity2percentage(sensitivity: Float): Float {
            return (sensitivity - sensitivityMin) / (sensitivityMax - sensitivityMin) * 100.0f
        }

        fun percentage2sensitivity(percentage: Float): Float {
            return sensitivityMin + percentage / 100.0f * (sensitivityMax - sensitivityMin)
        }

        fun px2cm(px: Float): Float {
            return px2dp(px) / 160.0f * 2.54f
        }

//        fun cm2px(cm: Float): Float {
//            return dp2px(cm * 160.0f / 2.54f)
//        }

        fun bpm2seconds(bpm: Float) = 60.0 / bpm

        fun bpm2millis(bpm: Float) = (1000.0 * 60.0 / bpm).roundToLong()

        fun millis2bpm(dt: Long) = 60.0f * 1000.0f / dt

        fun getBpmString(bpm: Float, useGrouping: Boolean = true): String {
            val tolerance = 1e-6f
            var i = 1
            while (i < bpmIncrements.size && abs(bpm / bpmIncrements[i] - (bpm / bpmIncrements[i]).roundToInt()) < tolerance) {
                ++i
            }
            val bpmIncrement: Float = bpmIncrements[i - 1]
            return getBpmString(bpm, bpmIncrement, useGrouping)
        }

        fun getBpmString(bpm: Float, bpmIncrement: Float, useGrouping: Boolean = true): String {
            val tolerance = 1e-6f
            val digits = when {
                abs(bpmIncrement - 0.125f) < tolerance -> {
                    3
                }
                abs(bpmIncrement - 0.25f) < tolerance -> {
                    2
                }
                abs(bpmIncrement - 0.5f) < tolerance -> {
                    1
                }
                else -> {
                    0
                }
            }

            bpmFormat.minimumFractionDigits = digits
            bpmFormat.maximumFractionDigits = digits
            bpmFormat.isGroupingUsed = useGrouping
            return bpmFormat.format(bpm.toDouble())
        }

    }

}
