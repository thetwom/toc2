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

package de.moekadu.metronome

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.*
import java.lang.NumberFormatException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SpeedLimiter(private val sharedPreferences: SharedPreferences, private val lifecycleOwner: LifecycleOwner) : LifecycleObserver{

    private val _minimumSpeed = MutableLiveData(InitialValues.minimumSpeed)
    private val _maximumSpeed = MutableLiveData(InitialValues.maximumSpeed)
    private val _speedIncrement = MutableLiveData(Utilities.speedIncrements[InitialValues.speedIncrementIndex])

    val minimumSpeed: LiveData<Float> get() = _minimumSpeed
    val maximumSpeed: LiveData<Float> get() = _maximumSpeed
    val speedIncrement: LiveData<Float> get() = _speedIncrement

    private val onSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener {
        sharedPreferences, key ->

        when (key) {
            "minimumspeed" -> {
                val newMinimumSpeed = sharedPreferences.getString("minimumspeed", InitialValues.minimumSpeed.toString())
                _minimumSpeed.value = newMinimumSpeed!!.toFloat()
            }
            "maximumspeed" -> {
                val newMaximumSpeed = sharedPreferences.getString("maximumspeed", InitialValues.maximumSpeed.toString())
                _maximumSpeed.value = newMaximumSpeed!!.toFloat()
            }
            "speedincrement" -> {
                val newSpeedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex)
                val newSpeedIncrement = Utilities.speedIncrements[newSpeedIncrementIndex]
                _speedIncrement.value = newSpeedIncrement
            }
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun readPreferences() {
        sharedPreferences.getString("minimumspeed", InitialValues.minimumSpeed.toString())?.let {
//            Log.v("Metronome", "SpeedLimiter.init: loading minimum speed: $it")
            try {
                _minimumSpeed.value = it.toFloat()
            } catch (e: NumberFormatException) {
                _minimumSpeed.value = InitialValues.minimumSpeed
            }
        }
        sharedPreferences.getString("maximumspeed", InitialValues.maximumSpeed.toString())?.let {
//            Log.v("Metronome", "SpeedLimiter.init: loading maximum speed: ${it.toFloat()}")
            try {
                _maximumSpeed.value = it.toFloat()
            } catch (e: NumberFormatException) {
                _minimumSpeed.value = InitialValues.maximumSpeed
            }
        }
        val speedIncrementIndex = sharedPreferences.getInt("speedincrement", InitialValues.speedIncrementIndex)
        _speedIncrement.value = Utilities.speedIncrements[speedIncrementIndex]
    }

    fun limit(speed: Float): Float {
        var speedCorrected = speed

        speedCorrected = min(speedCorrected, _maximumSpeed.value!!)
        speedCorrected = max(speedCorrected, _minimumSpeed.value!!)
        // Make speed match the increment
        speedCorrected = (speedCorrected / _speedIncrement.value!!).roundToInt() * _speedIncrement.value!!

        if(speedCorrected < _minimumSpeed.value!! - TOLERANCE)
            speedCorrected += _speedIncrement.value!!
        if(speedCorrected > _maximumSpeed.value!! + TOLERANCE)
            speedCorrected -= _speedIncrement.value!!

        return speedCorrected
    }

    fun checkSavedItemSpeedAndAlert(speed: Float, contextForMessages: Context) {
        var message = ""

        if(speed < _minimumSpeed.value!! - TOLERANCE) {
            message += contextForMessages.getString(R.string.saved_speed_too_small,
                    Utilities.getBpmString(speed), Utilities.getBpmString(_minimumSpeed.value!!))
        }

        if(speed > _maximumSpeed.value!! + TOLERANCE) {
            message += contextForMessages.getString(R.string.saved_speed_too_large,
                    Utilities.getBpmString(speed), Utilities.getBpmString(_maximumSpeed.value!!))
        }

        if(abs(speed / _speedIncrement.value!! - (speed / _speedIncrement.value!!).roundToInt()) > TOLERANCE) {
            message += contextForMessages.getString(R.string.inconsistent_saved_increment,
                    Utilities.getBpmString(speed), Utilities.getBpmString(_speedIncrement.value!!))
        }
        if (message != "") {
            message += contextForMessages.getString(R.string.inconsistent_summary)
            val builder = AlertDialog.Builder(contextForMessages)
                    .setTitle(R.string.inconsistent_load_title)
                    .setMessage(message)
                    .setNegativeButton(R.string.acknowledged) { dialog, _ -> dialog.dismiss() }
            builder.show()
        }
    }

    fun checkNewSpeedAndShowToast(speed: Float, contextForToast: Context): Boolean {
        var message: String? = null
        if(speed < minimumSpeed.value!! - TOLERANCE) {
            message = contextForToast.getString(R.string.speed_too_small,
                    Utilities.getBpmString(speed), Utilities.getBpmString(minimumSpeed.value!!))
        }
        if(speed > maximumSpeed.value!! + TOLERANCE) {
            message = contextForToast.getString(R.string.speed_too_large,
                    Utilities.getBpmString(speed), Utilities.getBpmString(maximumSpeed.value!!))
        }
        if(abs(speed / speedIncrement.value!! - (speed / speedIncrement.value!!).roundToInt()) > TOLERANCE) {
            message = contextForToast.getString(R.string.inconsistent_increment,
                    Utilities.getBpmString(speed), Utilities.getBpmString(speedIncrement.value!!))
        }
        if (message != null) {
            Toast.makeText(contextForToast, message, Toast.LENGTH_LONG).show()
        }

        return message == null
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun onDestroy() {
//        Log.v("Metronome", "SpeedLimiter.onDestroy")
        lifecycleOwner.lifecycle.removeObserver(this)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    companion object {
        const val TOLERANCE = 1.0e-6f
    }
}