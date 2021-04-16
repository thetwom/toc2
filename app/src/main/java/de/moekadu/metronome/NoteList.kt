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

@file:Suppress("DataClassPrivateConstructor")

package de.moekadu.metronome

import android.util.Log
import kotlin.math.min

data class UId private constructor(private val id: Int) {
    companion object {
        @Volatile
        private var c = 0
        fun create(): UId {
            val i = synchronized(this) {
                c += 1
                c
            }
            return UId(i)
        }
    }
}

/// Item in note list
/**
 * @param id Id identifying which note is played
 * @param volume Note volume (0.0f <= volume <= 1.0f
 * @param duration Note duration in seconds
 * @param uid Identifier which defines if two items are the same
 */
class NoteListItem(var id : Int = 0, var volume : Float = 1.0f, var duration : Float = 1.0f,
                   var uid: UId = UId.create()) {
    fun set(value : NoteListItem) {
        id = value.id
        volume = value.volume
        duration = value.duration
        uid = value.uid
    }

    fun clone() : NoteListItem {
        return NoteListItem(id, volume, duration, uid)
    }
}

fun deepCopyNoteList(origin: ArrayList<NoteListItem>, target: ArrayList<NoteListItem>) {
    if (target.size > origin.size)
        target.subList(origin.size, target.size).clear()
    for (i in target.indices)
        target[i].set(origin[i])
    for (i in target.size until origin.size)
        target.add(origin[i].clone())
}

fun isNoteListStringValid(string: String): Boolean {
    val elements = string.split(" ")
    for(i in 0 until elements.size / 2) {
        try {
            elements[2 * i].toInt()
            elements[2 * i + 1].toFloat()
        }
        catch (e: NumberFormatException) {
            return false
        }
    }
    return true
}
fun noteListToString(noteList: ArrayList<NoteListItem>): String {
    var s = ""
    for (note in noteList) {
        s += "${note.id} ${note.volume} "
    }
    return s
}

fun stringToNoteList(string: String): ArrayList<NoteListItem> {
    val noteList = ArrayList<NoteListItem>()
    val elements = string.split(" ")
//    Log.v("Metronome", "NoteList: stringToNoteList: string: $string")
    for (i in 0 until elements.size / 2) {
        val noteId = min(elements[2 * i].toInt(), getNumAvailableNotes() - 1)
        val volume = elements[2 * i + 1].toFloat()
        noteList.add(NoteListItem(noteId, volume, -1f))
    }
    return noteList
}


data class NoteInfo(val audio44ResourceID: Int, val audio48ResourceID: Int,
                    val stringResourceID: Int, val drawableResourceID: Int,
                    val vibrationDuration: Long)

val availableNotes = arrayOf(
        NoteInfo(R.raw.base44_wav, R.raw.base48_wav, R.string.base, R.drawable.ic_note_a, 80L),
        NoteInfo(R.raw.snare44_wav, R.raw.snare48_wav, R.string.snare, R.drawable.ic_note_c, 65L),
        NoteInfo(R.raw.sticks44_wav, R.raw.sticks48_wav, R.string.sticks, R.drawable.ic_note_c_rim, 65L),
        NoteInfo(R.raw.woodblock_high44_wav, R.raw.woodblock_high48_wav, R.string.woodblock, R.drawable.ic_note_ep, 50L),
        NoteInfo(R.raw.claves44_wav, R.raw.claves48_wav, R.string.claves, R.drawable.ic_note_gp, 35L),
        // NoteInfo(R.raw.hhp_dry_a, R.string.hihat, R.drawable.ic_hihat, 30L),
        NoteInfo(R.raw.hihat44_wav, R.raw.hihat48_wav, R.string.hihat, R.drawable.ic_note_hihat, 35L),
        // NoteInfo(R.raw.sn_jazz_c, R.string.snare, R.drawable.ic_snare, 30L),
        NoteInfo(R.raw.mute44_wav, R.raw.mute48_wav, R.string.mute, R.drawable.ic_note_pause, 0L)
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

fun getNoteVibrationDuration(index : Int) = availableNotes[index].vibrationDuration