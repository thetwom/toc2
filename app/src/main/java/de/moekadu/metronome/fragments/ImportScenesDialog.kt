package de.moekadu.metronome.fragments

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import de.moekadu.metronome.R
import de.moekadu.metronome.scenes.SceneDatabase

class ImportScenesDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val scenesString = arguments?.getString(SCENES_KEY, "") ?: ""
        val scenes = SceneDatabase.stringToScenes(scenesString).scenes

        val dialog = AlertDialog.Builder(requireContext()).apply {
            setTitle(context.resources.getQuantityString(R.plurals.load_scenes, scenes.size, scenes.size))
            setNegativeButton(R.string.abort) { _, _ -> dismiss() }
            setItems(R.array.load_scenes_list) { _, which ->
                val array = context.resources.getStringArray(R.array.load_scenes_list)

                val task = when (array[which]) {
                    context.getString(R.string.prepend_current_list) -> SceneDatabase.InsertMode.Prepend
                    context.getString(R.string.append_current_list) -> SceneDatabase.InsertMode.Append
                    else -> SceneDatabase.InsertMode.Replace
                }
                val bundle = Bundle(2)
                bundle.putString(INSERT_MODE_KEY, task.toString())
                bundle.putString(SCENES_KEY, scenesString)
                setFragmentResult(REQUEST_KEY, bundle)
                //scenesFragment.loadScenes(scenes, task)
            }
        }.create()
        return dialog
    }

    companion object {
        const val REQUEST_KEY = "ImportScenesDialog: import scenes"
        const val INSERT_MODE_KEY = "insert mode"
        const val SCENES_KEY = "scenes key"

        fun createInstance(scenesString: String): ImportScenesDialog {
            val dialog = ImportScenesDialog()
            val bundle = Bundle(1)
            bundle.putString(SCENES_KEY, scenesString)
            dialog.arguments = bundle
            return dialog
        }
    }
}