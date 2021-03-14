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

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.database.getStringOrNull

class SceneArchiving(private val activity: MainActivity) {

    fun sendArchivingIntent(sceneDatabase: SceneDatabase?) {
        if (sceneDatabase?.size ?: 0 == 0) {
            Toast.makeText(activity, R.string.database_empty, Toast.LENGTH_LONG).show()
        } else {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "metronome.txt")
                // default path
                // putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
            }
            activity.startActivityForResult(intent, MainActivity.FILE_CREATE)
        }
    }

    fun sendUnarchivingIntent() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            // default path
            // putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }
        activity.startActivityForResult(intent, MainActivity.FILE_OPEN)
    }

    fun archiveScenes(uri: Uri?, databaseString: String?) {
        if (uri == null)
            return

        activity.contentResolver?.openOutputStream(uri)?.use { stream ->
            stream.write((databaseString ?: "").toByteArray())
            Toast.makeText(activity,
                    activity.getString(R.string.database_saved, getFilenameFromUri(uri)),
                    Toast.LENGTH_LONG).show()
        }
    }

    fun unarchiveScenes(uri: Uri?, loadDatabaseFromString: (String, SceneDatabase.InsertMode) -> Unit) {
        if (uri == null)
            return
        val builder = AlertDialog.Builder(activity).apply {
            setTitle(R.string.load_scenes)
            setNegativeButton(R.string.abort) {dialog,_  -> dialog.dismiss()}
            setItems(R.array.load_scenes_list) { _, which ->
                val array = activity.resources.getStringArray(R.array.load_scenes_list)
                val task = when(array[which]) {
                    activity.getString(R.string.prepend_current_list) -> SceneDatabase.InsertMode.Prepend
                    activity.getString(R.string.append_current_list) -> SceneDatabase.InsertMode.Append
                    else -> SceneDatabase.InsertMode.Replace
                }

                activity.contentResolver?.openInputStream(uri)?.use { stream ->
                    stream.reader().use {
                        val databaseString = it.readText()
                        loadDatabaseFromString(databaseString, task)
                    }
                }
            }
        }
        builder.show()
    }

    private fun getFilenameFromUri(uri: Uri): String? {
        var filename: String? = null
        activity.contentResolver?.query(
                uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            filename = cursor.getStringOrNull(nameIndex)
            cursor.close()
        }
        return filename
    }
}
