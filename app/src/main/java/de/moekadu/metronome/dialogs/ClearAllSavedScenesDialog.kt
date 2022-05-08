package de.moekadu.metronome.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import de.moekadu.metronome.R

class ClearAllSavedScenesDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.clear_all_question)
            setNegativeButton(R.string.no) { _, _ -> dismiss() }
            setPositiveButton(R.string.yes) { _, _ ->
                val bundle = Bundle(1)
                bundle.putBoolean(CLEAR_ALL_KEY, true)
                setFragmentResult(REQUEST_KEY, bundle)
            }
        }.create()

        return dialog
    }

    companion object {
        const val REQUEST_KEY = "ClearAllSaveScenesDialog: clear all saved scenes"
        const val CLEAR_ALL_KEY = "clear all"
    }
}