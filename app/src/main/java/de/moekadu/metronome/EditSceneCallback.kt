package de.moekadu.metronome

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.viewpager2.widget.ViewPager2

class EditSceneCallback(private val activity: MainActivity,
                        private val scenesViewModel: ScenesViewModel,
                        private val metronomeViewModel: MetronomeViewModel,
                        private val viewPager: ViewPager2?)  : ActionMode.Callback {
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
                    val noteList = metronomeViewModel.noteList.value?.let { n -> noteListToString(n) }
                    val title = metronomeViewModel.scene.value

                    scenesViewModel.scenes.value?.editScene(stableId, title = title, bpm = bpm, noteList = noteList)
                    // saveCurrentSettings() // double check that this is already saved by scenefragment
                    scenesViewModel.setActiveStableId(stableId)
                    scenesViewModel.setEditingStableId(Scene.NO_STABLE_ID)
                    viewPager?.currentItem = ViewPagerAdapter.SCENES
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
