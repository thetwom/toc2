package de.moekadu.metronome.dialogs

import android.app.Dialog
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import de.moekadu.metronome.R
import kotlinx.parcelize.Parcelize

class SpeedLimiterInfoDialog() : DialogFragment() {
    private var message = ""

    @Parcelize
    private class SavedState(val message: String) : Parcelable

    constructor(message: String) : this() {
        this.message = message
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val state = SavedState(message)
        outState.putParcelable("speed limiter info dialog fragment state", state)
        super.onSaveInstanceState(outState)
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        savedInstanceState?.let {
            it.getParcelable<SavedState>("speed limiter info dialog fragment state")?.let { storedState ->
                message = storedState.message
            }
        }

        val dialog = AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.inconsistent_load_title)
            setMessage(message)
            setNegativeButton(R.string.acknowledged) { _, _ -> dismiss() }
        }.create()
        return dialog
    }
}