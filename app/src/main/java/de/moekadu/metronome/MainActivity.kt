/*
 * Copyright 2019 Michael Moessner
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

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.preference.PreferenceManager
import java.util.*

class MainActivity : AppCompatActivity() {

    // TODO: handle incorrect loads which could occur, when loading with newer version
    // TODO: nicer saved-item layout
    // TODO: delete log messages?

    // TODO: test different device formats
    // TODO: translations

    // TODO: volume control shouldn't animate if volume changes only one step

    private val metronomeViewModel by viewModels<MetronomeViewModel> {
        val playerConnection = PlayerServiceConnection.getInstance(this,
                AppPreferences.readMetronomeSpeed(this),
                AppPreferences.readMetronomeNoteList(this)
        )
        MetronomeViewModel.Factory(playerConnection)
    }
    private val scenesViewModel by viewModels<ScenesViewModel> {
        ScenesViewModel.Factory(AppPreferences.readScenesDatabase(this))
    }

    private val sceneArchiving by lazy {
        SceneArchiving(this)
    }

    private val editSceneCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menuInflater.inflate(R.menu.edit, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_edit_done -> {
                    scenesViewModel.editingStableId.value?.let { stableId ->
                        val bpm = metronomeViewModel.speed.value
                        val noteList = metronomeViewModel.noteList.value?.let { n -> noteListToString(n) }
                        val title = metronomeViewModel.scene.value
                        // TODO: maybe date and time
                        scenesViewModel.scenes.value?.editScene(stableId, title = title, bpm = bpm, noteList = noteList)
                        // saveCurrentSettings() // double check that this is already saved by scenefragment
                        scenesViewModel.setActiveStableId(stableId)
                        scenesViewModel.setEditingStableId(Scene.NO_STABLE_ID)
                        setMetronomeAndScenesViewPagerId(ViewPagerAdapter.SCENES)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        Log.v("Metronome", "MainActivity:onCreate")

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val appearance = sharedPreferences.getString("appearance", "auto")
        var nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

        if(appearance.equals("dark")){
            nightMode = AppCompatDelegate.MODE_NIGHT_YES
        }
        else if(appearance.equals("light")){
            nightMode = AppCompatDelegate.MODE_NIGHT_NO
        }

        AppCompatDelegate.setDefaultNightMode(nightMode)

        val screenOn = sharedPreferences.getBoolean("screenon", false)
        if(screenOn)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        volumeControlStream = AudioManager.STREAM_MUSIC

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace<MetronomeAndScenesFragment>(R.id.fragment_container)
            }
        }

        setDisplayHomeButton()
//        Log.v("Metronome", "MainActivity:onCreate: end");
    }

    override fun onStop() {
        AppPreferences.writeMetronomeState(
                metronomeViewModel.speed.value, metronomeViewModel.noteList.value, this)
        super.onStop()
    }
//    override fun onSupportNavigateUp() : Boolean{
//        supportFragmentManager.popBackStack()
//        return true
//    }

    override fun onBackPressed() {
//        Log.v("Metronome", "MainActivity.onBackPressed():  backStackEntryCount = ${supportFragmentManager.backStackEntryCount}")
        when (supportFragmentManager.findFragmentById(R.id.fragment_container)) {
            is MetronomeAndScenesFragment -> {
                setMetronomeAndScenesViewPagerId(ViewPagerAdapter.METRONOME)
                return
            }
        }

        supportFragmentManager.popBackStack()
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    fun setMetronomeAndScenesViewPagerId(id: Int) {
        when (val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)) {
            is MetronomeAndScenesFragment -> {
                currentFragment.viewPager?.currentItem = id
                supportActionBar?.setDisplayHomeAsUpEnabled(id != ViewPagerAdapter.METRONOME)
            }
        }
    }

    fun setDisplayHomeButton() {
        //Log.v("Metronome", "MainActivity:setDisplayHomeButton: viewPager.currentItem = ${viewPager.currentItem}");
//        Log.v("Metronome", "MainActivity:setDisplayHomeButton: backStackEntryCount = ${supportFragmentManager.backStackEntryCount}");
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            return
        }

        when (val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)) {
            is MetronomeAndScenesFragment -> {
                val id =  currentFragment.viewPager?.currentItem
                if (id != ViewPagerAdapter.METRONOME && id != null) {
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    return
                }
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onCreateOptionsMenu(menu : Menu) : Boolean{
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item : MenuItem) : Boolean{

        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
            }
            R.id.action_properties -> {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace<SettingsFragment>(R.id.fragment_container)
                    addToBackStack("main")
                 //   setDisplayHomeButton()
                }
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
            }
            R.id.action_load -> {
                setMetronomeAndScenesViewPagerId(ViewPagerAdapter.SCENES)
            }
            R.id.action_save -> {
                saveCurrentSettings()
            }
            R.id.action_archive -> {
                if (scenesViewModel.scenes.value?.size ?: 0 == 0) {
                    Toast.makeText(this, R.string.database_empty, Toast.LENGTH_LONG).show()
                } else {
                    sceneArchiving.sendArchivingIntent(scenesViewModel.scenes.value)
                }
            }
            R.id.action_unarchive -> {
                sceneArchiving.sendUnarchivingIntent()
            }
            R.id.action_clear_all -> {
                clearAllSavedItems()
            }
            R.id.action_edit -> {
                val actionMode = startSupportActionMode(editSceneCallback)
                actionMode?.title = getString(R.string.editing_scene)
                val stableId = scenesViewModel.activeStableId.value
//                Log.v("Metronome", "MainActivity: onOptionsItemSelected: R.id.action_edit, stableId = $stableId")
                if (stableId != null && stableId != Scene.NO_STABLE_ID) {
                    scenesViewModel.setEditingStableId(stableId)
                    setMetronomeAndScenesViewPagerId(ViewPagerAdapter.METRONOME)
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK)
            return

        if (requestCode == FILE_CREATE) {
            sceneArchiving.archiveScenes(data?.data,
                    scenesViewModel.scenes.value?.getScenesString())
        }
        else if (requestCode == FILE_OPEN) {
            sceneArchiving.unarchiveScenes(data?.data) { databaseString, task ->
                when (scenesViewModel.scenes.value?.loadSceneFromString(databaseString, task)) {
                    SceneDatabase.FileCheck.Empty ->
                        Toast.makeText(this, R.string.file_empty, Toast.LENGTH_LONG).show()
                    SceneDatabase.FileCheck.Invalid ->
                        Toast.makeText(this, R.string.file_invalid, Toast.LENGTH_LONG).show()
                    SceneDatabase.FileCheck.Ok ->
                        AppPreferences.writeScenesDatabase(scenesViewModel.scenesAsString, this)
                }
            }
        }
    }

    private fun clearAllSavedItems() {
        val builder = AlertDialog.Builder(this).apply {
            setTitle(R.string.clear_all_question)
            setNegativeButton(R.string.no) { dialog, _ -> dialog.dismiss() }
            setPositiveButton(R.string.yes) { _, _ ->
                scenesViewModel.scenes.value?.clear()
                AppPreferences.writeScenesDatabase(scenesViewModel.scenesAsString, this@MainActivity)
            }
        }
        builder.show()
    }

    private fun saveCurrentSettings() {
        SaveSceneDialog.save(this, metronomeViewModel.speed.value, metronomeViewModel.noteList.value) { item ->
            scenesViewModel.scenes.value?.add(item)
            AppPreferences.writeScenesDatabase(scenesViewModel.scenesAsString, this)
            true
        }
    }

    companion object {
        const val FILE_CREATE = 1
        const val FILE_OPEN = 2
    }
}
