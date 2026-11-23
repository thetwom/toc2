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

package de.moekadu.metronome.fragments

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/** Adapter for the three pages metronome, tracks and scenes.
 *
 *  The track page is always shown in portrait, therefore it does not exist in landscape. In
 *  landscape the pages are metronome and scenes, so that a swipe from the metronome page leads
 *  directly to the scenes page.
 *
 * @param activity Activity which hosts the pages.
 * @param includeTracks If true the track page is part of the pages (portrait).
 */
class ViewPagerAdapter(activity: FragmentActivity, val includeTracks: Boolean) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int {
        return if (includeTracks) NUM_FRAGMENTS else NUM_FRAGMENTS_WITHOUT_TRACKS
    }

    override fun createFragment(position: Int): Fragment {
        return when (pageOf(position)) {
            METRONOME -> MetronomeFragment()
            TRACKS -> TrackFragment()
            SCENES -> ScenesFragment()
            else -> throw RuntimeException("No fragment for position $position")
        }
    }

    /** Logical page of a view pager position. */
    fun pageOf(position: Int): Int {
        return if (!includeTracks && position >= TRACKS) position + 1 else position
    }

    /** View pager position of a logical page. */
    fun positionOf(page: Int): Int {
        return if (!includeTracks && page > METRONOME) page - 1 else page
    }

    companion object {
        const val METRONOME = 0
        const val TRACKS = 1
        const val SCENES = 2
        const val NUM_FRAGMENTS = 3
        const val NUM_FRAGMENTS_WITHOUT_TRACKS = 2
    }
}
