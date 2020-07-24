package de.moekadu.metronome

import android.media.AudioManager
import android.media.AudioTrack

data class NoteListItem(var id : Int = 0, var volume : Float = 1.0f, var duration : Float = 1.0f) {
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
typealias NoteList = ArrayList<NoteListItem>

fun NoteList.removeNote(note : NoteListItem) {
    for (i in this.indices)
        if(this[i] === note) {
            this.removeAt(i)
            break
        }
}

fun NoteList.compareNoteListItemInstances(other : NoteList) : Boolean {
    if(this.size != other.size)
        return false
    for(i in this.indices) {
        if (!(this[i] === other[i])){
            return false
        }
    }
    return true
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

fun getNoteAudioResourceID(index : Int) = when(AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)) {
    48000 -> availableNotes[index].audio48ResourceID
    44100 -> availableNotes[index].audio44ResourceID
    else ->  availableNotes[index].audio48ResourceID
}

fun getNoteStringResourceID(index : Int) = availableNotes[index].stringResourceID

fun getNoteDrawableResourceID(index : Int) = availableNotes[index].drawableResourceID