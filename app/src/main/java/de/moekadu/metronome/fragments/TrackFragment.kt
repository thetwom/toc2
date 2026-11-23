/*
 * Copyright 2024 Michael Moessner
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

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.moekadu.metronome.R
import de.moekadu.metronome.metronomeproperties.MAX_NUM_TRACKS
import de.moekadu.metronome.metronomeproperties.Track
import de.moekadu.metronome.preferences.AppPreferences
import de.moekadu.metronome.services.PlayerServiceConnection
import de.moekadu.metronome.viewmodels.MetronomeViewModel
import de.moekadu.metronome.views.TrackPreviewView

/** Page which manages the tracks. Eight rows of equal height, the tracks fill the rows from the top,
 *  the "new track" button sits directly below the last track. */
class TrackFragment : Fragment() {

    private val viewModel by activityViewModels<MetronomeViewModel> {
        val playerConnection = PlayerServiceConnection.getInstance(
            requireContext(),
            AppPreferences.readMetronomeBpm(requireActivity()),
            AppPreferences.readMetronomeTracks(requireActivity()),
            AppPreferences.readIsMute(requireActivity())
        )
        MetronomeViewModel.Factory(playerConnection)
    }

    private val rows = ArrayList<View>(MAX_NUM_TRACKS)
    private val rowBackground = IntArray(MAX_NUM_TRACKS) { Color.TRANSPARENT }
    private val rowFlashRunnables = arrayOfNulls<Runnable>(MAX_NUM_TRACKS)

    private var focusColor = Color.GRAY
    private var flashColor = Color.GRAY
    private var iconColor = Color.BLACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context?.let {
            focusColor = Color.argb(40,
                Color.red(ContextCompat.getColor(it, R.color.colorSecondary)),
                Color.green(ContextCompat.getColor(it, R.color.colorSecondary)),
                Color.blue(ContextCompat.getColor(it, R.color.colorSecondary)))
            flashColor = Color.argb(90,
                Color.red(ContextCompat.getColor(it, R.color.colorSecondary)),
                Color.green(ContextCompat.getColor(it, R.color.colorSecondary)),
                Color.blue(ContextCompat.getColor(it, R.color.colorSecondary)))
            iconColor = ContextCompat.getColor(it, R.color.colorPrimary)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tracks, container, false)

        val rowsContainer = view.findViewById<LinearLayout>(R.id.track_rows)
        for (index in 0 until MAX_NUM_TRACKS) {
            val row = inflater.inflate(R.layout.track_row, rowsContainer, false)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            rowsContainer.addView(row, layoutParams)
            rows.add(row)
            bindRow(row, index)
        }

        view.findViewById<ImageButton>(R.id.to_scenes_button)?.setOnClickListener {
            (parentFragment as? MetronomeAndScenesFragment)?.jumpToPage(ViewPagerAdapter.SCENES)
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.tracks.observe(viewLifecycleOwner) {
            updateRows()
        }
        viewModel.activeTrackIndex.observe(viewLifecycleOwner) {
            updateRows()
        }
        viewModel.currentlyPlayingNote.observe(viewLifecycleOwner) { noteStarted ->
            val uid = noteStarted?.note?.uid ?: return@observe
            val tracks = viewModel.tracks.value ?: return@observe
            val index = tracks.indexOfFirst { track -> track.notes.any { it.uid == uid } }
            if (index >= 0)
                flashRow(index)
        }

        updateRows()
    }

    override fun onDestroyView() {
        for (index in rows.indices) {
            val runnable = rowFlashRunnables[index] ?: continue
            rows[index].removeCallbacks(runnable)
            rowFlashRunnables[index] = null
        }
        rows.clear()
        super.onDestroyView()
    }

    private fun bindRow(row: View, index: Int) {
        row.findViewById<ImageButton>(R.id.track_mute)?.setOnClickListener {
            val track = tracks().getOrNull(index) ?: return@setOnClickListener
            viewModel.setTrackMute(index, !track.isMute)
        }
        row.findViewById<ImageButton>(R.id.track_focus)?.setOnClickListener {
            if (index < tracks().size)
                viewModel.setActiveTrackIndex(index)
        }
        row.findViewById<ImageButton>(R.id.track_delete)?.setOnClickListener {
            if (!viewModel.removeTrack(index)) {
                toast(R.string.cannot_delete_last_track)
            }
        }
        row.findViewById<SeekBar>(R.id.track_volume)?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && index < tracks().size)
                        viewModel.setTrackVolume(index, progress / 100f)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )
        row.findViewById<AppCompatTextView>(R.id.new_track_button)?.setOnClickListener {
            if (viewModel.addTrack() < 0)
                toast(R.string.max_num_tracks_reached)
        }
    }

    private fun tracks(): ArrayList<Track> = viewModel.tracks.value ?: ArrayList()

    private fun updateRows() {
        val tracks = tracks()
        val activeIndex = (viewModel.activeTrackIndex.value ?: 0)
            .coerceIn(0, (tracks.size - 1).coerceAtLeast(0))

        for (index in 0 until MAX_NUM_TRACKS) {
            val row = rows.getOrNull(index) ?: continue
            val content = row.findViewById<View>(R.id.track_content)
            val newTrackButton = row.findViewById<AppCompatTextView>(R.id.new_track_button)
            val track = tracks.getOrNull(index)

            rowBackground[index] = if (index == activeIndex && track != null) focusColor else Color.TRANSPARENT
            row.setBackgroundColor(rowBackground[index])

            if (track != null) {
                content?.visibility = View.VISIBLE
                newTrackButton?.visibility = View.GONE

                row.findViewById<AppCompatTextView>(R.id.track_index)?.text = "${index + 1}"
                row.findViewById<TrackPreviewView>(R.id.track_preview)?.let { preview ->
                    preview.iconColor = iconColor
                    preview.setNotes(track.notes)
                }
                row.findViewById<SeekBar>(R.id.track_volume)?.progress =
                    (track.volume * 100f).toInt().coerceIn(0, 100)
                row.findViewById<ImageButton>(R.id.track_mute)?.setImageResource(
                    if (track.isMute) R.drawable.ic_action_mute_active else R.drawable.ic_action_mute
                )
                row.findViewById<ImageButton>(R.id.track_focus)?.setImageResource(
                    if (index == activeIndex) R.drawable.ic_focus_on else R.drawable.ic_focus_off
                )
                row.findViewById<ImageButton>(R.id.track_focus)?.isEnabled = true
                row.findViewById<ImageButton>(R.id.track_delete)?.isEnabled = tracks.size > 1
            } else if (index == tracks.size && tracks.size < MAX_NUM_TRACKS) {
                content?.visibility = View.GONE
                newTrackButton?.visibility = View.VISIBLE
            } else {
                content?.visibility = View.GONE
                newTrackButton?.visibility = View.GONE
            }
        }
    }

    private fun flashRow(index: Int) {
        val row = rows.getOrNull(index) ?: return
        rowFlashRunnables[index]?.let { row.removeCallbacks(it) }
        row.setBackgroundColor(flashColor)
        val runnable = Runnable {
            row.setBackgroundColor(rowBackground.getOrElse(index) { Color.TRANSPARENT })
            rowFlashRunnables[index] = null
        }
        rowFlashRunnables[index] = runnable
        row.postDelayed(runnable, 130L)
    }

    private fun toast(resourceId: Int) {
        android.widget.Toast.makeText(context, getString(resourceId), android.widget.Toast.LENGTH_SHORT).show()
    }
}
