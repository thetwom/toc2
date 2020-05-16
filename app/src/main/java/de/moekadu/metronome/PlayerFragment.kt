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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.moekadu.metronome.AudioMixer.PlayListItem


/**
 * The fragment which stays alive even if the activity restarts
 */
class PlayerFragment : Fragment() {

    var playerService: PlayerService? = null
        private set
    private var playerContext: Context? = null

    private var playerConnection: ServiceConnection? = null

    private var speed = InitialValues.speed
    private var playList: Array<PlayListItem> = Array(1) {PlayListItem(Sounds.defaultSound(), 1.0f, -1f, null)}

    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        // Log.v("Metronome", "PlayerFragment:onCreate")

        retainInstance = true

        val context = activity

        if(context != null) {
            // Log.v("Metronome", "PlayerFragment:onCreate : loading preferences");
            val preferences = context.getPreferences(Context.MODE_PRIVATE)
            speed = preferences.getFloat("speed", InitialValues.speed)
            val soundString = preferences.getString("sound", Sounds.defaultSound().toString()) ?: Sounds.defaultSound().toString()
            playList = SoundProperties.parseMetaDataString(soundString)
            if(playList.isEmpty()){
                playList = Array(1) {PlayListItem(Sounds.defaultSound(), 1.0f, -1f, null)}
            }

            bindService(context.applicationContext)
        }
    }

    private fun bindService(context : Context) {

        if(playerService == null) {
            playerConnection = object : ServiceConnection {

                override fun onServiceConnected(className : ComponentName, service : IBinder) {
                    // Log.v("Metronome", "PlayerService:onServiceConnected")

                    // We've bound to LocalService, cast the IBinder and get LocalService instance
                    val binder = service as PlayerService.PlayerBinder
                    playerService = binder.service
                    playerContext = context
                    playerService?.changeSpeed(speed)
                    playerService?.setSounds(playList)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // Log.v("Metronome", "PlayerService:onServiceDisconnected")
                    playerService = null
                    playerContext = null
                }
            }

            val serviceIntent = Intent(context, PlayerService::class.java)
            playerConnection?.let {
                context.bindService(serviceIntent, it, Context.BIND_AUTO_CREATE)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return null
    }

    override fun onStop() {
//        Log.v("Metronome", "PlayerFragment:onStop()")
        val context = activity

        if(context != null)
        {
            // Log.v("Metronome", "PlayerFragment:onStop : saving preferences");
            val preferences = context.getPreferences(Context.MODE_PRIVATE)
            val editor = preferences.edit()
            //editor.putInt("speed", speed);
            speed = playerService?.playbackState?.playbackSpeed ?: InitialValues.speed
            playerService?.sound?.let { playList = it }

            editor.putFloat("speed", speed)
            val metaDataString = SoundProperties.createMetaDataString(playList)
            // Log.v("Metronome", "PlayerFragment:onStop : saving meta data: " + metaDataString)
            editor.putString("sound", metaDataString)
            editor.apply()
        }

        super.onStop()
    }

    override fun onDestroy() {
        // Log.v("Metronome", "PlayerFragment:onDestroy")
        if(playerService != null) {
            playerConnection?.let {
                playerContext?.unbindService(it)
                playerService = null
            }
        }

        super.onDestroy()
    }
}