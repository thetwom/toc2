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

package de.moekadu.metronome.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import de.moekadu.metronome.R
import de.moekadu.metronome.metronomeproperties.NoteListItem

@SuppressLint("ViewConstructor")
class SoundChooserControlButton(
    context: Context, noteListItem: NoteListItem, volumeColor: Int,
    noteColor: ColorStateList?, noteHighlightColor: ColorStateList?) : NoteView(context) {

//    private var isMovingToTarget = false
//    private var cachedAnimationDuration = 0L
//    private var cachedFadeOut = false

    var isBeingDragged = false
    var eventXOnDown = 0f
        private set
    var eventYOnDown = 0f
        private set
    var translationXInit = 0f
        private set
    var translationYInit = 0f
        private set
    var translationXTarget = 0f
        private set
    var translationYTarget = 0f
        private set
    var moveToTargetOnDelete = false

    var leftBoundToSwitchPosition = 0f
    var rightBoundToSwitchPosition = 0f

    val centerY get() = y + 0.5f * height

    var isActive = false

    val note = noteListItem.clone()
    val uid get() = note.uid
    val volume get() = note.volume
    val noteId get() = note.id
    val noteDuration get() = note.duration

    //private var cachedVisibility: Int? = null
    //private var cachedAlpha: Float? = null
    private var cachedEndAction: (()->Unit)? = null

    private var targetVisibility = GONE
    private var targetAlpha = 1.0f

    init {
        setBackgroundResource(R.drawable.control_button_background)
        this.elevation = elevation
        this.volumeColor = volumeColor
        this.showNumbers = true
        this.noteColor = noteColor
        this.noteColor = noteHighlightColor
        val noteList = ArrayList<NoteListItem>()
        noteList.add(noteListItem)
        setNoteList(noteList, 0L)
        highlightNote(0, true)
        visibility = GONE
    }

    fun set(noteListItem: NoteListItem) {
        require(noteListItem.uid == note.uid)
        note.set(noteListItem)
        setNoteId(0, noteListItem.id)
        setVolume(0, noteListItem.volume)
        setDuration(0, noteListItem.duration)
//        Log.v("Metronome", "SoundChooserControlButton.set: noteListItem.duration: ${noteListItem.duration}")
    }

    fun containsCoordinates(xCo: Float, yCo: Float): Boolean {
        //Log.v("Metronome", "SoundChooserControlButton.containsCoordinates: xCo=$xCo, x=$x, y=$y")
        return xCo > x && xCo < x + width && yCo > y && yCo < y + height
    }

    fun coordinateXWithinBoundsToKeepPosition(coX: Float): Boolean {
//        Log.v("Metronome", "SoundChooserControlButton.coordinateXWihtinBoundsToKeepPosition: coX=$coX, leftBound=$leftBoundToSwitchPosition, rightBound=$rightBoundToSwitchPosition")
        return coX in leftBoundToSwitchPosition..rightBoundToSwitchPosition
    }
    fun centerXWithinBoundsToKeepPosition(): Boolean {
        val centerX = x + 0.5f * width
        return coordinateXWithinBoundsToKeepPosition(centerX)
    }

    fun animateAllNotes() {
        for (i in 0 until size)
            animateNote(i)
    }

    fun startDragging(eventX: Float, eventY: Float) {
        eventXOnDown = eventX
        eventYOnDown = eventY
        translationXInit = translationX
        translationYInit = translationY
        isBeingDragged = true
    }

    fun stopDragging() {
      isBeingDragged = false
    }

    fun setTargetTranslation(xCo: Float, yCo: Float) {
        if (xCo == translationXTarget && yCo == translationYTarget)
            return

        translationXTarget = xCo
        translationYTarget = yCo

        //if (isMovingToTarget)
        //    moveToTarget(cachedAnimationDuration, cachedFadeOut)
    }

//    fun moveToTarget(animationDuration: Long = 0L, fadeOut: Boolean = false, endAction: (()->Unit)? = null) {
//        // don't interrupt running animations if it matches the settings
//        if (isMovingToTarget && endAction == null && fadeOut == cachedFadeOut)
//            return
//
////        if (animationDuration == 0L || visibility != VISIBLE) {
////            translationX = translationXTarget
////            translationY = translationYTarget
////            if (endAction != null)
////                endAction()
////        }
////        else if (translationX != translationXTarget || translationY != translationYTarget || fadeOut) {
//            animate().cancel()
//            val alphaEnd = if (fadeOut) 0.0f else alpha
//            Log.v("Metronome", "SoundChooserControlButton.moveTotarget, alpha=$alpha, alphaEnd=$alphaEnd, fadeOut=$fadeOut, endAction=$endAction")
//            animate()
//                    .setDuration(animationDuration)
//                    .alpha(alphaEnd)
//                    .translationX(translationXTarget)
//                    .translationY(translationYTarget)
//                    .withStartAction {
//                        isMovingToTarget = true
//                        cachedAnimationDuration = animationDuration
//                        cachedFadeOut = fadeOut
//                    }
//                    .withEndAction {
//                        isMovingToTarget = false
//                        if (endAction != null)
//                            endAction()
//                    }
////        }
////        else if (endAction != null) {
////            endAction()
////        }
//    }

    private fun changeState(animationDuration: Long, alphaEnd: Float? = null,
                            visibilityStart: Int? = null, visibilityEnd: Int? = null,
                            endAction: (()->Unit)? = null) {
        val animationDurationCorrected = if (visibility == VISIBLE || visibilityStart == VISIBLE) animationDuration else 0L
        val alphaEndResolved = alphaEnd ?: targetAlpha

        if (alphaEnd != null)
            targetAlpha = alphaEnd

        if (visibilityStart != null)
            targetVisibility = visibilityStart
        if (visibilityEnd != null)
            targetVisibility = visibilityEnd

        cachedEndAction?.let {it()}
        cachedEndAction = endAction

        require(!(visibilityStart != null && visibilityEnd != null))
//        Log.v("Metronome", "SoundChooserControlButton: changeState: uid=$uid, visibilityStart=$visibilityStart, visibilityEnd=$visibilityEnd")
        animate()
            .setDuration(animationDurationCorrected)
            .translationX(translationXTarget)
            .translationY(translationYTarget)
            .alpha(alphaEndResolved)
            .withStartAction {
                if (visibilityStart != null)
                    visibility = visibilityStart
                else if (visibilityEnd == null)
                    visibility = targetVisibility
            }
            .withEndAction {
                translationX = translationXTarget
                translationY = translationYTarget
                visibility = targetVisibility
                cachedEndAction?.let {it()}
                cachedEndAction = null
//                Log.v("Metronome", "SoundChooserControlButton: uid=$uid, visibilityStart=$visibilityStart, visibilityEnd=$visibilityEnd")
            }
    }

    fun moveToTarget2(animationDuration: Long) {
//        Log.v("Metronome", "SoundChooserControlButton.moveTotarget2, uid=$uid")
        changeState(animationDuration, null, null, null)
    }

    fun moveToTarget2AndDisappear(animationDuration: Long, endAction: (()->Unit)? = null) {
//        Log.v("Metronome", "SoundChooserControlButton.moveTotarget2AndDisappear uid=$uid")
        changeState(animationDuration, 0f, null, GONE, endAction)
    }

    fun emerge(animationDuration: Long) {
//        Log.v("Metronome", "SoundChooserControlButton.emerge uid=$uid")
        changeState(animationDuration, 1f, VISIBLE, null)
    }
}