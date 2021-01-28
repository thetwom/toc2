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