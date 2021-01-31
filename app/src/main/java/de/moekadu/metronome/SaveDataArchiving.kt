package de.moekadu.metronome

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.database.getStringOrNull

class SaveDataArchiving(private val activity: MainActivity) {

    fun sendArchivingIntent(savedItemDatabase: SavedItemDatabase?) {
        if (savedItemDatabase?.size ?: 0 == 0) {
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

    fun archiveSavedItems(uri: Uri?, databaseString: String?) {
        if (uri == null)
            return

        activity.contentResolver?.openOutputStream(uri)?.use { stream ->
            stream.write((databaseString ?: "").toByteArray())
            Toast.makeText(activity,
                    activity.getString(R.string.database_saved, getFilenameFromUri(uri)),
                    Toast.LENGTH_LONG).show()
        }
    }

    fun unarchiveSaveItems(uri: Uri?, loadDatabaseFromString: (String, Int) -> Unit) {
        if (uri == null)
            return
        val builder = AlertDialog.Builder(activity).apply {
            setTitle(R.string.load_saved_items)
            setNegativeButton(R.string.abort) {dialog,_  -> dialog.dismiss()}
            setItems(R.array.load_saved_items_list) {_, which ->
                val array = activity.resources.getStringArray(R.array.load_saved_items_list)
                val task = when(array[which]) {
                    activity.getString(R.string.prepend_current_list) -> SavedItemDatabase.PREPEND
                    activity.getString(R.string.append_current_list) -> SavedItemDatabase.APPEND
                    else -> SavedItemDatabase.REPLACE
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
