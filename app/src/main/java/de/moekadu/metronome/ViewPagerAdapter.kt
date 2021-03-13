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
//            SETTINGS -> {
//                SettingsFragment()
//            }
            else -> {
                throw RuntimeException("No fragment for position $position")
            }
        }
    }

    companion object {
        const val METRONOME = 0
        const val SAVE_DATA = 1
        //const val SETTINGS = 2
        //const val NUM_FRAGMENTS = 3
        const val NUM_FRAGMENTS = 2
    }
}