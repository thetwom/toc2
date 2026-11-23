/*
 * Copyright 2024 Michael Moessner
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

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import de.moekadu.metronome.R
import de.moekadu.metronome.metronomeproperties.SoundSetManager
import de.moekadu.metronome.metronomeproperties.SoundSets

/** Dialog for selecting the metronome sound set. The selection is applied immediately, also while
 *  the metronome is playing. */
class SoundSetDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val soundSets = SoundSets.all
        val names = soundSets.map { getString(it.nameResourceID) }.toTypedArray()
        val checkedIndex = soundSets.indexOfFirst { it.id == SoundSetManager.currentId }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.soundset)
            .setSingleChoiceItems(names, checkedIndex) { dialog, which ->
                SoundSetManager.select(requireContext(), soundSets[which].id)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        const val REQUEST_KEY = "de.moekadu.metronome.SOUND_SET_DIALOG"
    }
}
