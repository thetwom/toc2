/*
 * Copyright 2020 Michael Moessner
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
import android.os.DeadObjectException
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.*

class PlayerServiceConnection(
        context: Context, private val initialSpeed: Float, private val initialNoteList: NoteList) {

    private val _noteList = MutableLiveData<NoteList>()
    val noteList: LiveData<NoteList> get() = _noteList

    private val _speed = MutableLiveData<Float>()
    val speed: LiveData<Float> get() = _speed

    private val _playerStatus = MutableLiveData(PlayerStatus.Paused)
    val playerStatus: LiveData<PlayerStatus> get() = _playerStatus

    val noteStartedEvent = LifecycleAwareEvent<NoteListItem>()

    private val applicationContext = context.applicationContext
    private var serviceBinder: PlayerService.PlayerBinder? = null

    private val serviceStateListener = object : PlayerService.StatusChangedListener {
        override fun onPlay() {
            if (_playerStatus.value != PlayerStatus.Playing)
                _playerStatus.value = PlayerStatus.Playing
        }

        override fun onPause() {
            if (_playerStatus.value != PlayerStatus.Paused)
                _playerStatus.value = PlayerStatus.Paused
        }

        override fun onNoteStarted(noteListItem: NoteListItem) {
            noteStartedEvent.triggerEvent(noteListItem)
        }

        override fun onSpeedChanged(speed: Float) {
            if (_speed.value != speed)
                _speed.value = speed
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.v("Metronome", "ServiceConnection.onServiceConnected")
            serviceBinder = binder as PlayerService.PlayerBinder?

            try {
                serviceBinder?.service?.let { service ->
                    service.registerStatusChangedListener(serviceStateListener)

                    // TODO: make service to use PlayerStatus and then enable this again
                    //if (_playerStatus.value != service.playbackState)
                    //    _playbackState.value = service.playbackState
                    setSpeed(initialSpeed)
                    service.noteList.set(initialNoteList)

                    if (_speed.value != service.speed)
                        _speed.value = service.speed
                    if (_noteList.value != service.noteList)
                        _noteList.value = service.noteList
                }
            }
            catch (_: DeadObjectException) {
                serviceBinder = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.v("Metronome", "ServiceConnection.onServiceDisconnected")
            serviceBinder = null
        }
    }

    init {
        bindToService()
    }

    fun onDestroy() {
        unbindFromService()
    }

    fun play() {
        try {
            serviceBinder?.service?.startPlay()
        }
        catch (_: DeadObjectException) {
            serviceBinder = null
        }
    }

    fun pause() {
        try {
            serviceBinder?.service?.stopPlay()
        }
        catch (_: DeadObjectException) {
            serviceBinder = null
        }
    }

    fun setSpeed(speed: Float) {
        try {
            serviceBinder?.service?.speed = speed
        }
        catch (_: DeadObjectException) {
            serviceBinder = null
        }
    }

    fun syncClickWithUptimeMillis(uptimeMillis : Long) {
        try {
            serviceBinder?.service?.syncClickWithUptimeMillis(uptimeMillis)
        }
        catch (_: DeadObjectException) {
            serviceBinder = null
        }
    }

    private fun bindToService() {
        Log.v("Metronome", "ServiceConnection.bindToService")
        val serviceIntent = Intent(applicationContext, PlayerService::class.java)
        val success = applicationContext.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        if (!success) {
            throw RuntimeException("ServiceConnection.bindToService: Can't start bind to service")
        }
        // the registering is either done in onServiceConnected or directly done here if it is already available
        // doing it also here is necessary since onServiceConnected is only called on the first bind.
        try {
            serviceBinder?.service?.registerStatusChangedListener(serviceStateListener)
        }
        catch (_: DeadObjectException){
            serviceBinder = null
        }
    }

    private fun unbindFromService() {
        Log.v("Metronome", "ServiceConnection.unbindFromService")
        applicationContext.unbindService(connection)
        try {
            serviceBinder?.service?.unregisterStatusChangedListener(serviceStateListener)
        }
        catch (_: DeadObjectException) {
            serviceBinder = null
        }
    }

    companion object {
        @Volatile
        private var instance: PlayerServiceConnection? = null

        fun getInstance(context: Context, initialSpeed: Float, initialNoteList: NoteList) =
                instance ?: synchronized(this) {
                instance ?: PlayerServiceConnection(context, initialSpeed, initialNoteList)
                        .also { instance = it }
            }
    }
}
