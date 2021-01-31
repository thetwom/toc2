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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MetronomeViewModel(private val playerConnection: PlayerServiceConnection): ViewModel() {

    val speed get() = playerConnection.speed
    val playerStatus get() = playerConnection.playerStatus
    val noteStartedEvent get() = playerConnection.noteStartedEvent
    val noteList get() = playerConnection.noteList

    fun setSpeed(value: Float) {
        playerConnection.setSpeed(value)
    }

    fun play() {
        playerConnection.play()
    }

    fun pause() {
        playerConnection.pause()
    }

    fun syncClickWithUptimeMillis(uptimeMillis : Long) {
        playerConnection.syncClickWithUptimeMillis(uptimeMillis)
    }

    override fun onCleared() {
        playerConnection.onDestroy()
        super.onCleared()
    }

    class Factory(private val playerConnection: PlayerServiceConnection) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return MetronomeViewModel(playerConnection) as T
        }
    }
}