/*
 * Copyright 2022 Michael Moessner
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

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.parcelize.Parcelize
import java.io.File

class ScenesSharingDialogFragment() : DialogFragment() {
    private var adapter: ScenesSharingDialogAdapter? = null
    private var scenesString: String? = null

    @Parcelize
    private class SavedState(val checkedList: List<Boolean>?, val scenesString: String?) : Parcelable

    constructor(scenes: List<Scene>) : this() {
        adapter = ScenesSharingDialogAdapter(scenes)
        scenesString = SceneDatabase.scenesToString(scenes)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val state = SavedState(adapter?.getStateOfEachScene(), scenesString)
        outState.putParcelable("scenes sharing dialog fragment state", state)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        savedInstanceState?.let {
            it.getParcelable<SavedState>("scenes sharing dialog fragment state")?.let { storedState ->
                if (storedState.checkedList != null && storedState.scenesString != null) {
                    scenesString = storedState.scenesString
                    val scenes = SceneDatabase.stringToScenes(storedState.scenesString).scenes
                    adapter = ScenesSharingDialogAdapter(scenes)
                    adapter?.setStateOfEachScene(storedState.checkedList)
                }
            }
        }

        return AlertDialog.Builder(requireContext()).apply {
            // root must be null here since alert dialog does not provide a root view
            val v = layoutInflater.inflate(R.layout.select_scenes_dialog, null)
            val r = v.findViewById<RecyclerView>(R.id.scenes_list)
            r?.layoutManager = LinearLayoutManager(v.context)
            r?.adapter = adapter
            setTitle(R.string.select_scenes)
            setView(v)
            setPositiveButton(R.string.share2) { dialog, which ->
                //val numScenes = viewModel.scenes.value?.size ?: 0
                val scenesForSharing = adapter?.getScenesToBeShared()
                //val numScenes = viewModel.scenes.value?.size ?: 0
                val numScenes = scenesForSharing?.size ?: 0
                if (numScenes == 0 || scenesForSharing == null) {
                    Toast.makeText(requireContext(), R.string.no_scenes_selected, Toast.LENGTH_LONG).show()
                } else {
                    val content = SceneDatabase.scenesToString(scenesForSharing)

                    val sharePath = File(requireContext().cacheDir, "share").also { it.mkdir() }
                    val sharedFile = File(sharePath.path, "metronome.txt")
                    sharedFile.writeBytes(content.toByteArray())

                    val uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName, sharedFile)

                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_EMAIL, "")
                        putExtra(Intent.EXTRA_CC, "")
                        putExtra(Intent.EXTRA_TITLE, resources.getQuantityString(R.plurals.sharing_num_scenes, numScenes, numScenes))
                        type = "text/plain"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                }
            }
            setNegativeButton(R.string.abort) { dialog, which -> }
        }.create()
        //return super.onCreateDialog(savedInstanceState)
    }
}