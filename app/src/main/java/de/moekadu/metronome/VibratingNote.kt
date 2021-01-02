package de.moekadu.metronome

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.*

fun vibratingNoteHasHardwareSupport(context: Context?): Boolean {
    val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    if (vibrator != null && vibrator.hasVibrator())
        return true
    return false
}

const val maximumVibrationScaling = 2.0f
fun vibratingNote100ToLog(value: Int) = maximumVibrationScaling.pow((value-50) / 50f)
fun vibratingNoteLogTo100(value: Float) = (50f * log(value, maximumVibrationScaling) + 50).toInt()

class VibratingNote(context: Context)  {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
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


    fun vibrate(volume: Float, note: NoteListItem) {
        val halfNoteDurationInMillis = (0.5f * 1000 * note.duration).toLong()
        val duration = min(halfNoteDurationInMillis, (_strength * getNoteVibrationDuration(note.id)).toLong())

        vibrator?.let {
            if (!it.hasVibrator())
                return

            if (System.currentTimeMillis() < earliestNextVibrationTime)
                return

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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