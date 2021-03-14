package de.moekadu.metronome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2

class MetronomeAndScenesFragment : Fragment() {

    private val metronomeViewModel by activityViewModels<MetronomeViewModel> {
        val playerConnection = PlayerServiceConnection.getInstance(
                requireContext(),
                AppPreferences.readMetronomeSpeed(requireActivity()),
                AppPreferences.readMetronomeNoteList(requireActivity())
        )
        MetronomeViewModel.Factory(playerConnection)
    }

    private val scenesViewModel by activityViewModels<ScenesViewModel> {
        ScenesViewModel.Factory(AppPreferences.readScenesDatabase(requireActivity()))
    }

    var viewPager: ViewPager2? = null

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            (activity as MainActivity?)?.setDisplayHomeButton()
            super.onPageSelected(position)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_metronome_and_scenes, container, false)

        viewPager = view.findViewById(R.id.viewpager)
        viewPager?.adapter = ViewPagerAdapter(requireActivity())

        (activity as MainActivity?)?.setDisplayHomeButton()

        viewPager?.registerOnPageChangeCallback(pageChangeListener)

        metronomeViewModel.disableViewPageUserInput.observe(viewLifecycleOwner) {
            lockViewPager()
        }

        scenesViewModel.editingStableId.observe(viewLifecycleOwner) {
            lockViewPager()
        }
        return view
    }

    override fun onDestroyView() {
        viewPager?.unregisterOnPageChangeCallback(pageChangeListener)
        super.onDestroyView()
    }

    private fun lockViewPager() {
        var lock = false

        if (metronomeViewModel.disableViewPageUserInput.value == true)
            lock = true

        scenesViewModel.editingStableId.value?.let {
            if (it != Scene.NO_STABLE_ID)
                lock = true
        }

        viewPager?.isUserInputEnabled = !lock
    }

}