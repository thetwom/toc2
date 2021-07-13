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
import androidx.fragment.app.FragmentActivity

class AppPreferences {
    companion object {
        private fun readPreferenceString(key: String, activity: FragmentActivity): String? {
            val preferences = activity.getPreferences(Context.MODE_PRIVATE)
            return preferences.getString(key, null)
        }

        private fun writePreferenceString(key: String, value: String, activity: FragmentActivity) {
            val preferences = activity.getPreferences(Context.MODE_PRIVATE)
            val editor = preferences.edit()
            editor.putString(key, value)
            editor.apply()
        }
        private fun readPreferenceFloat(key: String, default: Float, activity: FragmentActivity): Float {
            val preferences = activity.getPreferences(Context.MODE_PRIVATE)
            return preferences.getFloat(key, default)
        }
        private fun writePreferenceFloat(key: String, value: Float, activity: FragmentActivity) {
            val preferences = activity.getPreferences(Context.MODE_PRIVATE)
            val editor = preferences.edit()
            editor.putFloat(key, value)
            editor.apply()
        }
        private fun readPreferenceBoolean(key: String, default: Boolean, activity: FragmentActivity): Boolean {
            val preferences = activity.getPreferences(Context.MODE_PRIVATE)
            return preferences.getBoolean(key, default)
        }

        private fun writePreferenceBoolean(key: String, value: Boolean, activity: FragmentActivity) {
            val preferences = activity.getPreferences(Context.MODE_PRIVATE)
            val editor = preferences.edit()
            editor.putBoolean(key, value)
            editor.apply()
        }

        fun writeMetronomeState(bpm: Bpm?, noteList: ArrayList<NoteListItem>?, isMute: Boolean?,
                                activity: FragmentActivity) {
            if (bpm != null) {
                writePreferenceFloat("bpm", bpm.bpm, activity)
                writePreferenceString("beatNote", bpm.noteDuration.toString(), activity)
            }
            if (noteList != null)
                writePreferenceString("sound", noteListToString(noteList), activity)

            if (isMute != null)
                writePreferenceBoolean("isMute", isMute, activity)
        }

        fun readIsMute(activity: FragmentActivity): Boolean {
            return readPreferenceBoolean("isMute", false, activity)
        }

        fun readMetronomeBpm(activity: FragmentActivity): Bpm {
            val speed = readPreferenceFloat("speed", -1f, activity)
            val bpm = readPreferenceFloat("bpm", -1f, activity)
            val bpmToUse = if (bpm > 0.0) bpm else if (speed > 0.0) speed else InitialValues.bpm.bpm

            val noteDurationString = readPreferenceString("beatNote", activity) ?:  NoteDuration.Quarter.toString()
            val noteDuration = NoteDuration.valueOf(noteDurationString)
            return Bpm(bpmToUse, noteDuration)
        }

        fun readMetronomeNoteList(activity: FragmentActivity): ArrayList<NoteListItem> {
            readPreferenceString("sound", activity)?.let { noteListString ->
                return stringToNoteList(noteListString)
            }

            val noteList = ArrayList<NoteListItem>()
            noteList.add(NoteListItem(defaultNote, 1.0f, NoteDuration.Quarter))
            return noteList
        }

        fun writeScenesDatabase(databaseString: String?, activity: FragmentActivity) {
//            Log.v("Metronome", "AppPreferences.writeSavedItemsDatabase: $databaseString")
            if (databaseString != null && databaseString != "")
                writePreferenceString("savedDatabase", databaseString, activity)
        }
        fun readScenesDatabase(activity: FragmentActivity): String {
//            Log.v("Metronome", "AppPreferences.readScenesDatabase: ${readPreferenceString("savedDatabase", activity) ?: ""}")
            return readPreferenceString("savedDatabase", activity) ?: ""
        }
    }
}