/*
 * Copyright 2020 Michael Moessner
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

package de.moekadu.metronome.preferences

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.*
import de.moekadu.metronome.misc.InitialValues
import de.moekadu.metronome.R
import de.moekadu.metronome.dialogs.SpeedLimiterInfoDialog
import de.moekadu.metronome.misc.Utilities
import de.moekadu.metronome.metronomeproperties.Bpm
import java.lang.NumberFormatException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SpeedLimiter(private val sharedPreferences: SharedPreferences, private val lifecycleOwner: LifecycleOwner) : LifecycleEventObserver {

    private val _minimumBpm = MutableLiveData(InitialValues.minimumBpm)
    private val _maximumBpm = MutableLiveData(InitialValues.maximumBpm)
    private val _bpmIncrement = MutableLiveData(Utilities.bpmIncrements[InitialValues.bpmIncrementIndex])

    val minimumBpm: LiveData<Float> get() = _minimumBpm
    val maximumBpm: LiveData<Float> get() = _maximumBpm
    val bpmIncrement: LiveData<Float> get() = _bpmIncrement

    private val onSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener {
        sharedPreferences, key ->

        when (key) {
            "minimumspeed" -> {
                val newMinimumBpm = sharedPreferences.getString("minimumspeed", InitialValues.minimumBpm.toString())
                _minimumBpm.value = newMinimumBpm!!.toFloat()
            }
            "maximumspeed" -> {
                val newMaximumBpm = sharedPreferences.getString("maximumspeed", InitialValues.maximumBpm.toString())
                _maximumBpm.value = newMaximumBpm!!.toFloat()
            }
            "speedincrement" -> {
                val newBpmIncrementIndex = sharedPreferences.getInt("speedincrement",
                    InitialValues.bpmIncrementIndex
                )
                val newBpmIncrement = Utilities.bpmIncrements[newBpmIncrementIndex]
                _bpmIncrement.value = newBpmIncrement
            }
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_START) {
            readPreferences()
        } else if (event == Lifecycle.Event.ON_DESTROY) {
            onDestroy()
        }
    }

    private fun readPreferences() {
        sharedPreferences.getString("minimumspeed", InitialValues.minimumBpm.toString())?.let {
//            Log.v("Metronome", "SpeedLimiter.init: loading minimum bpm: $it")
            try {
                _minimumBpm.value = it.toFloat()
            } catch (e: NumberFormatException) {
                _minimumBpm.value = InitialValues.minimumBpm
            }
        }
        sharedPreferences.getString("maximumspeed", InitialValues.maximumBpm.toString())?.let {
//            Log.v("Metronome", "SpeedLimiter.init: loading maximum bpm: ${it.toFloat()}")
            try {
                _maximumBpm.value = it.toFloat()
            } catch (e: NumberFormatException) {
                _minimumBpm.value = InitialValues.maximumBpm
            }
        }
        val bpmIncrementIndex = sharedPreferences.getInt("speedincrement",
            InitialValues.bpmIncrementIndex
        )
        _bpmIncrement.value = Utilities.bpmIncrements[bpmIncrementIndex]
    }

    fun limit(bpm: Float): Float {
        var bpmCorrected = bpm

        bpmCorrected = min(bpmCorrected, _maximumBpm.value!!)
        bpmCorrected = max(bpmCorrected, _minimumBpm.value!!)
        // Make bpm match the increment
        bpmCorrected = (bpmCorrected / _bpmIncrement.value!!).roundToInt() * _bpmIncrement.value!!

        // limit to maximum and minimum bpm again and don't allow zero
        if(bpmCorrected > _maximumBpm.value!! + TOLERANCE)
            bpmCorrected = max(bpmCorrected - _bpmIncrement.value!!, _minimumBpm.value!!)
        if(bpmCorrected < _minimumBpm.value!! - TOLERANCE || bpmCorrected < TOLERANCE)
            bpmCorrected = min(bpmCorrected + _bpmIncrement.value!!, _maximumBpm.value!!)

        return bpmCorrected
    }

    fun limit(bpm: Bpm): Bpm {
        return bpm.copy(bpm = limit(bpm.bpm))
    }

    fun checkSavedItemBpmAndAlert(bpm: Float, contextForMessages: Context, fragmentManager: FragmentManager) {
        var message = ""

        if(bpm < _minimumBpm.value!! - TOLERANCE) {
            message += contextForMessages.getString(
                R.string.saved_speed_too_small,
                Utilities.getBpmString(bpm), Utilities.getBpmString(_minimumBpm.value!!)
            )
        }

        if(bpm > _maximumBpm.value!! + TOLERANCE) {
            message += contextForMessages.getString(
                R.string.saved_speed_too_large,
                Utilities.getBpmString(bpm), Utilities.getBpmString(_maximumBpm.value!!)
            )
        }

        if(abs(bpm / _bpmIncrement.value!! - (bpm / _bpmIncrement.value!!).roundToInt()) > TOLERANCE) {
            message += contextForMessages.getString(
                R.string.inconsistent_saved_increment,
                Utilities.getBpmString(bpm), Utilities.getBpmString(_bpmIncrement.value!!)
            )
        }
        if (message != "") {
            message += contextForMessages.getString(R.string.inconsistent_summary)
            val dialog = SpeedLimiterInfoDialog(message)
            dialog.show(fragmentManager, "speed limiter info dialog")
        }
    }

    fun checkNewBpmAndShowToast(bpm: Float, contextForToast: Context): Boolean {
        var message: String? = null
        if(bpm < minimumBpm.value!! - TOLERANCE) {
            message = contextForToast.getString(
                R.string.speed_too_small,
                Utilities.getBpmString(bpm), Utilities.getBpmString(minimumBpm.value!!)
            )
        }
        if(bpm > maximumBpm.value!! + TOLERANCE) {
            message = contextForToast.getString(
                R.string.speed_too_large,
                Utilities.getBpmString(bpm), Utilities.getBpmString(maximumBpm.value!!)
            )
        }
        if(abs(bpm / bpmIncrement.value!! - (bpm / bpmIncrement.value!!).roundToInt()) > TOLERANCE) {
            message = contextForToast.getString(
                R.string.inconsistent_increment,
                Utilities.getBpmString(bpm), Utilities.getBpmString(bpmIncrement.value!!)
            )
        }
        if (message != null) {
            Toast.makeText(contextForToast, message, Toast.LENGTH_LONG).show()
        }

        return message == null
    }

    private fun onDestroy() {
//        Log.v("Metronome", "SpeedLimiter.onDestroy")
        lifecycleOwner.lifecycle.removeObserver(this)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    companion object {
        const val TOLERANCE = 1.0e-6f
    }
}