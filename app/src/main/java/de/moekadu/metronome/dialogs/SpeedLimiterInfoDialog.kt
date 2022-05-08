package de.moekadu.metronome.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import de.moekadu.metronome.R

class SpeedLimiterInfoDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = arguments?.getString("message", "") ?: ""

        val dialog = AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.inconsistent_load_title)
            setMessage(message)
            setNegativeButton(R.string.acknowledged) { _, _ -> dismiss() }
        }.create()
        return dialog
    }

    companion object {
        fun createInstance(message: String): SpeedLimiterInfoDialog {
            return SpeedLimiterInfoDialog().apply {
                val bundle = Bundle(1).apply {
                    putString("message", message)
                }
                arguments = bundle
            }
        }
    }
}