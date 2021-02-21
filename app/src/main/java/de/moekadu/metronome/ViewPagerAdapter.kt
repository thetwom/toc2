package de.moekadu.metronome

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity, ) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int {
        return NUM_FRAGMENTS
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            METRONOME -> {
                MetronomeFragment()
            }
            SAVE_DATA -> {
                SaveDataFragment()
            }
            SETTINGS -> {
                SettingsFragment()
            }
            else -> {
                throw RuntimeException("No fragment for position $position")
            }
        }
    }

    companion object {
        const val SETTINGS = 0
        const val METRONOME = 1
        const val SAVE_DATA = 2
        const val NUM_FRAGMENTS = 3
    }
}