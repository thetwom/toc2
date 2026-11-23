package de.moekadu.metronome.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import androidx.appcompat.content.res.AppCompatResources
import de.moekadu.metronome.R
import de.moekadu.metronome.metronomeproperties.NoteDuration
import de.moekadu.metronome.metronomeproperties.getNoteDrawableResourceID

internal fun tupletNumberResource(size: Int) = when (size) {
    3 -> R.drawable.ic_triplet
    5 -> R.drawable.ic_quintuplet
    7 -> R.drawable.ic_septuplet
    else -> 0
}

/** Compact note and tuplet numeral for surfaces without a full notation bracket. */
internal fun notePreviewDrawable(context: Context, noteId: Int, duration: NoteDuration): Drawable? {
    val note = AppCompatResources.getDrawable(context, getNoteDrawableResourceID(noteId, duration))
        ?: return null
    val numeralResource = tupletNumberResource(duration.tupletSize)
    if (numeralResource == 0) return note
    val numeral = AppCompatResources.getDrawable(context, numeralResource) ?: return note
    val numeralHeight = note.intrinsicHeight / 4
    val numeralWidth = numeralHeight * numeral.intrinsicWidth / numeral.intrinsicHeight
    return LayerDrawable(arrayOf(note, numeral)).apply {
        paddingMode = LayerDrawable.PADDING_MODE_STACK
        setLayerSize(0, note.intrinsicWidth, note.intrinsicHeight)
        setLayerGravity(0, Gravity.LEFT or Gravity.TOP)
        setLayerSize(1, numeralWidth, numeralHeight)
        setLayerGravity(1, Gravity.LEFT or Gravity.TOP)
        setLayerInset(1, note.intrinsicWidth, note.intrinsicHeight / 6, 0, 0)
    }
}
