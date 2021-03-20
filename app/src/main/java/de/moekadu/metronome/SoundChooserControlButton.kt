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
import android.content.res.ColorStateList
import android.view.MotionEvent

@SuppressLint("ViewConstructor")
class SoundChooserControlButton(
        context: Context, note: NoteListItem, elevation: Float, volumeColor: Int,
        noteColor: ColorStateList?, noteHighlightColor: ColorStateList?) : NoteView(context) {

    var eventXOnDown = 0f
    var eventYOnDown = 0f
    var translationXInit = 0f
    var translationYInit = 0f
    var translationXTarget = 0f
    var translationYTarget = 0f
    val uid = note.uid

    var volume = note.volume
        private set
    var noteId = note.id
        private set

    init {
        setBackgroundResource(R.drawable.control_button_background)
        this.elevation = elevation
        this.volumeColor = volumeColor
        this.showNumbers = true
        this.noteColor = noteColor
        this.noteColor = noteHighlightColor
        val noteList = ArrayList<NoteListItem>()
        noteList.add(note)
        setNoteList(noteList)
        highlightNote(0, true)
    }

    fun set(noteListItem: NoteListItem) {
        require(noteListItem.uid == uid)
        setNoteId(0, noteListItem.id)
        setVolume(0, noteListItem.volume)
        noteId = noteListItem.id
        volume = noteListItem.volume
    }

    fun animateAllNotes() {
        for (i in 0 until size)
            animateNote(i)
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