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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.preference.PreferenceManager
import de.moekadu.metronome.fragments.MetronomeAndScenesFragment
import de.moekadu.metronome.fragments.SettingsFragment
import de.moekadu.metronome.fragments.ViewPagerAdapter
import de.moekadu.metronome.notification.PlayerNotification
import de.moekadu.metronome.preferences.AppPreferences
import de.moekadu.metronome.services.PlayerServiceConnection
import de.moekadu.metronome.viewmodels.MetronomeViewModel
import de.moekadu.metronome.viewmodels.ScenesViewModel

class MainActivity : AppCompatActivity() {

    // Regular todos
    // TODO: delete log messages?
    // TODO: test different device formats
    // TODO: translations

    // TODO: synchronization with tap-in seems not really perfect?

    // TODO??: Allow "odd" speeds e.g. during load or when typing

    private val metronomeViewModel by viewModels<MetronomeViewModel> {
        val playerConnection = PlayerServiceConnection.getInstance(this,
            AppPreferences.readMetronomeBpm(this),
            AppPreferences.readMetronomeNoteList(this),
            AppPreferences.readIsMute(this)
        )
        MetronomeViewModel.Factory(playerConnection)
    }

    private val scenesViewModel by viewModels<ScenesViewModel> {
        ScenesViewModel.Factory(AppPreferences.readScenesDatabase(this))
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

        if (savedInstanceState == null)
            handleFileLoadingIntent(intent)
        setDisplayHomeButton()
//        Log.v("Metronome", "MainActivity:onCreate: end");
    }

    override fun onStop() {
//        Log.v("Metronome", "MainActivity.onStop");
        AppPreferences.writeMetronomeState(
            metronomeViewModel.bpm.value, metronomeViewModel.noteList.value,
            metronomeViewModel.mute.value, this)
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
                val changed = setMetronomeAndScenesViewPagerId(ViewPagerAdapter.METRONOME)
                if (!changed) {
                    // this will only remove the notification if no foreground task is running
                    PlayerNotification.destroyNotification(this)
                    moveTaskToBack(true)
                }
            }
            else -> {
                supportFragmentManager.popBackStack()
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
//        Log.v("Metronome", "MainActivity.onNewIntent: intent=$intent")
        handleFileLoadingIntent(intent)
    }

    private fun handleFileLoadingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_VIEW) {
//            Log.v("Metronome", "MainActivity.handleFileLoadingIntent: intent=${intent.data}")

            intent.data?.let { uri ->
//                Log.v("Metronome", "MainActivity.handleFileLoadingIntent: uri=$uri")
                supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                scenesViewModel.loadScenesFromFile(uri)
            }
        }
    }

    private fun setMetronomeAndScenesViewPagerId(id: Int): Boolean {
        var changed = false
        when (val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)) {
            is MetronomeAndScenesFragment -> {
                if (currentFragment.viewPager?.currentItem != id) {
                    currentFragment.viewPager?.currentItem = id
                    changed = true
                }
                supportActionBar?.setDisplayHomeAsUpEnabled(id != ViewPagerAdapter.METRONOME)
            }
        }
        return changed
    }

    fun setDisplayHomeButton() {
//        Log.v("Metronome", "MainActivity:setDisplayHomeButton: backStackEntryCount = ${supportFragmentManager.backStackEntryCount}");
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            return
        }

        when (val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)) {
            is MetronomeAndScenesFragment -> {
                val id =  currentFragment.viewPager?.currentItem
//                Log.v("Metronome", "MainActivity:setDisplayHomeButton: viewPager.currentItem = $id");
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
                    addToBackStack(PROPERTIES_FRAGMENT)
                 //   setDisplayHomeButton()
                }
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
            }
        }

        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val PROPERTIES_FRAGMENT = "de.moekadu.metronome.PROPERTIES_FRAGMENT"
//        const val METRONOME_AND_SCENES_FRAGMENT = "de.moekadu.metronome.METRONOME_AND_SCENES_FRAGMENT"
    }
}
