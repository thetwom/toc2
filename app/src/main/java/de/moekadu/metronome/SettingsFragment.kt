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

package de.moekadu.metronome

import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.*
import kotlin.math.roundToInt

/**
 * A simple {@link Fragment} subclass.
 */
class SettingsFragment: PreferenceFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
//        super.onPrepareOptionsMenu(menu);
        val settingsItem = menu.findItem(R.id.action_properties)
        settingsItem?.isVisible = false

        val loadDataItem = menu.findItem(R.id.action_load)
        loadDataItem?.isVisible = false

        val saveDataItem = menu.findItem(R.id.action_save)
        saveDataItem?.isVisible = false

        val archive = menu.findItem(R.id.action_archive)
        archive?.isVisible = false

        val unarchive = menu.findItem(R.id.action_unarchive)
        unarchive?.isVisible = false

        val clearAll = menu.findItem(R.id.action_clear_all)
        clearAll?.isVisible = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val vibratePreference = findPreference("vibrate") as SwitchPreferenceCompat?
        require(vibratePreference != null)
        if (!vibratingNoteHasHardwareSupport(activity)) {
//            vibratePreference.isVisible = false // leave visible but deactivate
            vibratePreference.isEnabled = false
            vibratePreference.isChecked = false
            vibratePreference.summary = getString(R.string.not_supported_by_hardware)
        }
        val vibrationStrength = findPreference("vibratestrength") as SeekBarPreference?
        require(vibrationStrength != null)
        vibrationStrength.updatesContinuously = true
        vibrationStrength.seekBarIncrement = 1
        vibrationStrength.min = 0
        vibrationStrength.max = 100
        vibrationStrength.summary = getVibrationStrengthSummary(vibrationStrength.value)
        vibrationStrength.setOnPreferenceChangeListener { _, newValue ->
            vibrationStrength.summary = getVibrationStrengthSummary(newValue as Int)
            true
        }

        val appearance = findPreference("appearance") as ListPreference?
        require(appearance != null)
        appearance.summary = getAppearanceSummary()
        appearance.setOnPreferenceChangeListener { _, _ ->
            val act = activity as MainActivity?
            appearance.summary = getAppearanceSummary()
            act?.recreate()
            true
        }

        val speedIncrement = findPreference("speedincrement") as SeekBarPreference?
        require(speedIncrement != null)
        speedIncrement.updatesContinuously = true
        speedIncrement.seekBarIncrement = 1
        speedIncrement.min = 0
        speedIncrement.max = Utilities.speedIncrements.size - 1
        if(Utilities.speedIncrements.size > speedIncrement.value) {
            val speedIncrementValue = Utilities.speedIncrements[speedIncrement.value]
            speedIncrement.summary = getString(R.string.bpm, Utilities.getBpmString(speedIncrementValue, speedIncrementValue))
        }
        speedIncrement.setOnPreferenceChangeListener { _, newValue ->
            val incrementIndex = newValue as Int
            if (incrementIndex < Utilities.speedIncrements.size) {
                    val speedIncrementValue = Utilities.speedIncrements[incrementIndex]
                speedIncrement.summary = getString(R.string.bpm, Utilities.getBpmString(speedIncrementValue, speedIncrementValue))
                }
                true
            }

        val speedIncrementValue = Utilities.speedIncrements[speedIncrement.value]

        val speedSensitivity = findPreference("speedsensitivity") as SeekBarPreference?
        require(speedSensitivity != null)
        speedSensitivity.updatesContinuously = true
        speedSensitivity.summary = getString(R.string.speed_sensitivity_summary, Utilities.percentage2sensitivity(speedSensitivity.value.toFloat()))
        speedSensitivity.setOnPreferenceChangeListener { _, newValue ->
            val percentage = newValue as Int
            val sensitivity = Utilities.percentage2sensitivity(percentage.toFloat())
            speedSensitivity.summary = getString(R.string.speed_sensitivity_summary, sensitivity)
            true
        }

        val minimumSpeed = findPreference("minimumspeed") as EditTextPreference?
        require(minimumSpeed != null)
        if(minimumSpeed.text == null)
            minimumSpeed.text = InitialValues.minimumSpeed.toString()

        minimumSpeed.summary = getString(R.string.bpm, Utilities.getBpmString(minimumSpeed.text.toFloat(), speedIncrementValue))
        minimumSpeed.setOnBindEditTextListener(EditTextPreference.OnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        })

        val maximumSpeed = findPreference("maximumspeed") as EditTextPreference?
        require(maximumSpeed != null)
        if(maximumSpeed.text == null)
            maximumSpeed.text = InitialValues.maximumSpeed.toString()

        maximumSpeed.summary = getString(R.string.bpm, Utilities.getBpmString(maximumSpeed.text.toFloat(), speedIncrementValue))
        maximumSpeed.setOnBindEditTextListener(EditTextPreference.OnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        })

        minimumSpeed.setOnPreferenceChangeListener { _, newValue ->
            val speed = (newValue as String).toFloat()
            val maxSpeed = maximumSpeed.text.toFloat()
            if (speed < maxSpeed) {
                minimumSpeed.summary = getString(R.string.bpm, Utilities.getBpmString(speed))
                true
            }
            else {
                Toast.makeText(activity, getString(R.string.min_speed_higher_maximum, getString(R.string.bpm, newValue), getString(R.string.bpm, maximumSpeed.text)), Toast.LENGTH_LONG).show()
//                Toast.makeText(activity, "Invalid minimum speed: $speed", Toast.LENGTH_SHORT).show()
                false
            }
        }

        maximumSpeed.setOnPreferenceChangeListener { _, newValue ->
            val speed = (newValue as String).toFloat()
            val minSpeed = minimumSpeed.text.toFloat()
            if (speed > minSpeed) {
                maximumSpeed.summary = getString(R.string.bpm, Utilities.getBpmString(speed))
                true
            } else {
                Toast.makeText(activity, getString(R.string.max_speed_lower_minimum, getString(R.string.bpm, newValue), getString(R.string.bpm, minimumSpeed.text)), Toast.LENGTH_LONG).show()
//                Toast.makeText(activity, "Invalid maximum speed: $speed", Toast.LENGTH_SHORT).show()
                false
            }
        }

        val screenOnPreference = findPreference("screenon") as SwitchPreferenceCompat?
        require(screenOnPreference != null)
        screenOnPreference.setOnPreferenceChangeListener { _, newValue ->
            val screenOn = newValue as Boolean
            if (screenOn)
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            true
        }

        val resetSettings = findPreference("setdefault") as Preference?
        require(resetSettings != null)
        resetSettings.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            context?.let { ctx ->
                val builder = AlertDialog.Builder(ctx)
                        .setTitle(R.string.reset_settings_prompt)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            vibratePreference.isChecked = false
                            vibrationStrength.value = 50
                            vibrationStrength.onPreferenceChangeListener.onPreferenceChange(vibrationStrength, 0)

                            minimumSpeed.text = InitialValues.minimumSpeed.toString()
                            minimumSpeed.onPreferenceChangeListener.onPreferenceChange(minimumSpeed, InitialValues.minimumSpeed.toString())
                            maximumSpeed.text = InitialValues.maximumSpeed.toString()
                            maximumSpeed.onPreferenceChangeListener.onPreferenceChange(maximumSpeed, InitialValues.maximumSpeed.toString())
                            speedIncrement.value = 3
                            speedIncrement.onPreferenceChangeListener.onPreferenceChange(speedIncrement, 3)

                            speedSensitivity.value = 30
                            speedSensitivity.onPreferenceChangeListener.onPreferenceChange(speedSensitivity, 30)

                            appearance.value = "auto"
                            appearance.onPreferenceChangeListener.onPreferenceChange(appearance, "auto")

                            screenOnPreference.isChecked = false
                            screenOnPreference.onPreferenceChangeListener.onPreferenceChange(screenOnPreference, false)
                        }
                        .setNegativeButton(R.string.no) { dialog, _ -> dialog?.cancel() }
                builder.show()
            }
            false
        }

        val aboutPreference = findPreference("about") as Preference?
        require(aboutPreference != null)
        aboutPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val textView = TextView(context)
            //textView.text = getString(R.string.about_message, getString(R.string.version))
            textView.text = getString(R.string.about_message, BuildConfig.VERSION_NAME)
            val pad = Utilities.dp2px(20f).roundToInt()
            textView.setPadding(pad, pad, pad, pad)
            context?.let { ctx ->
                val builder = AlertDialog.Builder(ctx)
                        .setTitle(R.string.about)
                        .setView(textView)
                builder.show()
            }
            false
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    private fun getAppearanceSummary(): String {
        val listPreference = findPreference("appearance") as ListPreference?
        require(listPreference != null)
        val state = listPreference.value

        if (state == null || state == "auto") {
            return getString(R.string.system_appearance)
        } else if (state == "dark") {
            return getString(R.string.dark_appearance)
        } else if (state == "light") {
            return getString(R.string.light_appearance)
        }
        throw RuntimeException("No summary for given appearance value")
    }

    private fun getVibrationStrengthSummary(value: Int): String {
        return if (value in 0 until 25)
            getString(R.string.low_strength)
        else if (value in 25 .. 75)
            getString(R.string.medium_strength)
        else
            getString(R.string.high_strength)
    }
}
