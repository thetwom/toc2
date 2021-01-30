package de.moekadu.metronome

import android.content.Context
import android.util.Log
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

        fun writeMetronomeState(speed: Float?, noteList: NoteList?, activity: FragmentActivity) {
            if (speed != null)
                writePreferenceFloat("speed", speed, activity)
            if (noteList != null)
                writePreferenceString("sound", noteList.toString(), activity)
        }

        fun readMetronomeSpeed(activity: FragmentActivity): Float {
            return readPreferenceFloat("speed", InitialValues.speed, activity)
        }
        fun readMetronomeNoteList(activity: FragmentActivity): NoteList {
            val noteList = NoteList()

            readPreferenceString("sound", activity)?.let { noteListString ->
                noteList.fromString(noteListString)
            }
            return noteList
        }

        fun writeSavedItemsDatabase(databaseString: String?, activity: FragmentActivity) {
            Log.v("Metronome", "AppPreferences.writeSavedItemsDatabase: $databaseString")
            if (databaseString != null && databaseString != "")
                writePreferenceString("savedDatabase", databaseString, activity)
        }
        fun readSavedItemsDatabase(activity: FragmentActivity): String {
            Log.v("Metronome", "AppPreferences.readSavedItemsDatabase: ${readPreferenceString("savedDatabase", activity) ?: ""}")
            return readPreferenceString("savedDatabase", activity) ?: ""
        }
    }
}