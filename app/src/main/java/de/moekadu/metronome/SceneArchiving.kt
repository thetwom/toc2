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
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AlertDialog
import androidx.core.database.getStringOrNull

class SceneArchiving(private val scenesFragment: ScenesFragment) {

    private inner class FileWriterContract : ActivityResultContract<String, String?>() {

        override fun createIntent(context: Context, input: String?): Intent {
            return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "metronome.txt")
                // default path
                // putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): String? {
            val uri = intent?.data
            val context = scenesFragment.context

            if (uri == null || context == null)
                return null

            val fileData = scenesFragment.getDatabaseString() ?: return null

            context.contentResolver?.openOutputStream(uri)?.use { stream ->
                stream.write((fileData).toByteArray())
            }
            return getFilenameFromUri(context, uri)
        }
    }

    private class FileReaderContract : ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, input: String?): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                // default path
                // putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return intent?.data
        }
    }

    private val _archiveScenes = scenesFragment.registerForActivityResult(FileWriterContract()) { filename ->
        if (filename == null) {
            Toast.makeText(scenesFragment.requireContext(), R.string.failed_to_archive_scenes, Toast.LENGTH_LONG).show()
        } else {
            scenesFragment.context?.let { context ->
                Toast.makeText(context, context.getString(R.string.database_saved, filename), Toast.LENGTH_LONG).show()
            }
        }
    }

    private val _unarchiveScenes = scenesFragment.registerForActivityResult(FileReaderContract()) { uri ->
        val context = scenesFragment.context

        if (context != null && uri != null) {
            val filename = getFilenameFromUri(context, uri)

            val builder = AlertDialog.Builder(context).apply {
                setTitle(R.string.load_scenes)
                setNegativeButton(R.string.abort) { dialog, _ -> dialog.dismiss() }
                setItems(R.array.load_scenes_list) { _, which ->
                    val array = context.resources.getStringArray(R.array.load_scenes_list)
                    val task = when (array[which]) {
                        context.getString(R.string.prepend_current_list) -> SceneDatabase.InsertMode.Prepend
                        context.getString(R.string.append_current_list) -> SceneDatabase.InsertMode.Append
                        else -> SceneDatabase.InsertMode.Replace
                    }

                    context.contentResolver?.openInputStream(uri)?.use { stream ->
                        stream.reader().use {
                            val databaseString = it.readText()
                            scenesFragment.loadDatabaseFromString(databaseString, task, filename)
                        }
                    }
                }
            }
            builder.show()
        }
    }

    fun archiveScenes(sceneDatabase: SceneDatabase?) {
        if (sceneDatabase?.size ?: 0 == 0) {
            Toast.makeText(scenesFragment.requireContext(), R.string.database_empty, Toast.LENGTH_LONG).show()
        } else {
            _archiveScenes.launch(null)
        }
    }

    fun unarchiveScenes() {
        _unarchiveScenes.launch(null)
    }

    private fun getFilenameFromUri(context: Context, uri: Uri): String? {
        var filename: String? = null
        context.contentResolver?.query(
                uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            filename = cursor.getStringOrNull(nameIndex)
            cursor.close()
        }
        return filename
    }
}
