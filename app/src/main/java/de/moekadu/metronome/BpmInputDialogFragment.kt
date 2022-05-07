package de.moekadu.metronome

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

class BpmInputDialogFragment() : DialogFragment() {
    private var bpmText = ""

    constructor(bpm: Bpm) : this() {
        bpmText = Utilities.getBpmString(bpm.bpm, false)
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.bpm_input_dialog, null)
        val editText = view.findViewById<EditText>(R.id.bpm_text)
        editText.setText(bpmText)

        editText.hint = getString(R.string.bpm, "")
        editText.setSelectAllOnFocus(true)

        val dialog = AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.set_new_speed)
            setPositiveButton(R.string.done) { _, _ ->
                val newBpmText = editText.text.toString()
                val newBpm = newBpmText.toFloatOrNull()
                if (newBpm == null) {
                    Toast.makeText(
                        requireContext(), "${getString(R.string.invalid_speed)}$newBpmText",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val bundle = Bundle(1)
                    bundle.putFloat(BPM_KEY, newBpm)
                    setFragmentResult(REQUEST_KEY, bundle)
                }
            }
            setNegativeButton(R.string.abort) { _, _ ->
                dismiss()
            }
            setView(view)
        }.create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        editText.requestFocus()
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val editText = view.findViewById<EditText>(R.id.bpm_text)
        editText.requestFocus()
    }

    override fun onDismiss(dialog: DialogInterface) {
        // this seems only necessary on some devices ...
        (dialog as AlertDialog).window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        super.onDismiss(dialog)
    }

    companion object {
        const val REQUEST_KEY = "bpm from input dialog fragment"
        const val BPM_KEY = "bpm"
    }
}