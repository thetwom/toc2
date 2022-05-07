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

package de.moekadu.metronome.players

import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import androidx.lifecycle.*
import de.moekadu.metronome.metronomeproperties.getNoteAudioResourceID

class SingleNotePlayer(context: Context, private val lifecycleOwner: LifecycleOwner): LifecycleEventObserver {
    /// Sound pool which is used for playing sample sounds when selected in the sound chooser.
    private val soundPool = SoundPool.Builder().setMaxStreams(3).build().apply {
        setOnLoadCompleteListener { soundPool, sampleId, _ ->
            soundPool?.play(sampleId, cachedVolume, cachedVolume, 1, 0, 1f)
        }
    }
    /// Handles of the available sound used by the sound pool for 44100Hz sample rate.
    private val soundHandles44 = mutableMapOf<Int, Int>()
    /// Handles of the available sound used by the sound pool for 48000Hz sample rate.
    private val soundHandles48 = mutableMapOf<Int, Int>()

    private val applicationContext = context.applicationContext

    private var cachedVolume = 1.0f

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY)
            clear()
    }

    fun play(noteId: Int, volume:Float) {
        val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        val soundHandle = getSoundHandle(noteId, sampleRate)
//        Log.v("Metronome", "SingleNotePlayer.play: soundHandle=$soundHandle")
        if (soundHandle != null)
            soundPool.play(soundHandle, volume, volume, 1, 0, 1f)
        else
            loadAndPlayNote(noteId, volume, sampleRate)
    }

    private fun loadAndPlayNote(noteId: Int, volume: Float, sampleRate: Int) {
        cachedVolume = volume
        if (sampleRate == 44100) {
            val soundId = getNoteAudioResourceID(noteId, 44100)
            soundHandles44[noteId] = soundPool.load(applicationContext, soundId, 1)
        } else { //  (sampleRate == 48000)
            val soundId = getNoteAudioResourceID(noteId, 48000)
            soundHandles48[noteId] = soundPool.load(applicationContext, soundId, 1)
        }
    }

    private fun getSoundHandle(noteId: Int, sampleRate: Int): Int? {
        return if (sampleRate == 44100) {
            soundHandles44[noteId]
        } else {
            soundHandles48[noteId]
        }
    }

    fun clear() {
        lifecycleOwner.lifecycle.removeObserver(this)
        for (sH in soundHandles44)
            soundPool.unload(sH.value)
        for (sH in soundHandles48)
            soundPool.unload(sH.value)
        soundHandles44.clear()
        soundHandles48.clear()
    }
}