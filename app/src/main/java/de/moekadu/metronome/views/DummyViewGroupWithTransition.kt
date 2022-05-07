/*
 * Copyright 2021 Michael Moessner
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

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

class DummyViewGroupWithTransition(context : Context, attrs : AttributeSet?, defStyleAttr : Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    private val dummyView = View(context)

    constructor(context: Context, attrs: AttributeSet? = null)
            : this(context, attrs, 0)

    init {
        addView(dummyView)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpec = MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY)
        dummyView.measure(widthSpec, heightSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        dummyView.layout(0, 0, 1, 1)
    }

//    fun dummyTransition() {
//        // Log.v("Metronome", "DummyViewGroupWithTransition.dummyTransition")
//        TransitionManager.beginDelayedTransition(this,
//                AutoTransition().apply {
//                    duration = 1L
//                }
//        )
//        if (dummyView.visibility == View.VISIBLE)
//            dummyView.visibility = View.GONE
//        else
//            dummyView.visibility = View.VISIBLE
//    }
}