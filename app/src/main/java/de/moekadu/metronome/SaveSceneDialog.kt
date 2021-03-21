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

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class SaveSceneDialog {
    companion object {
        @SuppressLint("SimpleDateFormat")
        fun save(context: Context, bpm: Float?, noteList: ArrayList<NoteListItem>?, saveItem: (Scene) -> Boolean) {
            if (bpm == null || noteList == null)
                return

            val editText = EditText(context).apply {
                setHint(R.string.save_name)
                inputType = InputType.TYPE_CLASS_TEXT
            }

            val dialogBuilder = AlertDialog.Builder(context).apply {
                setTitle(R.string.save_settings_dialog_title)
                setView(editText)
                setNegativeButton(R.string.dismiss) { dialog, _ -> dialog.cancel() }
                setPositiveButton(R.string.save) { _, _ ->
                    var title = editText.text.toString()
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy")
                    val timeFormat = SimpleDateFormat("HH:mm")
                    val calendarDate = Calendar.getInstance().time
                    val date = dateFormat.format(calendarDate)
                    val time = timeFormat.format(calendarDate)

                    //                    Log.v("Metronome", item.playList);
                    if (title.length > 200) {
                        title = title.substring(0, 200)
                        Toast.makeText(context, context.getString(R.string.max_allowed_characters, 200), Toast.LENGTH_SHORT).show()
                    }

                    val item = Scene(title, date, time, bpm, noteListToString(noteList), Scene.NO_STABLE_ID)
                    val success = saveItem(item)
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.saved_scene_message, item.title),
                                Toast.LENGTH_SHORT).show()
                    }
                }
            }
            dialogBuilder.show()
        }
    }
}