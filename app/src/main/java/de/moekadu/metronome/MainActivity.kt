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
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.TextViewCompat
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
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
    private val saveDataViewModel by viewModels<SaveDataViewModel> {
        SaveDataViewModel.Factory(AppPreferences.readSavedItemsDatabase(this))
    }

    private lateinit var viewPager: ViewPager2

    private lateinit var editItemOverlay: ConstraintLayout
    private lateinit var editItemTitle: AppCompatTextView

    private val saveDataArchiving by lazy {
        SaveDataArchiving(this)
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

        viewPager = findViewById(R.id.viewpager)
        viewPager.adapter = ViewPagerAdapter(this)
        viewPager.currentItem = ViewPagerAdapter.METRONOME
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val showDisplayHomeButton = (position != ViewPagerAdapter.METRONOME)
                supportActionBar?.setDisplayHomeAsUpEnabled(showDisplayHomeButton)
                super.onPageSelected(position)
            }
        })

        editItemOverlay = findViewById(R.id.edit_item)
        editItemTitle = findViewById(R.id.edit_item_text)
        val editItemDoneButton = findViewById<MaterialButton>(R.id.edit_item_done)
        editItemDoneButton.setOnClickListener {
            saveDataViewModel.editingStableId.value?.let { stableId ->
                val bpm = metronomeViewModel.speed.value
                val noteList = metronomeViewModel.noteList.value?.let { n -> noteListToString(n) }
                val title = saveDataViewModel.editedName.value
                // TODO: maybe date and time
                saveDataViewModel.savedItems.value?.editItem(stableId, title = title, bpm = bpm, noteList = noteList)
                // saveCurrentSettings() // double check that this is already saved by savedatafragment
                saveDataViewModel.setActiveStableId(stableId)
                saveDataViewModel.setEditingStableId(SavedItem.NO_STABLE_ID)
                editItemOverlay.visibility = View.GONE
                viewPager.currentItem = ViewPagerAdapter.SAVE_DATA
            }
        }

        val editItemAbortButton = findViewById<MaterialButton>(R.id.edit_item_abort)
        editItemAbortButton.setOnClickListener {
            saveDataViewModel.setEditingStableId(SavedItem.NO_STABLE_ID)
            editItemOverlay.visibility = View.GONE
        }

        val editItemRenameButton = findViewById<MaterialButton>(R.id.edit_item_rename)

        editItemRenameButton.setOnClickListener {
            val editText = EditText(this).apply {
                setHint(R.string.save_name)
                inputType = InputType.TYPE_CLASS_TEXT
            }
            val dialogBuilder = AlertDialog.Builder(this).apply {
                setTitle(R.string.rename_saved_item)
                setView(editText)
                setNegativeButton(R.string.dismiss) { dialog, _ -> dialog.cancel() }
                setPositiveButton(R.string.done) { _, _ ->
                    var newName = editText.text.toString()
                    if (newName.length > 200) {
                        newName = newName.substring(0, 200)
                        Toast.makeText(this@MainActivity, getString(R.string.max_allowed_characters, 200), Toast.LENGTH_SHORT).show()
                    }
                    saveDataViewModel.setEditedName(newName)
                }
            }
            dialogBuilder.show()
        }

        metronomeViewModel.disableViewPageUserInput.observe(this) {
            lockViewPager()
        }

        saveDataViewModel.editedName.observe(this) {
            editItemTitle.text = getString(R.string.editing_item, it)
        }
        saveDataViewModel.editingStableId.observe(this) {
            lockViewPager()
        }
        setDisplayHomeButton()
//        Log.v("Metronome", "MainActivity:onCreate: end");
    }

    override fun onStop() {
        AppPreferences.writeMetronomeState(
                metronomeViewModel.speed.value, metronomeViewModel.noteList.value, this)
        super.onStop()
    }
    override fun onSupportNavigateUp() : Boolean{
        supportFragmentManager.popBackStack()
        return true
    }

    override fun onBackPressed() {
        Log.v("Metronome", "MainActivity.onBackPressed()")
        viewPager.currentItem = ViewPagerAdapter.METRONOME
    }

    private fun setDisplayHomeButton() {
        //val showDisplayHomeButton = supportFragmentManager.backStackEntryCount > 0
        val showDisplayHomeButton = (viewPager.currentItem != ViewPagerAdapter.METRONOME)
        supportActionBar?.setDisplayHomeAsUpEnabled(showDisplayHomeButton)
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
                viewPager.currentItem = ViewPagerAdapter.SETTINGS
            }
            R.id.action_load -> {
            viewPager.currentItem = ViewPagerAdapter.SAVE_DATA
            }
            R.id.action_save -> {
                saveCurrentSettings()
            }
            R.id.action_archive -> {
                saveDataArchiving.sendArchivingIntent(saveDataViewModel.savedItems.value)
            }
            R.id.action_unarchive -> {
                saveDataArchiving.sendUnarchivingIntent()
            }
            R.id.action_clear_all -> {
                clearAllSavedItems()
            }
            R.id.action_edit -> {
                val stableId = saveDataViewModel.activeStableId.value
                if (stableId != null && stableId != SavedItem.NO_STABLE_ID) {
                    saveDataViewModel.savedItems.value?.getItem(stableId)?.title?.let { title ->
                        saveDataViewModel.setEditedName(title)
                    }
                    saveDataViewModel.setEditingStableId(stableId)
                    editItemOverlay.visibility = View.VISIBLE
                    viewPager.currentItem = ViewPagerAdapter.METRONOME
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
            saveDataArchiving.archiveSavedItems(data?.data,
                    saveDataViewModel.savedItems.value?.getSaveDataString())
        }
        else if (requestCode == FILE_OPEN) {
            saveDataArchiving.unarchiveSaveItems(data?.data) { databaseString, task ->
                saveDataViewModel.savedItems.value?.loadDataFromString(databaseString, task)
                AppPreferences.writeSavedItemsDatabase(saveDataViewModel.savedItemsAsString, this)
            }
        }
    }

    private fun clearAllSavedItems() {
        val builder = AlertDialog.Builder(this).apply {
            setTitle(R.string.clear_all_question)
            setNegativeButton(R.string.no) { dialog, _ -> dialog.dismiss() }
            setPositiveButton(R.string.yes) { _, _ ->
                saveDataViewModel.savedItems.value?.clear()
                AppPreferences.writeSavedItemsDatabase(saveDataViewModel.savedItemsAsString, this@MainActivity)
            }
        }
        builder.show()
    }

    private fun saveCurrentSettings() {
        SaveDataDialog.save(this, metronomeViewModel.speed.value, metronomeViewModel.noteList.value) { item ->
            saveDataViewModel.savedItems.value?.add(item)
            AppPreferences.writeSavedItemsDatabase(saveDataViewModel.savedItemsAsString, this)
            true
        }
    }

    private fun lockViewPager() {
        var lock: Boolean = false
        if (metronomeViewModel.disableViewPageUserInput.value == true)
            lock = true
        saveDataViewModel.editingStableId.value?.let {
            if (it != SavedItem.NO_STABLE_ID)
                lock = true
        }
        viewPager.isUserInputEnabled = !lock
    }

    companion object {
        const val FILE_CREATE = 1
        const val FILE_OPEN = 2
    }
}
