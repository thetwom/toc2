package de.moekadu.metronome

import android.content.Context

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
        val privateNote = NoteListItem().apply { set(note) }
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