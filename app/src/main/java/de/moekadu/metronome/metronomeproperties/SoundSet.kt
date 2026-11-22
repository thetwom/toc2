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

package de.moekadu.metronome.metronomeproperties

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import de.moekadu.metronome.R
import java.util.concurrent.CopyOnWriteArrayList

/** A complete set of note sounds which can be selected as metronome sound.
 *
 *  Every sound set holds the same number of notes as [defaultNotes], the last one being mute.
 *  This keeps note ids which are stored in saved scenes valid when the sound set changes. */
data class SoundSet(
    val id: String,
    @StringRes val nameResourceID: Int,
    val notes: Array<NoteInfo>
)

private fun note(
    audio44: Int, audio48: Int, @StringRes name: Int,
    quarter: Int, eighth: Int, sixteenth: Int, vibration: Long
) = NoteInfo(audio44, audio48, name, quarter, eighth, sixteenth, vibration)

/// Mute note which is part of every sound set (same as in the original sound set).
private fun muteNote() = defaultNotes.first { it.stringResourceID == R.string.mute }

object SoundSets {

    val default = SoundSet("default", R.string.soundset_default, defaultNotes)

    /// Ableton Live like metronome clicks: very short sine pulses.
    val ableton = SoundSet(
        "ableton", R.string.soundset_ableton, arrayOf(
            note(R.raw.ableton_0_44_wav, R.raw.ableton_0_48_wav, R.string.click_1568,
                R.drawable.ic_note_a, R.drawable.ic_note_a_eighth, R.drawable.ic_note_a_sixteenth, 25L),
            note(R.raw.ableton_1_44_wav, R.raw.ableton_1_48_wav, R.string.click_1046,
                R.drawable.ic_note_c, R.drawable.ic_note_c_eighth, R.drawable.ic_note_c_sixteenth, 25L),
            note(R.raw.ableton_2_44_wav, R.raw.ableton_2_48_wav, R.string.click_880,
                R.drawable.ic_note_c_rim, R.drawable.ic_note_c_rim_eighth, R.drawable.ic_note_c_rim_sixteenth, 25L),
            note(R.raw.ableton_3_44_wav, R.raw.ableton_3_48_wav, R.string.click_1319,
                R.drawable.ic_note_ep, R.drawable.ic_note_ep_eighth, R.drawable.ic_note_ep_sixteenth, 25L),
            note(R.raw.ableton_4_44_wav, R.raw.ableton_4_48_wav, R.string.click_2093,
                R.drawable.ic_note_gp, R.drawable.ic_note_gp_eighth, R.drawable.ic_note_gp_sixteenth, 25L),
            note(R.raw.ableton_5_44_wav, R.raw.ableton_5_48_wav, R.string.click_698,
                R.drawable.ic_note_hihat, R.drawable.ic_note_hihat_eighth, R.drawable.ic_note_hihat_sixteenth, 25L),
            muteNote(),
            note(R.raw.ableton_6_44_wav, R.raw.ableton_6_48_wav, R.string.click_784,
                R.drawable.ic_note_extra, R.drawable.ic_note_extra_eighth, R.drawable.ic_note_extra_sixteenth, 25L),
        )
    )

    /// 808 style drum kit.
    val m808 = SoundSet(
        "m808", R.string.soundset_808, arrayOf(
            note(R.raw.s808_0_44_wav, R.raw.s808_0_48_wav, R.string.base,
                R.drawable.ic_note_a, R.drawable.ic_note_a_eighth, R.drawable.ic_note_a_sixteenth, 70L),
            note(R.raw.s808_1_44_wav, R.raw.s808_1_48_wav, R.string.snare,
                R.drawable.ic_note_c, R.drawable.ic_note_c_eighth, R.drawable.ic_note_c_sixteenth, 60L),
            note(R.raw.s808_2_44_wav, R.raw.s808_2_48_wav, R.string.hihat,
                R.drawable.ic_note_hihat, R.drawable.ic_note_hihat_eighth, R.drawable.ic_note_hihat_sixteenth, 30L),
            note(R.raw.s808_3_44_wav, R.raw.s808_3_48_wav, R.string.cowbell,
                R.drawable.ic_note_gp, R.drawable.ic_note_gp_eighth, R.drawable.ic_note_gp_sixteenth, 55L),
            note(R.raw.s808_4_44_wav, R.raw.s808_4_48_wav, R.string.clap,
                R.drawable.ic_note_c_rim, R.drawable.ic_note_c_rim_eighth, R.drawable.ic_note_c_rim_sixteenth, 55L),
            note(R.raw.s808_5_44_wav, R.raw.s808_5_48_wav, R.string.sticks,
                R.drawable.ic_note_ep, R.drawable.ic_note_ep_eighth, R.drawable.ic_note_ep_sixteenth, 30L),
            muteNote(),
            note(R.raw.s808_6_44_wav, R.raw.s808_6_48_wav, R.string.tom,
                R.drawable.ic_note_extra, R.drawable.ic_note_extra_eighth, R.drawable.ic_note_extra_sixteenth, 60L),
        )
    )

