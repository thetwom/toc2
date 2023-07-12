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

package de.moekadu.metronome.fragments

import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.*
import de.moekadu.metronome.*
import de.moekadu.metronome.R
import de.moekadu.metronome.dialogs.AboutDialog
import de.moekadu.metronome.dialogs.ResetSettingsDialog
import de.moekadu.metronome.misc.InitialValues
import de.moekadu.metronome.misc.Utilities
import de.moekadu.metronome.players.vibratingNote100ToLog
import de.moekadu.metronome.players.vibratingNoteHasHardwareSupport

/**
 * A simple {@link Fragment} subclass.
 */
class SettingsFragment: PreferenceFragmentCompat() {

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menu.clear() // this should not be needed, but not setting a menuProvider doesn't work
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return false
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        activity?.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

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

        val vibrationDelay = findPreference<SeekBarPreference>("vibratedelay") ?: throw RuntimeException("no vibrate delay setting")
        vibrationDelay.updatesContinuously = true
        vibrationDelay.summary =  getString(R.string.milliseconds, vibrationDelay.value)
        vibrationDelay.setOnPreferenceChangeListener { _, newValue ->
            vibrationDelay.summary = getString(R.string.milliseconds, newValue as Int)
            true
        }

        val visualDelay = findPreference<SeekBarPreference>("visualdelay") ?: throw RuntimeException("no visual delay setting")
        visualDelay.updatesContinuously = true
        visualDelay.summary =  getString(R.string.milliseconds, visualDelay.value)
        visualDelay.setOnPreferenceChangeListener { _, newValue ->
            visualDelay.summary = getString(R.string.milliseconds, newValue as Int)
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

        val compactScenesLayout = findPreference("compact_scenes_layout") as SwitchPreferenceCompat?
        require(compactScenesLayout != null)

        val bpmIncrement = findPreference("speedincrement") as SeekBarPreference?
        require(bpmIncrement != null)
        bpmIncrement.updatesContinuously = true
        bpmIncrement.seekBarIncrement = 1
        bpmIncrement.min = 0
        bpmIncrement.max = Utilities.bpmIncrements.size - 1
        if(Utilities.bpmIncrements.size > bpmIncrement.value) {
            val bpmIncrementValue = Utilities.bpmIncrements[bpmIncrement.value]
            bpmIncrement.summary = getString(
                R.string.bpm,
                Utilities.getBpmString(bpmIncrementValue, bpmIncrementValue)
            )
        }
        bpmIncrement.setOnPreferenceChangeListener { _, newValue ->
            val incrementIndex = newValue as Int
            if (incrementIndex < Utilities.bpmIncrements.size) {
                    val bpmIncrementValue = Utilities.bpmIncrements[incrementIndex]
                bpmIncrement.summary = getString(
                    R.string.bpm,
                    Utilities.getBpmString(bpmIncrementValue, bpmIncrementValue)
                )
                }
                true
            }

        val bpmIncrementValue = Utilities.bpmIncrements[bpmIncrement.value]

        val bpmSensitivity = findPreference("speedsensitivity") as SeekBarPreference?
        require(bpmSensitivity != null)
        bpmSensitivity.updatesContinuously = true
        bpmSensitivity.summary = getString(
            R.string.speed_sensitivity_summary,
            Utilities.percentage2sensitivity(bpmSensitivity.value.toFloat())
        )
        bpmSensitivity.setOnPreferenceChangeListener { _, newValue ->
            val percentage = newValue as Int
            val sensitivity = Utilities.percentage2sensitivity(percentage.toFloat())
            bpmSensitivity.summary = getString(R.string.speed_sensitivity_summary, sensitivity)
            true
        }

        val minimumBpm = findPreference("minimumspeed") as EditTextPreference?
        require(minimumBpm != null)
        if(minimumBpm.text == null)
            minimumBpm.text = InitialValues.minimumBpm.toString()

        minimumBpm.summary = getString(
            R.string.bpm,
            Utilities.getBpmString(minimumBpm.text!!.toFloat(), bpmIncrementValue)
        )
        minimumBpm.setOnBindEditTextListener(EditTextPreference.OnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        })

