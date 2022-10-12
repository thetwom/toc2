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

package de.moekadu.metronome.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.moekadu.metronome.metronomeproperties.Bpm
import de.moekadu.metronome.metronomeproperties.NoteDuration
import de.moekadu.metronome.metronomeproperties.NoteListItem
import de.moekadu.metronome.metronomeproperties.UId
import de.moekadu.metronome.misc.InitialValues
import de.moekadu.metronome.services.PlayerServiceConnection
import kotlin.math.min

class MetronomeViewModel(private val playerConnection: PlayerServiceConnection): ViewModel() {

    val bpm get() = playerConnection.bpm
    val playerStatus get() = playerConnection.playerStatus
    val noteStartedEvent get() = playerConnection.noteStartedEvent
    val noteList get() = playerConnection.noteList

    val mute: LiveData<Boolean> get() = playerConnection.mute

    private val _editedSceneTitle = MutableLiveData<String?>(null)
    val editedSceneTitle: LiveData<String?> get() = _editedSceneTitle

    private val _isParentViewPagerSwiping = MutableLiveData(false)
    val isParentViewPagerSwiping: LiveData<Boolean>
        get() = _isParentViewPagerSwiping

    var isVisible = true
        set(value) {
            _isVisibleLiveData.value = value
            field = value
        }
    private val _isVisibleLiveData = MutableLiveData(isVisible)
    val isVisibleLiveData: LiveData<Boolean> get() = _isVisibleLiveData

    fun setParentViewPagerSwiping(isSwiping: Boolean) {
        _isParentViewPagerSwiping.value = isSwiping
    }

    fun setBpm(bpm: Float) {
        val oldBpm = this.bpm.value
        val newBpm = oldBpm?.copy(bpm = bpm) ?: Bpm(bpm, NoteDuration.Quarter)
        setBpm(newBpm)
    }

    fun setBpm(duration: NoteDuration) {
        val oldBpm = this.bpm.value
        val newBpm = oldBpm?.copy(noteDuration =  duration) ?: Bpm(InitialValues.bpm.bpm, duration)
        setBpm(newBpm)
    }

    fun setBpm(bpm: Bpm) {
//        Log.v("Metronome", "MetronomeViewModel: setBpm=$bpm")
        playerConnection.setBpm(bpm)
    }

    fun setMute(value: Boolean) {
        playerConnection.isMute = value
    }

    fun setNoteList(noteList: ArrayList<NoteListItem>) {
        playerConnection.setNoteList(noteList)
    }

    fun setNoteListVolume(index: Int, volume: Float) {
        playerConnection.modifyNoteList { noteList ->
            if (index in 0 until noteList.size) {
                noteList[index].volume = volume
                true
            } else {
                false
            }
        }
    }

    fun setNoteListVolume(uid: UId, volume: Float) {
        playerConnection.modifyNoteList { noteList ->
            var success = false
            noteList.filter { uid == it.uid }.forEach {
                it.volume = volume
                success = true
            }
            success
        }
    }

    fun setNoteListId(uid: UId, id: Int) {
        playerConnection.modifyNoteList { noteList ->
            var success = false
            noteList.filter { uid == it.uid }.forEach {
                it.id = id
                success = true
            }
            success
        }
    }

    fun setNoteListDuration(uid: UId, duration: NoteDuration) {
        playerConnection.modifyNoteList { noteList ->
            var success = false
            noteList.filter { uid == it.uid }.forEach {
                it.duration = duration
                success = true
            }
            success
        }
    }

    fun addNote(noteListItem: NoteListItem, index: Int? = null) {
        playerConnection.modifyNoteList { noteList ->
            val i = if (index == null) noteList.size else min(index, noteList.size)
            noteList.add(i, noteListItem)
            true
        }
    }

    fun removeNote(uid: UId) {
        playerConnection.modifyNoteList { noteList ->
            noteList.removeAll { it.uid == uid }
        }
    }

    fun moveNote(uid: UId, toIndex: Int) {
        playerConnection.modifyNoteList { noteList ->
            val toIndexCorrected = min(toIndex, noteList.size - 1)
            val fromIndex = noteList.indexOfFirst { it.uid == uid }
            when {
                toIndexCorrected == fromIndex -> {
                    false
                }
                fromIndex == -1 -> {
                    false
                }
                else -> {
                    val note = noteList.removeAt(fromIndex)
                    noteList.add(toIndexCorrected, note)
                    true
                }
            }
        }
    }

    fun setEditedSceneTitle(name: String?) {
        _editedSceneTitle.value = name
    }

    fun play() {
        Log.v("Metronome", "MetronomeViewModel.play() : starting play on viewmodel $this")
        playerConnection.play()
    }

    fun pause() {
        Log.v("Metronome", "MetronomeViewModel.play() : pausing play on viewmodel $this")
        playerConnection.pause()
    }

    fun syncClickWithUptimeMillis(uptimeMillis: Long) {
        playerConnection.syncClickWithUptimeMillis(uptimeMillis)
    }

    fun setNextNoteIndex(index: Int) {
        playerConnection.setNextNoteIndex(index)
    }

    fun restartPlayingNoteList() {
        playerConnection.restartPlayingNoteList()
    }

    override fun onCleared() {
//        Log.v("Metronome", "MetronomeViewModel.onCleared")
        PlayerServiceConnection.onDestroy()
        super.onCleared()
    }

    class Factory(private val playerConnection: PlayerServiceConnection) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
//            Log.v("Metronome", "MetronomeViewModel.factory.create")
            return MetronomeViewModel(playerConnection) as T
        }
    }
}