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

class SaveDataDialog {
    companion object {
        @SuppressLint("SimpleDateFormat")
        fun save(context: Context, speed: Float?, noteList: ArrayList<NoteListItem>?, saveItem: (SavedItem) -> Boolean) {
            if (speed == null || noteList == null)
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
                    val item = SavedItem()
                    item.title = editText.text.toString()
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy")
                    val timeFormat = SimpleDateFormat("HH:mm")
                    val date = Calendar.getInstance().time
                    item.date = dateFormat.format(date)
                    item.time = timeFormat.format(date)

                    item.bpm = speed
                    item.noteList = noteList.toString()
                    //                    Log.v("Metronome", item.playList);
                    if (item.title.length > 200) {
                        item.title = item.title.substring(0, 200)
                        Toast.makeText(context, context.getString(R.string.max_allowed_characters, 200), Toast.LENGTH_SHORT).show()
                    }
                    val success = saveItem(item)
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.saved_item_message, item.title),
                                Toast.LENGTH_SHORT).show()
                    }
                }
            }
            dialogBuilder.show()
        }
    }
}