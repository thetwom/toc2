/*
 * Copyright 2024 Michael Moessner
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

package de.moekadu.metronome.metronomeproperties

import kotlin.math.min

/** Maximum number of tracks which can be played at the same time. */
const val MAX_NUM_TRACKS = 8

/// One track of the metronome.
/**
 * Every track has its own note list and is looped independently, i.e. tracks of different length
 * drift against each other which gives poly metric patterns.
 *
 * @param notes Notes of this track.
 * @param volume Volume of the whole track (0.0f <= volume <= 1.0f).
 * @param isMute If true, the track is not audible but still visualized.
 * @param uid Identifier which defines if two tracks are the same.
 */
class Track(var notes: ArrayList<NoteListItem> = ArrayList(),
            var volume: Float = 1.0f,
            var isMute: Boolean = false,
            val uid: UId = UId.create()) {

    fun clone(): Track {
        val notesCopy = ArrayList<NoteListItem>()
        deepCopyNoteList(notes, notesCopy)
        return Track(notesCopy, volume, isMute, uid)
    }

    fun isEqual(other: Track?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (volume != other.volume) return false
        if (isMute != other.isMute) return false
        if (uid != other.uid) return false
        return areNoteListsEqual(notes, other.notes)
    }
}

/** Copy the tracks of origin into target without changing the track objects themselves. */
fun deepCopyTracks(origin: ArrayList<Track>, target: ArrayList<Track>) {
    var i = 0
    while (i < origin.size && i < target.size) {
        target[i].volume = origin[i].volume
        target[i].isMute = origin[i].isMute
        if (target[i].uid == origin[i].uid)
            deepCopyNoteList(origin[i].notes, target[i].notes)
        else
            target[i] = origin[i].clone()
        ++i
    }
    if (target.size > origin.size)
        target.subList(origin.size, target.size).clear()
    for (j in i until origin.size)
        target.add(origin[j].clone())
}

fun areTrackListsEqual(a: ArrayList<Track>?, b: ArrayList<Track>?): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    if (a.size != b.size) return false
    for (i in a.indices)
        if (!a[i].isEqual(b[i])) return false
    return true
}

/// Serialize the track list.
/**
 * Format (version 2):
 * `#2 <numTracks> ( <volume> <mute> <numNotes> ( <id> <volume> <duration> )* )*`
 */
fun tracksToString(tracks: ArrayList<Track>): String {
    val stringBuilder = StringBuilder()
    stringBuilder.append("#2 ").append(tracks.size)
    for (track in tracks) {
        stringBuilder.append(" ").append(track.volume)
        stringBuilder.append(" ").append(if (track.isMute) 1 else 0)
        stringBuilder.append(" ").append(track.notes.size)
        for (note in track.notes) {
            stringBuilder.append(" ").append(note.id)
            stringBuilder.append(" ").append(note.volume)
            stringBuilder.append(" ").append(note.duration.name)
        }
    }
    return stringBuilder.toString()
}

/// Read a track list from a string. Strings of the older versions (single track) are accepted.
fun stringToTracks(string: String): ArrayList<Track> {
    val version = getNoteListStringVersion(string)
    if (version < 2) {
        val tracks = ArrayList<Track>()
        val notes = try {
            stringToNoteList(string)
        } catch (_: Exception) {
            ArrayList()
        }
        tracks.add(Track(notes))
        return tracks
    }

    val elements = string.split(" ")
    val tracks = ArrayList<Track>()
    var index = 1
    try {
        val numTracks = min(elements[index++].toInt(), MAX_NUM_TRACKS)
        for (t in 0 until numTracks) {
            val volume = elements[index++].toFloat().coerceIn(0f, 1f)
            val isMute = elements[index++].toInt() != 0
            val numNotes = elements[index++].toInt()
            val notes = ArrayList<NoteListItem>(numNotes)
            for (n in 0 until numNotes) {
                val noteId = min(elements[index++].toInt(), getNumAvailableNotes() - 1)
                val noteVolume = elements[index++].toFloat()
                val duration = NoteDuration.valueOf(elements[index++])
                notes.add(NoteListItem(noteId, noteVolume, duration))
            }
            tracks.add(Track(notes, volume, isMute))
        }
    } catch (_: Exception) {
        // keep what we could read, a broken scene should not crash the app
    }
    if (tracks.isEmpty())
        tracks.add(Track())
    return tracks
}
