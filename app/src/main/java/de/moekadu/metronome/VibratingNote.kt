package de.moekadu.metronome

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.min

fun vibratingNoteHasHardwareSupport(context: Context?): Boolean {
    val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    if (vibrator != null && vibrator.hasVibrator())
        return true
    return false
}

class VibratingNote(context: Context)  {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    private var lastPlayTime = 0L

    fun vibrate(volume: Float, duration: Long) {
        vibrator?.let {
            if (!it.hasVibrator())
                return

            if (System.currentTimeMillis() < lastPlayTime + 1.2f * duration)
                return

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val v = min(255, (volume * 255).toInt())
                if (v > 0)
                    it.vibrate(VibrationEffect.createOneShot(duration, v))
            } else {
                it.vibrate(duration)
            }
            lastPlayTime = System.currentTimeMillis()
        }
    }
}