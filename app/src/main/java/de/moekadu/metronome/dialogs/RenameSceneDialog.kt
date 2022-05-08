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

package de.moekadu.metronome.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import de.moekadu.metronome.R

class RenameSceneDialog : DialogFragment() {
    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.save_scene_dialog, null)
        val editText = view.findViewById<EditText>(R.id.scene_title)
        editText.setText(arguments?.getString(TITLE_KEY, ""))

        val dialog = AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.rename_scene)
            setView(view)
            setNegativeButton(R.string.dismiss) { _, _ -> dismiss() }
            setPositiveButton(R.string.done) { _, _ ->
                var newName = editText.text.toString()
                if (newName.length > 200) {
                    newName = newName.substring(0, 200)
                    Toast.makeText(context, context.getString(R.string.max_allowed_characters, 200), Toast.LENGTH_SHORT).show()
                }
                val bundle = Bundle(1)
                bundle.putString(TITLE_KEY, newName)
                setFragmentResult(REQUEST_KEY, bundle)
            }
        }.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val editText = view.findViewById<EditText>(R.id.scene_title)
        editText.requestFocus()
    }

    override fun onDismiss(dialog: DialogInterface) {
        // this seems only necessary on some devices ...
        (dialog as AlertDialog).window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        super.onDismiss(dialog)
    }

    companion object {
        const val REQUEST_KEY = "dialogs.SaveSceneDialog: rename scene"
        const val TITLE_KEY = "title"

        fun createInstance(initialTitle: String): RenameSceneDialog {
            val dialog = RenameSceneDialog()
            val bundle = Bundle(1)
            bundle.putString(TITLE_KEY, initialTitle)
            dialog.arguments = bundle
            return dialog
        }
    }
}