        val maximumBpm = findPreference("maximumspeed") as EditTextPreference?
        require(maximumBpm != null)
        if(maximumBpm.text == null)
            maximumBpm.text = InitialValues.maximumBpm.toString()

        maximumBpm.summary = getString(
            R.string.bpm,
            Utilities.getBpmString(maximumBpm.text!!.toFloat(), bpmIncrementValue)
        )
        maximumBpm.setOnBindEditTextListener(EditTextPreference.OnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        })

        minimumBpm.setOnPreferenceChangeListener { _, newValue ->
            val bpm = (newValue as String).toFloat()
            val maxBpm = maximumBpm.text!!.toFloat()
            if (bpm < maxBpm) {
                minimumBpm.summary = getString(R.string.bpm, Utilities.getBpmString(bpm))
                true
            }
            else {
                Toast.makeText(activity, getString(
                    R.string.min_speed_higher_maximum, getString(R.string.bpm, newValue), getString(
                        R.string.bpm, maximumBpm.text)), Toast.LENGTH_LONG).show()
//                Toast.makeText(activity, "Invalid minimum bpm: $bpm", Toast.LENGTH_SHORT).show()
                false
            }
        }

        maximumBpm.setOnPreferenceChangeListener { _, newValue ->
            val bpm = (newValue as String).toFloat()
            val minBpm = minimumBpm.text!!.toFloat()
            if (bpm > minBpm && bpm <= ABSOLUTE_MAXIMUM_SPEED) {
                maximumBpm.summary = getString(R.string.bpm, Utilities.getBpmString(bpm))
                true
            } else if (bpm <= minBpm) {
                Toast.makeText(activity, getString(
                    R.string.max_speed_lower_minimum, getString(R.string.bpm, newValue), getString(
                        R.string.bpm, minimumBpm.text)), Toast.LENGTH_LONG).show()
//                Toast.makeText(activity, "Invalid maximum bpm: $bpm", Toast.LENGTH_SHORT).show()
                false
            } else { // bpm > ABSOLUTE_MAXIMUM_SPEED
                Toast.makeText(activity, getString(
                    R.string.max_speed_larger_than_allowed, getString(
                        R.string.bpm, newValue), getString(R.string.bpm, ABSOLUTE_MAXIMUM_SPEED.toString())), Toast.LENGTH_LONG).show()
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

        val tickvis = findPreference("tickvisualization") as ListPreference?
        require(tickvis != null)
        tickvis.summary = getTickVisualizationSummary(tickvis.value)
        tickvis.setOnPreferenceChangeListener { it, value ->
            it.summary = getTickVisualizationSummary(value as String)
            true
        }

        val resetSettings = findPreference("setdefault") as Preference?
        require(resetSettings != null)
        resetSettings.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val dialog = ResetSettingsDialog()
            dialog.show(parentFragmentManager, "tag")
            false
        }

        val aboutPreference = findPreference("about") as Preference?
        require(aboutPreference != null)
        aboutPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val dialog = AboutDialog()
            dialog.show(parentFragmentManager, "tag")
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

    private fun getTickVisualizationSummary(state: String): String {
        return when (state) {
            "leftright" -> getString(R.string.thickvis_leftright)
            "bounce" -> getString(R.string.tickvis_bounce)
            "fade" -> getString(R.string.thickvis_fade)
            else -> throw RuntimeException("No summary for given tickvisualization value")
        }
    }

    private fun getVibrationStrengthSummary(value: Int): String {
        return when (value) {
            in 0 until 25 -> getString(R.string.low_strength, vibratingNote100ToLog(value))
            in 25 .. 75 -> getString(R.string.medium_strength, vibratingNote100ToLog(value))
            else -> getString(R.string.high_strength, vibratingNote100ToLog(value))
        }
    }

    companion object {
        const val ABSOLUTE_MAXIMUM_SPEED = 10000f
    }
}
