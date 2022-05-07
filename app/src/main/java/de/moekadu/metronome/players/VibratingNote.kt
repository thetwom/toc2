/*
 * Copyright 2021 Michael Moessner
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

package de.moekadu.metronome.players

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import de.moekadu.metronome.metronomeproperties.NoteListItem
import de.moekadu.metronome.metronomeproperties.durationInMillis
import de.moekadu.metronome.metronomeproperties.getNoteVibrationDuration
import kotlin.math.*

private fun getVibrator(context: Context?): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =  context?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager?
        vibratorManager?.defaultVibrator
    } else {
        context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    }
}

fun vibratingNoteHasHardwareSupport(context: Context?): Boolean {
    // val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    val vibrator = getVibrator(context)
    if (vibrator != null && vibrator.hasVibrator())
        return true
    return false
}

const val maximumVibrationScaling = 3.0f
fun vibratingNote100ToLog(value: Int) = maximumVibrationScaling.pow((value - 50) / 50f)
fun vibratingNoteLogTo100(value: Float) = (50f * log(value, maximumVibrationScaling) + 50).toInt()

class VibratingNote(context: Context) {

    private val applicationContext = context.applicationContext
    private val vibrator by lazy {
        getVibrator(applicationContext)
        // applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    }
    private var earliestNextVibrationTime = 0L
    private var _strength = 1.0f
    var strength: Int
        set(value) {
//            Log.v("Metronome", "VibratingNote.strength: $value")
            require(value in 0..100)
            _strength = vibratingNote100ToLog(value)
//            Log.v("Metronome", "VibratingNote.strength: $_strength")
        }
        get() {
            return vibratingNoteLogTo100(_strength)
        }


    fun vibrate(volume: Float, note: NoteListItem, bpmQuarter: Float) {
        val halfNoteDurationInMillis = (0.5f * note.duration.durationInMillis(bpmQuarter)).toLong()
        val duration = min(halfNoteDurationInMillis, (_strength * getNoteVibrationDuration(note.id)).toLong())

        if (duration <= 0L)
            return

        vibrator?.let {
            if (!it.hasVibrator())
                return

            if (System.currentTimeMillis() < earliestNextVibrationTime)
                return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val v = min(255, (volume * 255).toInt())
                if (v > 0)
                    it.vibrate(VibrationEffect.createOneShot(duration, v))
            } else {
                it.vibrate(duration)
            }
            earliestNextVibrationTime = System.currentTimeMillis() + (1.2f * duration).toLong()
        }
    }
}