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
import android.os.*
import androidx.annotation.RequiresApi
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

    private var lastVibrationUpdateMillies = 0L
    private data class DurationAndVolume(val duration: Long, val volume: Int)

    private val effectMap = hashMapOf<DurationAndVolume, VibrationEffect>()
    private val cacheSize = 100

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getVibrationEffect(duration: Long, volume: Int): VibrationEffect {
        if (effectMap.size >= cacheSize)
            effectMap.clear()

        val key = DurationAndVolume(duration, volume)
        val possibleEffect = effectMap[key]
        val effect = if (possibleEffect == null) {
//            Log.v("Metronome", "VibratingNote.getVibrationEffect: creating new effect with volume=$volume, duration=$duration")
            val newEffect = VibrationEffect.createOneShot(duration, volume)
            effectMap[key] = newEffect
            newEffect
        } else {
//            Log.v("Metronome", "VibratingNote.getVibrationEffect: reusing effect with volume=$volume, duration=$duration")
            possibleEffect
        }
        return effect
    }

    /** Vibrate.
     * @param volume Note volume (between 0f and 1f).
     * @param note Note.
     * @param bpmQuarter Bpm for a quarter note. This is needed to predict the time of the next
     *   note, and when the next note comes very early, reducing the vibration duration.
     * @param numNotesInNoteList Notes in note list. This is needed since we cache vibration effects
     *   and we don't want to cache too many effects. In theory, this can be 0, but this would
     *   effectively turn off the caching of effects.
     */
    fun vibrate(volume: Float, note: NoteListItem, bpmQuarter: Float, numNotesInNoteList: Int = 100) {
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
                if (v > 0) {
                    //    it.vibrate(VibrationEffect.createOneShot(duration, v))
                    val newTimeMillis = System.nanoTime() / 1000_000L
                    val diff = newTimeMillis - lastVibrationUpdateMillies
                    lastVibrationUpdateMillies = newTimeMillis
//                    Log.v("Metronome", "VibratingNote: time since last vibration = ${diff}")
                    it.vibrate(getVibrationEffect(duration, v))
                }
            } else {
                it.vibrate(duration)
            }
            earliestNextVibrationTime = System.currentTimeMillis() + (1.2f * duration).toLong()
        }
    }
}