    /// iReal Pro like wooden clicks.
    val ireal = SoundSet(
        "ireal", R.string.soundset_ireal, arrayOf(
            note(R.raw.ireal_0_44_wav, R.raw.ireal_0_48_wav, R.string.wood_1180,
                R.drawable.ic_note_a, R.drawable.ic_note_a_eighth, R.drawable.ic_note_a_sixteenth, 35L),
            note(R.raw.ireal_1_44_wav, R.raw.ireal_1_48_wav, R.string.wood_900,
                R.drawable.ic_note_c, R.drawable.ic_note_c_eighth, R.drawable.ic_note_c_sixteenth, 35L),
            note(R.raw.ireal_2_44_wav, R.raw.ireal_2_48_wav, R.string.wood_720,
                R.drawable.ic_note_c_rim, R.drawable.ic_note_c_rim_eighth, R.drawable.ic_note_c_rim_sixteenth, 35L),
            note(R.raw.ireal_3_44_wav, R.raw.ireal_3_48_wav, R.string.wood_1520,
                R.drawable.ic_note_ep, R.drawable.ic_note_ep_eighth, R.drawable.ic_note_ep_sixteenth, 30L),
            note(R.raw.ireal_4_44_wav, R.raw.ireal_4_48_wav, R.string.shaker,
                R.drawable.ic_note_gp, R.drawable.ic_note_gp_eighth, R.drawable.ic_note_gp_sixteenth, 30L),
            note(R.raw.ireal_5_44_wav, R.raw.ireal_5_48_wav, R.string.wood_560,
                R.drawable.ic_note_hihat, R.drawable.ic_note_hihat_eighth, R.drawable.ic_note_hihat_sixteenth, 30L),
            muteNote(),
            note(R.raw.ireal_6_44_wav, R.raw.ireal_6_48_wav, R.string.wood_1000,
                R.drawable.ic_note_extra, R.drawable.ic_note_extra_eighth, R.drawable.ic_note_extra_sixteenth, 30L),
        )
    )

    /// Drum and bass kit.
    val dnb = SoundSet(
        "dnb", R.string.soundset_dnb, arrayOf(
            note(R.raw.dnb_0_44_wav, R.raw.dnb_0_48_wav, R.string.base,
                R.drawable.ic_note_a, R.drawable.ic_note_a_eighth, R.drawable.ic_note_a_sixteenth, 70L),
            note(R.raw.dnb_1_44_wav, R.raw.dnb_1_48_wav, R.string.snare,
                R.drawable.ic_note_c, R.drawable.ic_note_c_eighth, R.drawable.ic_note_c_sixteenth, 60L),
            note(R.raw.dnb_2_44_wav, R.raw.dnb_2_48_wav, R.string.hihat,
                R.drawable.ic_note_hihat, R.drawable.ic_note_hihat_eighth, R.drawable.ic_note_hihat_sixteenth, 25L),
            note(R.raw.dnb_3_44_wav, R.raw.dnb_3_48_wav, R.string.hat_open,
                R.drawable.ic_note_gp, R.drawable.ic_note_gp_eighth, R.drawable.ic_note_gp_sixteenth, 40L),
            note(R.raw.dnb_4_44_wav, R.raw.dnb_4_48_wav, R.string.sticks,
                R.drawable.ic_note_c_rim, R.drawable.ic_note_c_rim_eighth, R.drawable.ic_note_c_rim_sixteenth, 30L),
            note(R.raw.dnb_5_44_wav, R.raw.dnb_5_48_wav, R.string.shaker,
                R.drawable.ic_note_ep, R.drawable.ic_note_ep_eighth, R.drawable.ic_note_ep_sixteenth, 35L),
            muteNote(),
            note(R.raw.dnb_6_44_wav, R.raw.dnb_6_48_wav, R.string.tom,
                R.drawable.ic_note_extra, R.drawable.ic_note_extra_eighth, R.drawable.ic_note_extra_sixteenth, 55L),
        )
    )

