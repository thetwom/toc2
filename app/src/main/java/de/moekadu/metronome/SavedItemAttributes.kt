/*
 * Copyright 2019 Michael Moessner
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

package de.moekadu.metronome;

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View


class SavedItemAttributes(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    val deleteColor: Int
    val onDeleteColor: Int

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.SavedItemAttributes)
        deleteColor = ta.getColor(R.styleable.SavedItemAttributes_deleteColor, Color.RED)
        onDeleteColor = ta.getColor(R.styleable.SavedItemAttributes_onDeleteColor, Color.WHITE)
        ta.recycle()
    }
}
