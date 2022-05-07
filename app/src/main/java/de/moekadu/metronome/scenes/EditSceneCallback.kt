/*
 * Copyright 2021 Michael Moessner
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

package de.moekadu.metronome.scenes

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import de.moekadu.metronome.MainActivity
import de.moekadu.metronome.R
import de.moekadu.metronome.viewmodels.MetronomeViewModel
import de.moekadu.metronome.viewmodels.ScenesViewModel

class EditSceneCallback(private val activity: MainActivity,
                        private val scenesViewModel: ScenesViewModel,
                        private val metronomeViewModel: MetronomeViewModel
)  : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        activity.menuInflater.inflate(R.menu.edit, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.action_edit_done -> {
                scenesViewModel.editingStableId.value?.let { stableId ->
                    val bpm = metronomeViewModel.bpm.value
                    val noteList = metronomeViewModel.noteList.value
                    val title = metronomeViewModel.editedSceneTitle.value

                    scenesViewModel.scenes.value?.editScene(stableId, title = title, bpm = bpm, noteList = noteList)
                    scenesViewModel.setActiveStableId(stableId)
                    scenesViewModel.setEditingStableId(Scene.NO_STABLE_ID)
                }
                mode?.finish()
                true
            }
            else -> false
        }
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        scenesViewModel.setEditingStableId(Scene.NO_STABLE_ID)
    }
}
