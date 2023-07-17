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

/** View model of metronome.
 * @param playerConnection Connection to the service, which does the playing.
 */
class MetronomeViewModel(private val playerConnection: PlayerServiceConnection): ViewModel() {

    /** Current speed of metronome. */
    val bpm get() = playerConnection.bpm
    /** Current player status of metronome (playing/paused). */
    val playerStatus get() = playerConnection.playerStatus
    /** Event which is triggered each time a note is started. */
    val noteStartedEvent get() = playerConnection.noteStartedEvent
    /** Info of which note is currently played or null if no note is played. */
    val currentlyPlayingNote get() = playerConnection.currentlyPlayingNote
    /** Currently used note list. */
    val noteList get() = playerConnection.noteList
    /** Defines if metronome is muted or not. */
    val mute: LiveData<Boolean> get() = playerConnection.mute
    /** Title of currently used scene or null if no scene is used. */
    private val _editedSceneTitle = MutableLiveData<String?>(null)
    val editedSceneTitle: LiveData<String?> get() = _editedSceneTitle

    /** Info if we are currently in the swiping process between metronome and scenes fragment. */
    private val _isParentViewPagerSwiping = MutableLiveData(false)
    val isParentViewPagerSwiping: LiveData<Boolean>
        get() = _isParentViewPagerSwiping

    /** Defines is the metronome fragment is currenlty visible to the user. */
    var isVisible = true
        set(value) {
            _isVisibleLiveData.value = value
            field = value
        }
    private val _isVisibleLiveData = MutableLiveData(isVisible)
    val isVisibleLiveData: LiveData<Boolean> get() = _isVisibleLiveData

    /** Define that we are are (or not) currently swiping between scenes and metronome fragment.
     * @param isSwiping True, if we are swiping, else false.
     */
    fun setParentViewPagerSwiping(isSwiping: Boolean) {
        _isParentViewPagerSwiping.value = isSwiping
    }

    /** Change duration of one beat.
     * @param bpm Value how many beats per minute should be played.
     */
    fun setBpm(bpm: Float) {
        val oldBpm = this.bpm.value
        val newBpm = oldBpm?.copy(bpm = bpm) ?: Bpm(bpm, NoteDuration.Quarter)
        setBpm(newBpm)
    }

    /** Define which note duration is one beat (a quarter, an eights, ...)
     * @param duration Note duration of a beat.
     */
    fun setBpm(duration: NoteDuration) {
        val oldBpm = this.bpm.value
        val newBpm = oldBpm?.copy(noteDuration =  duration) ?: Bpm(InitialValues.bpm.bpm, duration)
        setBpm(newBpm)
    }

    /** Change metronome speed.
     * @param bpm New speed including the note duration of a beat.
     */
    fun setBpm(bpm: Bpm) {
//        Log.v("Metronome", "MetronomeViewModel: setBpm=$bpm")
        playerConnection.setBpm(bpm)
    }

    /** (Un)mute the metronome.
     * @param isMute Boolean if metronome is muted or not.
     */
    fun setMute(isMute: Boolean) {
        playerConnection.isMute = isMute
    }

    /** Change note list to be played.
     * @param noteList New note list to be played.
     */
    fun setNoteList(noteList: ArrayList<NoteListItem>) {
        playerConnection.setNoteList(noteList)
    }

    /** Change volume of a note by providing the note list index.
     * @param index Note index in note list.
     * @param volume New volume (0 -- 1)
     */
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

    /** Change volume of a note by providing the note uid.
     * @param uid Uid of note.
     * @param volume New volume (0 -- 1)
     */
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

    /** Change type of note.
     * @param uid Uid of note, which should be changed.
     * @param id New note type id for the given note.
     */
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

    /** Change note duration of a note (quarter, eights, ...).
     * @param uid Uid of note, which should be changed.
     * @param duration Note duration.
     */
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

    /** Add a note to the note list.
     * @param noteListItem New note.
     * @param index Position where to insert the note or null to insert at the end.
     */
    fun addNote(noteListItem: NoteListItem, index: Int? = null) {
        playerConnection.modifyNoteList { noteList ->
            val i = if (index == null) noteList.size else min(index, noteList.size)
            noteList.add(i, noteListItem)
            true
        }
    }

    /** Remove a note from note list.
     * @param uid Uid of note to be removed.
     */
    fun removeNote(uid: UId) {
        playerConnection.modifyNoteList { noteList ->
            noteList.removeAll { it.uid == uid }
        }
    }

    /** Move a note within note list.
     * @param uid Uid of note to be moved.
     * @param toIndex New note position.
     */
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

    /** Set the title of the scene which is currently edited. */
    fun setEditedSceneTitle(name: String?) {
        _editedSceneTitle.value = name
    }

    /** Start playing the metronome. */
    fun play() {
        playerConnection.play()
    }

    /** Stop playing the metronome. */
    fun pause() {
        playerConnection.pause()
    }

    /** Synchronize the metronome with the given time.
     * @param timeNanos Time as given by System.nanoTime() with which the first note of the note
     *   list or any further beat should be synchronized.
     */
    fun syncClickWithSystemNanos(timeNanos: Long) {
        playerConnection.syncClickWithSystemNanos(timeNanos)
    }

    /** Set next note index to be played.
     * Normally the player just plays the next note in the note list. This function allows to
     * modify this.
     * @param index Index in note list of next note to be played.
     */
    fun setNextNoteIndex(index: Int) {
        playerConnection.setNextNoteIndex(index)
    }

    /** Restart playing not list from the beginning, skipping all remaining note list notes. */
    fun restartPlayingNoteList() {
        playerConnection.restartPlayingNoteList()
    }

    override fun onCleared() {
//        Log.v("Metronome", "MetronomeViewModel.onCleared")
        PlayerServiceConnection.onDestroy()
        super.onCleared()
    }

    /** Factory to create the view model.
     * @param playerConnection Connection to service, which contains the player.
     */
    class Factory(private val playerConnection: PlayerServiceConnection) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
//            Log.v("Metronome", "MetronomeViewModel.factory.create")
            return MetronomeViewModel(playerConnection) as T
        }
    }
}