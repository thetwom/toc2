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

import android.util.Log
import java.security.KeyStore
import kotlin.math.min

/// Item in note list
/**
 * @param id Id identifying which note is played
 * @param volume Note volume (0.0f <= volume <= 1.0f
 * @param duration Note duration in seconds
 */
class NoteListItem(var id : Int = 0, var volume : Float = 1.0f, var duration : Float = 1.0f) {
    fun set(value : NoteListItem) {
        id = value.id
        volume = value.volume
        duration = value.duration
    }

    fun clone() : NoteListItem {
        val c = NoteListItem()
        c.set(this)
        return c
    }
}

class NoteList : Collection<NoteListItem>{
    companion object {
        const val STRING_OK = true
        const val STRING_INVALID = false

        fun checkString(string: String): Boolean {
            val elements = string.split(" ")
            for(i in 0 until elements.size / 2) {
                try {
                    elements[2 * i].toInt()
                    elements[2 * i + 1].toFloat()
                }
                catch (e: NumberFormatException) {
                    return STRING_INVALID
                }
            }
            return STRING_OK
        }
    }
    private val notes = ArrayList<NoteListItem>()

    override val size get() = notes.size
    operator fun get(index: Int) = notes[index]

    interface NoteListChangedListener {
        fun onNoteAdded(note: NoteListItem, index: Int)
        fun onNoteRemoved(note: NoteListItem, index: Int)
        fun onNoteMoved(note: NoteListItem, fromIndex: Int, toIndex: Int)
        fun onVolumeChanged(note: NoteListItem, index: Int)
        fun onNoteIdChanged(note: NoteListItem, index: Int)
        fun onDurationChanged(note: NoteListItem, index: Int)
    }

    private val noteListChangedListener = ArrayList<NoteListChangedListener>()

    fun add(note: NoteListItem, index : Int = size) {
        require(notes.indexOf(note) < 0) // make sure that each note only exists once!!
        notes.add(index, note)
        Log.v("Metronome", "NoteList.add: noteListChangedListener.size = ${noteListChangedListener.size}")
        for (n in noteListChangedListener) {
            n.onNoteAdded(note, index)
            Log.v("Metronome", "NoteList.add: called onNoteAdded")
        }
    }

    fun remove(note: NoteListItem) {
        val index = notes.indexOf(note)
        if (index >= 0) {
            notes.remove(note)
            for (n in noteListChangedListener)
                n.onNoteRemoved(note, index)
        }
    }

    fun move(fromIndex : Int, toIndex : Int) {
        if(fromIndex in 0 until notes.size && toIndex >= 0) {
            val note = notes[fromIndex]
            notes.removeAt(fromIndex)
            notes.add(min(notes.size, toIndex), note)
            for (n in noteListChangedListener)
                n.onNoteMoved(note, fromIndex, toIndex)
        }
    }

    fun setVolume(index: Int, volume: Float) {
        if (index in 0 until notes.size && notes[index].volume != volume) {
            notes[index].volume = volume
            for (n in noteListChangedListener)
                n.onVolumeChanged(notes[index], index)
        }
    }

    fun setNote(index: Int, noteId: Int) {
        if (index in 0 until notes.size && notes[index].id != noteId) {
            notes[index].id = noteId
            for (n in noteListChangedListener) {
                n.onNoteIdChanged(notes[index], index)
            }
        }
    }

    /// Set not duration in seconds.
    /**
     * @param index Note index.
     * @param duration Duration in seconds.
     */
    fun setDuration(index: Int, duration: Float) {
        if (index in 0 until notes.size && notes[index].duration != duration) {
            notes[index].duration = duration
            for (n in noteListChangedListener) {
                n.onDurationChanged(notes[index], index)
            }
        }

    }

    fun indexOf(note: NoteListItem): Int {
        for(i in notes.indices) {
            if (notes[i] === note)
                return i
        }
        return -1
    }

    fun last(): NoteListItem {
        return notes.last()
    }

    override fun isEmpty(): Boolean {
        return notes.isEmpty()
    }

    fun registerNoteListChangedListener(noteListChangedListener: NoteListChangedListener) {
        this.noteListChangedListener.add(noteListChangedListener)
    }

    fun unregisterNoteListChangedListener(noteListChangedListener: NoteListChangedListener) {
        this.noteListChangedListener.remove(noteListChangedListener)
    }

    override fun toString(): String {
        var s = ""
        for (note in notes) {
            s += "${note.id} ${note.volume} "
        }
        return s
    }

    fun fromString(string: String) {
        val elements = string.split(" ")
        while (size > 0)
            remove(notes.last())
        for(i in 0 until elements.size / 2) {
            add(NoteListItem(elements[2 * i].toInt(), elements[2 * i + 1].toFloat(), -1f))
        }
    }

    override fun iterator(): Iterator<NoteListItem> {
        return notes.iterator()
    }

    override fun contains(element: NoteListItem): Boolean {
        return notes.contains(element)
    }

    override fun containsAll(elements: Collection<NoteListItem>): Boolean {
        return notes.containsAll(elements)
    }
}

data class NoteInfo(val audio44ResourceID : Int, val audio48ResourceID : Int, val stringResourceID : Int, val drawableResourceID : Int)

val availableNotes = arrayOf(
        NoteInfo(R.raw.base44_wav, R.raw.base48_wav, R.string.base, R.drawable.ic_note_a),
        NoteInfo(R.raw.snare44_wav, R.raw.snare48_wav, R.string.snare, R.drawable.ic_note_c),
        NoteInfo(R.raw.sticks44_wav, R.raw.sticks48_wav, R.string.sticks, R.drawable.ic_note_c_rim),
        NoteInfo(R.raw.woodblock_high44_wav, R.raw.woodblock_high48_wav, R.string.woodblock, R.drawable.ic_note_ep),
        NoteInfo(R.raw.claves44_wav, R.raw.claves48_wav, R.string.claves, R.drawable.ic_note_gp),
        // NoteInfo(R.raw.hhp_dry_a, R.string.hihat, R.drawable.ic_hihat),
        NoteInfo(R.raw.hihat44_wav, R.raw.hihat48_wav, R.string.hihat, R.drawable.ic_note_hihat),
        // NoteInfo(R.raw.sn_jazz_c, R.string.snare, R.drawable.ic_snare),
        NoteInfo(R.raw.mute44_wav, R.raw.mute48_wav, R.string.mute, R.drawable.ic_note_pause)
)

fun getNumAvailableNotes() = availableNotes.size

const val defaultNote = 3

fun getNoteAudioResourceID(index : Int, sampleRate: Int) = when(sampleRate) {  // AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
    48000 -> availableNotes[index].audio48ResourceID
    44100 -> availableNotes[index].audio44ResourceID
    else ->  availableNotes[index].audio48ResourceID
}

//fun getNoteStringResourceID(index : Int) = availableNotes[index].stringResourceID

fun getNoteDrawableResourceID(index : Int) = availableNotes[index].drawableResourceID