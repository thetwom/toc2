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
import de.moekadu.metronome.metronomeproperties.MAX_NUM_TRACKS
import de.moekadu.metronome.metronomeproperties.NoteListItem
import de.moekadu.metronome.metronomeproperties.Track
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
    /** Currently used tracks. */
    val tracks get() = playerConnection.tracks

    /** Index of the track which is currently edited. */
    private val _activeTrackIndex = MutableLiveData(0)
    val activeTrackIndex: LiveData<Int> get() = _activeTrackIndex

    /** Select the track which is currently edited.
     * @param index Index of the track, clamped to the valid range.
     */
    fun setActiveTrackIndex(index: Int) {
        val size = tracks.value?.size ?: 0
        val corrected = if (size == 0) 0 else index.coerceIn(0, size - 1)
        if (_activeTrackIndex.value != corrected)
            _activeTrackIndex.value = corrected
    }

    /** Index of the currently edited track or -1 if there is none. */
    private fun currentTrackIndex(): Int {
        val size = tracks.value?.size ?: 0
        if (size == 0) return -1
        return (_activeTrackIndex.value ?: 0).coerceIn(0, size - 1)
    }

    /** Notes of the currently edited track (null if there is no track). */
    fun currentTrackNotes(): ArrayList<NoteListItem>? {
        val index = currentTrackIndex()
        return if (index < 0) null else tracks.value?.getOrNull(index)?.notes
    }

    /** Replace all tracks, e.g. when a scene is loaded. */
    fun setTracks(newTracks: ArrayList<Track>) {
        playerConnection.setTracks(newTracks)
        setActiveTrackIndex(0)
    }

    /** Add an empty track and select it.
     * @return Index of the new track or -1 if the maximum number of tracks is reached.
     */
    fun addTrack(): Int {
        var newIndex = -1
        playerConnection.modifyTracks { trackList ->
            if (trackList.size >= MAX_NUM_TRACKS) {
                false
            } else {
                trackList.add(Track())
                newIndex = trackList.size - 1
                true
            }
        }
        if (newIndex >= 0)
            setActiveTrackIndex(newIndex)
        return newIndex
    }

    /** Remove a track. The last remaining track cannot be removed. */
    fun removeTrack(index: Int): Boolean {
        var removed = false
        playerConnection.modifyTracks { trackList ->
            if (trackList.size <= 1 || index !in trackList.indices) {
                false
            } else {
                trackList.removeAt(index)
                removed = true
                true
            }
        }
        if (removed) {
            val size = tracks.value?.size ?: 1
            setActiveTrackIndex(min(index, size - 1))
        }
        return removed
    }

    /** Change the volume of a whole track. */
    fun setTrackVolume(index: Int, volume: Float) {
        playerConnection.modifyTracks { trackList ->
            val track = trackList.getOrNull(index)
            if (track != null) {
                track.volume = volume
                true
            } else {
                false
            }
        }
    }

    /** Mute or unmute a single track. */
    fun setTrackMute(index: Int, isMute: Boolean) {
        playerConnection.modifyTracks { trackList ->
            val track = trackList.getOrNull(index)
            if (track != null) {
                track.isMute = isMute
                true
            } else {
                false
            }
        }
    }
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
        val trackIndex = currentTrackIndex()
        playerConnection.modifyTracks { trackList ->
            val notes = trackList.getOrNull(trackIndex)?.notes
            if (notes != null) {
                notes.clear()
                notes.addAll(noteList)
                true
            } else {
                false
            }
        }
    }

    /** Change volume of a note by providing the note list index (within the edited track). */
    fun setNoteListVolume(index: Int, volume: Float) {
        val trackIndex = currentTrackIndex()
        playerConnection.modifyTracks { trackList ->
            val notes = trackList.getOrNull(trackIndex)?.notes
            if (notes != null && index in notes.indices) {
                notes[index].volume = volume
                true
            } else {
                false
            }
        }
    }

    /** Change volume of a note by providing the note uid. */
    fun setNoteListVolume(uid: UId, volume: Float) {
        playerConnection.modifyTracks { trackList ->
            var success = false
            for (track in trackList)
                track.notes.filter { uid == it.uid }.forEach {
                    it.volume = volume
                    success = true
                }
            success
        }
    }

    /** Change type of note. */
    fun setNoteListId(uid: UId, id: Int) {
        playerConnection.modifyTracks { trackList ->
            var success = false
            for (track in trackList)
                track.notes.filter { uid == it.uid }.forEach {
                    it.id = id
                    success = true
                }
            success
        }
    }

    /** Change note duration of a note (quarter, eights, ...). */
    fun setNoteListDuration(uid: UId, duration: NoteDuration) {
        playerConnection.modifyTracks { trackList ->
            var success = false
            for (track in trackList)
                track.notes.filter { uid == it.uid }.forEach {
                    it.duration = duration
                    success = true
                }
            success
        }
    }

    /** Add a note to the currently edited track. */
    fun addNote(noteListItem: NoteListItem, index: Int? = null) {
        val trackIndex = currentTrackIndex()
        playerConnection.modifyTracks { trackList ->
            val notes = trackList.getOrNull(trackIndex)?.notes
            if (notes != null) {
                val i = if (index == null) notes.size else min(index, notes.size)
                notes.add(i, noteListItem)
                true
            } else {
                false
            }
        }
    }

    /** Remove a note from any track. */
    fun removeNote(uid: UId) {
        playerConnection.modifyTracks { trackList ->
            var success = false
            for (track in trackList)
                if (track.notes.removeAll { it.uid == uid })
                    success = true
            success
        }
    }

    /** Move a note within its track. */
    fun moveNote(uid: UId, toIndex: Int) {
        playerConnection.modifyTracks { trackList ->
            for (track in trackList) {
                val notes = track.notes
                val fromIndex = notes.indexOfFirst { it.uid == uid }
                if (fromIndex >= 0) {
                    val toIndexCorrected = min(toIndex, notes.size - 1)
                    if (toIndexCorrected == fromIndex)
                        return@modifyTracks false
                    val note = notes.removeAt(fromIndex)
                    notes.add(toIndexCorrected, note)
                    return@modifyTracks true
                }
            }
            false
        }
    }

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