    /// Techno kit (909 like).
    val techno = SoundSet(
        "techno", R.string.soundset_techno, arrayOf(
            note(R.raw.techno_0_44_wav, R.raw.techno_0_48_wav, R.string.base,
                R.drawable.ic_note_a, R.drawable.ic_note_a_eighth, R.drawable.ic_note_a_sixteenth, 70L),
            note(R.raw.techno_1_44_wav, R.raw.techno_1_48_wav, R.string.snare,
                R.drawable.ic_note_c, R.drawable.ic_note_c_eighth, R.drawable.ic_note_c_sixteenth, 60L),
            note(R.raw.techno_2_44_wav, R.raw.techno_2_48_wav, R.string.hihat,
                R.drawable.ic_note_hihat, R.drawable.ic_note_hihat_eighth, R.drawable.ic_note_hihat_sixteenth, 25L),
            note(R.raw.techno_3_44_wav, R.raw.techno_3_48_wav, R.string.hat_open,
                R.drawable.ic_note_gp, R.drawable.ic_note_gp_eighth, R.drawable.ic_note_gp_sixteenth, 40L),
            note(R.raw.techno_4_44_wav, R.raw.techno_4_48_wav, R.string.clap,
                R.drawable.ic_note_c_rim, R.drawable.ic_note_c_rim_eighth, R.drawable.ic_note_c_rim_sixteenth, 50L),
            note(R.raw.techno_5_44_wav, R.raw.techno_5_48_wav, R.string.sticks,
                R.drawable.ic_note_ep, R.drawable.ic_note_ep_eighth, R.drawable.ic_note_ep_sixteenth, 30L),
            muteNote(),
            note(R.raw.techno_6_44_wav, R.raw.techno_6_48_wav, R.string.ride,
                R.drawable.ic_note_extra, R.drawable.ic_note_extra_eighth, R.drawable.ic_note_extra_sixteenth, 35L),
        )
    )

    /// Rock kit.
    val rock = SoundSet(
        "rock", R.string.soundset_rock, arrayOf(
            note(R.raw.rock_0_44_wav, R.raw.rock_0_48_wav, R.string.base,
                R.drawable.ic_note_a, R.drawable.ic_note_a_eighth, R.drawable.ic_note_a_sixteenth, 70L),
            note(R.raw.rock_1_44_wav, R.raw.rock_1_48_wav, R.string.snare,
                R.drawable.ic_note_c, R.drawable.ic_note_c_eighth, R.drawable.ic_note_c_sixteenth, 65L),
            note(R.raw.rock_2_44_wav, R.raw.rock_2_48_wav, R.string.hihat,
                R.drawable.ic_note_hihat, R.drawable.ic_note_hihat_eighth, R.drawable.ic_note_hihat_sixteenth, 25L),
            note(R.raw.rock_3_44_wav, R.raw.rock_3_48_wav, R.string.hat_open,
                R.drawable.ic_note_gp, R.drawable.ic_note_gp_eighth, R.drawable.ic_note_gp_sixteenth, 40L),
            note(R.raw.rock_4_44_wav, R.raw.rock_4_48_wav, R.string.tom,
                R.drawable.ic_note_c_rim, R.drawable.ic_note_c_rim_eighth, R.drawable.ic_note_c_rim_sixteenth, 60L),
            note(R.raw.rock_5_44_wav, R.raw.rock_5_48_wav, R.string.ride,
                R.drawable.ic_note_ep, R.drawable.ic_note_ep_eighth, R.drawable.ic_note_ep_sixteenth, 35L),
            muteNote(),
            note(R.raw.rock_6_44_wav, R.raw.rock_6_48_wav, R.string.crash,
                R.drawable.ic_note_extra, R.drawable.ic_note_extra_eighth, R.drawable.ic_note_extra_sixteenth, 40L),
        )
    )
    /// All sound sets in display order.
    val all: List<SoundSet> = listOf(default, ableton, m808, ireal, dnb, techno, rock)

    fun byId(id: String?): SoundSet = all.firstOrNull { it.id == id } ?: default
}

/** Holds the currently active sound set and notifies interested parts (e.g. the audio mixer) when
 *  it changes. The selected sound set is persisted in the default shared preferences. */
object SoundSetManager {
    private const val PREFERENCE_KEY = "soundset"

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var currentId: String = SoundSets.default.id
        private set

    val current: SoundSet
        get() = SoundSets.byId(currentId)

    /// Read the saved sound set from the preferences. Call this once on application start.
    fun init(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        currentId = SoundSets.byId(preferences.getString(PREFERENCE_KEY, SoundSets.default.id)).id
        availableNotes = current.notes
    }

    /// Select a sound set and notify all registered listeners.
    fun select(context: Context, id: String) {
        val soundSet = SoundSets.byId(id)
        val changed = soundSet.id != currentId
        currentId = soundSet.id
        availableNotes = soundSet.notes
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit().putString(PREFERENCE_KEY, soundSet.id).apply()
        if (changed)
            listeners.forEach { it() }
    }

    /// Listener which is called when the sound set changes (e.g. to reload the note samples).
    fun addChangeListener(listener: () -> Unit) {
        listeners.addIfAbsent(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}
