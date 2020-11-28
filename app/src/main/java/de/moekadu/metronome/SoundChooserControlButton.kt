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

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("ViewConstructor")
class SoundChooserControlButton(context: Context, note: NoteListItem, elevation: Float, volumeColor: Int) : NoteView(context) {

    var eventXOnDown = 0f
    var eventYOnDown = 0f
    var translationXInit = 0f
    var translationYInit = 0f
    var translationXTarget = 0f
    var translationYTarget = 0f

    fun animateAllNotes() {
        noteList?.let { notes ->
            for (n in notes)
                animateNote(n)
        }
    }

    var volume
        get() = noteList?.get(0)?.volume ?: 0f
        set(value) {noteList?.setVolume(0, value)}

     var noteId
        get() = noteList?.get(0)?.id ?: defaultNote
        set(value) {noteList?.setNote(0, value)}

    init {
        setBackgroundResource(R.drawable.control_button_background)
        this.elevation = elevation
        this.volumeColor = volumeColor

        val privateNoteList = NoteList()
        val privateNote = NoteListItem().apply {
            set(note)
            hash = -1
        }
        privateNoteList.add(privateNote)
        noteList = privateNoteList
        highlightNote(0, true)
    }

    fun moveToTarget(animationDuration: Long = 0L) {
        if (animationDuration == 0L) {
            translationX = translationXTarget
            translationY = translationYTarget
        }
        else {
            animate()
                    .setDuration(animationDuration)
                    .translationX(translationXTarget)
                    .translationY(translationYTarget)
                    .start()
        }
    }
}