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
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import java.util.*

class MainActivity : AppCompatActivity() {

    // TODO: handle incorrect loads which could occur, when loading with newer version
    // TODO: nicer saved-item layout
    // TODO: delete log messages?

    // TODO: test different device formats
    // TODO: translations
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

    private var settingsFragment : SettingsFragment? = null
    private var saveDataFragment : SaveDataFragment? = null

    private val speedLimiter by lazy {
        SpeedLimiter(PreferenceManager.getDefaultSharedPreferences(this), this)
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

        var metronomeFragment = supportFragmentManager.findFragmentByTag(METRONOME_FRAGMENT_TAG) as MetronomeFragment?
        if(metronomeFragment == null) {
            metronomeFragment = MetronomeFragment()
        }

        settingsFragment = supportFragmentManager.findFragmentByTag(SETTINGS_FRAGMENT_TAG) as SettingsFragment?
        if(settingsFragment == null) {
            settingsFragment = SettingsFragment()
        }

        saveDataFragment = supportFragmentManager.findFragmentByTag(SAVE_DATA_FRAGMENT_TAG) as SaveDataFragment?
        if(saveDataFragment == null) {
            saveDataFragment = SaveDataFragment()
        }
        saveDataFragment?.onItemClickedListener = object : SavedItemAdapter.OnItemClickedListener {
            override fun onItemClicked(item: SavedItem, position: Int) {
                loadSettings(item)
                supportFragmentManager.popBackStack()
            }
        }

        if(supportFragmentManager.fragments.size == 0)
            supportFragmentManager.beginTransaction().replace(R.id.mainframe, metronomeFragment, METRONOME_FRAGMENT_TAG).commit()

        setDisplayHomeButton()
        supportFragmentManager.addOnBackStackChangedListener { setDisplayHomeButton() }
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

    private fun setDisplayHomeButton() {
        val showDisplayHomeButton = supportFragmentManager.backStackEntryCount > 0
        supportActionBar?.setDisplayHomeAsUpEnabled(showDisplayHomeButton)
    }

    override fun onCreateOptionsMenu(menu : Menu) : Boolean{
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item : MenuItem) : Boolean{

        when (item.itemId) {
            R.id.action_properties -> {
                settingsFragment?.let {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.mainframe, it, SETTINGS_FRAGMENT_TAG)
                            .addToBackStack("blub")
                            .commit()
                }
            }
            R.id.action_load -> {
                saveDataFragment?.let {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.mainframe, it, SAVE_DATA_FRAGMENT_TAG)
                            .addToBackStack("blub")
                            .commit()
                }
            }
            R.id.action_save -> {
//                Log.v("Metronome", "MainActivity.onOptionsItemSelected: action_save")
                saveCurrentSettings()
            }
            R.id.action_archive -> {
                //if (saveDataFragment?.databaseSize ?: 0 == 0) {
                if (saveDataViewModel.savedItems.value?.size ?: 0 == 0) {
                    Toast.makeText(this, R.string.database_empty, Toast.LENGTH_LONG).show()
                }
                else {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TITLE, "metronome.txt")
                        // default path
                        // putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
                    }
                    startActivityForResult(intent, FILE_CREATE)
                }
            }
            R.id.action_unarchive -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    // default path
                    // putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
                }
                startActivityForResult(intent, FILE_OPEN)
            }
            R.id.action_clear_all -> {
                clearAllSavedItems()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK)
            return

        if (requestCode == FILE_CREATE)
            archiveSavedItems(data?.data)
        else if (requestCode == FILE_OPEN)
            unarchiveSavedItems(data?.data)
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

    private fun archiveSavedItems(uri: Uri?) {
        if (uri == null)
            return

        val databaseString = saveDataViewModel.savedItems.value?.getSaveDataString() ?: ""
        // Log.v("Metronome", "MainActivity.archiveSavedItems: databaseString = $databaseString")
        contentResolver?.openOutputStream(uri)?.use { stream ->
            stream.write(databaseString.toByteArray())
        }
    }

    private fun unarchiveSavedItems(uri: Uri?) {
        if (uri == null)
            return
        val builder = AlertDialog.Builder(this).apply {
            setTitle(R.string.load_saved_items)
            setNegativeButton(R.string.abort) {dialog,_  -> dialog.dismiss()}
            setItems(R.array.load_saved_items_list) {_, which ->
                val array = resources.getStringArray(R.array.load_saved_items_list)
                val task = when(array[which]) {
                    getString(R.string.prepend_current_list) -> SavedItemDatabase.PREPEND
                    getString(R.string.append_current_list) -> SavedItemDatabase.APPEND
                    else -> SavedItemDatabase.REPLACE
                }

                contentResolver?.openInputStream(uri)?.use { stream ->
                    stream.reader().use {
                        val databaseString = it.readText()
                        //saveDataFragment?.loadFromDatabaseString(databaseString, task)
                        saveDataViewModel.savedItems.value?.loadDataFromString(databaseString, task)
                        AppPreferences.writeSavedItemsDatabase(saveDataViewModel.savedItemsAsString, this@MainActivity)
                    }
                }
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

    private fun loadSettings(item : SavedItem) {
        speedLimiter.checkSavedItemSpeedAndAlert(item.bpm, this)
        metronomeViewModel.setSpeed(speedLimiter.limit(item.bpm))
        metronomeViewModel.noteList.value?.fromString(item.noteList)
        Toast.makeText(this, getString(R.string.loaded_message, item.title), Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val METRONOME_FRAGMENT_TAG = "metronomeFragment"
        private const val SETTINGS_FRAGMENT_TAG = "settingsFragment"
        private const val SAVE_DATA_FRAGMENT_TAG = "saveDataFragment"
        private const val FILE_CREATE = 1
        private const val FILE_OPEN = 2
    }
}
