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
import android.util.Log
import android.view.MotionEvent

@SuppressLint("ViewConstructor")
class SoundChooserControlButton2(
        context: Context, val note: NoteListItem, volumeColor: Int,
        noteColor: ColorStateList?, noteHighlightColor: ColorStateList?) : NoteView(context) {

    var isMoving = false
    var eventXOnDown = 0f
    var eventYOnDown = 0f
    var translationXInit = 0f
    var translationYInit = 0f
    var translationXTarget = 0f
    var translationYTarget = 0f
    var moveToTargetOnDelete = false
    val uid = note.uid

    var isActive = false

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
        visibility = GONE
    }

//    override fun onTouchEvent(event: MotionEvent?): Boolean {
//        val action = event?.actionMasked ?: return false
//
//        when (action) {
//            MotionEvent.ACTION_DOWN -> {
//                visibility = VISIBLE
//                return true
//            }
//            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                visibility = INVISIBLE
//                return true
//            }
//        }
//        return false
//    }

    fun set(noteListItem: NoteListItem) {
        require(noteListItem.uid == uid)
        setNoteId(0, noteListItem.id)
        setVolume(0, noteListItem.volume)
        noteId = noteListItem.id
        volume = noteListItem.volume
    }

    fun setTargetTranslation(targetX: Float, targetY: Float, animationDuration: Long = 0L) {
        translationXTarget = targetX
        translationYTarget = targetY
        moveToTarget(animationDuration)
    }

    fun containsCoordinates(xCo: Float, yCo: Float): Boolean {
        //Log.v("Metronome", "SoundChooserControlButton2.containsCoordinates: xCo=$xCo, x=$x, y=$y")
        return xCo > x && xCo < x + width && yCo > y && yCo < y + height
    }
    fun animateAllNotes() {
        for (i in 0 until size)
            animateNote(i)
    }

    fun moveToTarget(animationDuration: Long = 0L, endAction: (()->Unit)? = null) {
        if (animationDuration == 0L || visibility != VISIBLE) {
            translationX = translationXTarget
            translationY = translationYTarget
        }
        else if (translationX != translationXTarget || translationY != translationYTarget) {
            animate()
                    .setDuration(animationDuration)
                    .translationX(translationXTarget)
                    .translationY(translationYTarget)
                    .withEndAction {
                        if (endAction != null)
                            endAction()
                    }
        }
    }

    fun moveToTargetAndDisappear(animationDuration: Long = 0L, endAction: (()->Unit)? = null) {
        if (visibility != VISIBLE)
            moveToTarget(0L)
        else {
            animate()
                .setDuration(animationDuration)
                .translationX(translationXTarget)
                .translationY(translationYTarget)
                .alpha(0f)
                .withEndAction {
                    visibility = INVISIBLE
                    alpha = 1f
                    if (endAction != null)
                        endAction()
                }
        }
    }
}