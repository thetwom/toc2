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

package de.moekadu.metronome

import android.content.Context
import android.media.*
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import java.lang.RuntimeException
import kotlin.math.*

class AudioMixer (val context: Context) {
    companion object {
        fun createNoteSamples(context: Context, sampleRate: Int) : Array<FloatArray> {
            return Array(getNumAvailableNotes()) {
                // i -> audioToPCM(audioResourceIds[i], context)
                i -> waveToPCM(getNoteAudioResourceID(i, sampleRate), context)
            }
        }
    }

    /// Period in audio frames when we ask for new data in the audio buffer
    /**
     * We take this period to be half the time of the maximum latency, we copy the first half to
     * the AudioTrack and prepare the half to be ready writing it in time.
     */
    private var audioBufferUpdatePeriod = 0

    /// The playing audio track itself
    private var player : AudioTrack? = null

    /// Current sample rat which is used
    private var sampleRate = 0
        set(value) {
            if (value != field)
                noteSamples = createNoteSamples(context, value)
            field = value
        }

    private var minBufferSize = 0

    /// These are the samples for all available notes which we can play, samples are stored as as FloatArrays
    /** @note This is automatically set, when setting the sample rate */
    private var noteSamples = Array(0) { FloatArray(0)}

    /// Class which stores tracks which are queued for the playing
    /**
     * @param nodeId Note index in #availableNotes
     * @param nextSampleToMix Index of next sample inside the audio file which goes into our mixer
     * @param startDelay wait this number of frames until passing the track to our mixer
     * @param volume Track volume
     *   starts in this many frames.
     */
    class QueuedNotes(var nodeId : Int = 0, var nextSampleToMix : Int = 0, var startDelay : Int = 0, var volume : Float = 0f)

    /// List of tracks which are currently queued for playing.
    private val queuedNotes = InfiniteCircularBuffer(32) {QueuedNotes()}

    /// Total number of frames for which we queued track for playing. Is zeroed when player starts.
    private var queuedFrames = 0

    /// Mixing buffer where we mix our audio
    private var mixingBuffer = FloatArray(0)

    ///  Note list with tracks which are played in a loop
    var noteList : NoteList? = null

    /// Index of next playlist item which will be queued for playing
    private var nextNoteListIndex = 0

    /// Frame when next note list item starts playing
    private var nextNoteFrame = 0

    /// Required information for handling the notifications when a playlist item starts
    /**
     * @param frameWhenNoteListItemStarts Frame when we have to notify that the
     *   playlist item starts
     * @param noteListItem Object which is passed to the callback
     *   when the playlist item starts playing.
     */
    class MarkerPositionAndNote (var frameWhenNoteListItemStarts : Int = 0,
                                 var noteListItem : NoteListItem? = null)

    /// Markers where we call a listener
    private val markers = InfiniteCircularBuffer(32) { MarkerPositionAndNote() }

    /// Interface for listener which is used when a new note list item starts
    interface NoteStartedListener {
        /// Callback function which is called when a playlist item starts
        /**
         * @param noteListItem note list item which is started.
         */
        fun onNoteStarted(noteListItem: NoteListItem?)
    }

    /// Callback when a track starts
    var noteStartedListener : NoteStartedListener ?= null

    /// Variable which tells us if our player is running.
    private var isPlaying = false

    init {
        updateInternalStructures()
    }

    private fun updateInternalStructures() {
        val nativeSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        val minBufferSize = AudioTrack.getMinBufferSize(nativeSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)

        if (nativeSampleRate == sampleRate && minBufferSize == this.minBufferSize)
            return
//        Log.v("Metronome", "AudioMixer.updateInternalStructures: recreating AudioTrack")
        player?.let { audioTrack ->
            player = null
            audioTrack.pause()
            audioTrack.flush()
            audioTrack.release()
        }

        val savedNextNoteListIndex = if (isPlaying) nextNoteListIndex else 0

        sampleRate = nativeSampleRate // this will also reset the noteSamples if needed
        this.minBufferSize = minBufferSize
        val bufferSize = 2 * minBufferSize
        audioBufferUpdatePeriod = floor(bufferSize / 4f / 2.0f).toInt()
        mixingBuffer = FloatArray(audioBufferUpdatePeriod)

        player = AudioTrack.Builder()
                .setAudioAttributes(
                        AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                )
                .setAudioFormat(
                        AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

        player?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {

            override fun onMarkerReached(track: AudioTrack?) {
//                Log.v("AudioMixer", "AudioMixer: onMarkerReached, headPos=${track?.playbackHeadPosition}")
                val markerAndPosition = markers.pop()
//                Log.v("AudioMixer", "AudioMixer: onMarkerReached, nextMarker=${markerAndPosition.nextTrackPosition}")
                noteStartedListener?.onNoteStarted(markerAndPosition.noteListItem)

                if (markers.size > 0) {
                    val nextMarker = markers.first()
                    track?.notificationMarkerPosition = nextMarker.frameWhenNoteListItemStarts
                }
            }

            override fun onPeriodicNotification(track: AudioTrack?) {
//                Log.v("AudioMixer", "AudioMixer: onPeriodicNotification")
                if (track != null) {
                    queueNextNotes()
                    mixAndPlayQueuedNotes(track)
                }
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            player?.addOnRoutingChangedListener({
                Log.v("Metronome", "AudioMixer: On routing changed")
                updateInternalStructures()
            }, null)

        if (isPlaying)
            start(savedNextNoteListIndex)
    }

    /// Start playing
    fun start(firstNoteListIndex: Int = 0) {
        require(noteList?.isNotEmpty() ?: false) {"Note list must not be empty"}
        stop()

        updateInternalStructures()

        player?.playbackHeadPosition = 0
        markers.clear()

        queuedNotes.clear()
        queuedFrames = 0

        // lets add a delay for the first note to play to avoid playing artifacts
        nextNoteFrame = audioBufferUpdatePeriod
        nextNoteListIndex = firstNoteListIndex

        player?.flush()

        // Log.v("AudioMixer", "AudioMixer: start")
        // Log.v("AudioMixer", "AudioMixer:start : minimumBufferSize=$minBufferSize , periodicBaseSize: ${4 * 2 * audioBufferUpdatePeriod}")
        player?.play()

        player?.positionNotificationPeriod = audioBufferUpdatePeriod

        // Log.v("AudioMixer", "AudioMixer:start, positionPeriod = $audioBufferUpdatePeriod")

        // queue the track which start playing during the first audioBufferUpdatePeriod frames and play them
        // since the first periodic update is not at frame zero , we have to queue the next tracks already here
        for(i in 0 .. 1) {
            queueNextNotes()
            player?.let {mixAndPlayQueuedNotes(it)}
        }

        // Log.v("AudioMixer", "AudioMixer: start, first marker = ${player.notificationMarkerPosition}")
        isPlaying = true
    }

    /// Stop playing
    fun stop() {
        player?.pause()
        player?.flush()
        isPlaying = false
    }

    /// Synchronize first beat to note list to given time and beat duration
    /**
     * @param referenceTime Time in uptime millis (from call to SystemClock.uptimeMillis()
     *   to which the first beat should be synchronized
     * @param beatDuration Duration in seconds for a beat. The playing is then synchronized such,
     *   that the first beat of the playlist is played at
     *      referenceTime + n * beatDuration
     *   where n is a integer number.
     */
    fun synchronizeTime(referenceTime : Long, beatDuration : Float) {
        if (noteList?.size == 0)
            return

        player?.let { audioTrack ->
            noteList?.let { notes ->
                val currentTimeMillis = SystemClock.uptimeMillis()
                val currentTimeInFrames = audioTrack.playbackHeadPosition
                val referenceTimeInFrames = currentTimeInFrames + (referenceTime - currentTimeMillis).toInt() * audioTrack.sampleRate / 1000
                val beatDurationInFrames = (beatDuration * audioTrack.sampleRate).roundToInt()

                if (nextNoteListIndex >= notes.size)
                    nextNoteListIndex = 0

                var referenceTimeForNextNoteListItem = referenceTimeInFrames
                for (i in 0 until nextNoteListIndex)
                    referenceTimeForNextNoteListItem += (max(0f, notes[i].duration) * audioTrack.sampleRate).roundToInt()

                // remove multiples of beat duration from our reference, so that it is always smaller than the nextTrackFrame
                if (referenceTimeForNextNoteListItem > 0)
                    referenceTimeForNextNoteListItem -= (referenceTimeForNextNoteListItem / beatDurationInFrames) * (beatDurationInFrames + 1)
                require(referenceTimeForNextNoteListItem <= nextNoteFrame)

                val correctedNextFrameIndex = (referenceTimeForNextNoteListItem +
                        ((nextNoteFrame - referenceTimeForNextNoteListItem).toFloat()
                                / beatDurationInFrames).roundToInt()
                        * beatDurationInFrames)
                // Log.v("AudioMixer", "AudioMixer.synchronizeTime : correctedNextFrame=$correctedNextFrameIndex, nextTrackFrame=$nextTrackFrame")
                nextNoteFrame = correctedNextFrameIndex
            }
        }
    }

    private fun queueNextNotes() {
        if (noteList?.size == 0)
            return

//        Log.v("AudioMixer", "AudioMixer:queueNextTracks")
        player?.let { audioTrack ->
            noteList?.let { notes ->
                while (nextNoteFrame < queuedFrames + audioBufferUpdatePeriod) {
                    if (nextNoteListIndex >= notes.size)
                        nextNoteListIndex = 0
//            Log.v("AudioMixer", "AudioMixer:queueNextTracks nextPlaylistIndex=$nextPlaylistIndex")
                    val noteListItem = notes[nextNoteListIndex]

                    val queuedNote = queuedNotes.add()
                    queuedNote.nodeId = noteListItem.id
                    queuedNote.startDelay = max(0, nextNoteFrame - queuedFrames)
                    queuedNote.nextSampleToMix = 0
                    queuedNote.volume = noteListItem.volume

                    val queuedNoteMarker = markers.add()
                    queuedNoteMarker.frameWhenNoteListItemStarts = nextNoteFrame
                    queuedNoteMarker.noteListItem = noteListItem

                    if (markers.size == 1)
                        audioTrack.notificationMarkerPosition = queuedNoteMarker.frameWhenNoteListItemStarts

                    // notes can have a duration of -1 if it is not yet set ... in this case we pretend directly play the next note
                    nextNoteFrame += (max(0f, noteListItem.duration) * audioTrack.sampleRate).roundToInt()
                    ++nextNoteListIndex
                }
                queuedFrames += audioBufferUpdatePeriod
            }
        }
    }

    private fun mixAndPlayQueuedNotes(track: AudioTrack) {
//        Log.v("AudioMixer", "AudioMixer:mixAndQueueTracks")

        // Do note play if our player was destroyed
        if (!(track === player))
            return

        mixingBuffer.fill(0.0f)

        for (i in queuedNotes.indexStart until queuedNotes.indexEnd) {

            val queuedItem = queuedNotes[i]

            val noteId = queuedItem.nodeId
            val sampleStart = queuedItem.nextSampleToMix
            val startDelay = queuedItem.startDelay
            val volume = queuedItem.volume
//            Log.v("AudioMixer", "AudioMixer:mixAndQueueTracks : iTrack = $i, trackIndex=$trackIndex, startDelay=$startDelay")
            val samples = noteSamples[noteId]

            val numSamplesToWrite = min(samples.size - sampleStart, audioBufferUpdatePeriod - startDelay)
            val sampleEnd = sampleStart + numSamplesToWrite
//            Log.v("AudioMixer", "AudioMixer:mixAndQueueTracks : sampleStart=$sampleStart, sampleEnd=$sampleEnd, sampleSize=${trackSamples.size}")

            var j = startDelay
            for(k in sampleStart until sampleEnd) {
                mixingBuffer[j] = mixingBuffer[j] + volume * samples[k]
                ++j
            }

            queuedItem.startDelay = 0
            queuedItem.nextSampleToMix = sampleEnd
        }

        while(queuedNotes.size > 0) {
            val queuedItem = queuedNotes.first()
            val numSamples = noteSamples[queuedItem.nodeId].size
            if (queuedItem.nextSampleToMix >= numSamples)
                queuedNotes.pop()
            else
                break
        }
        val numWrite = track.write(mixingBuffer, 0, mixingBuffer.size, AudioTrack.WRITE_NON_BLOCKING)
//        Log.v("AudioMixer", "AudioMixer:mixAndQueueTracks : wrote $numWrite to audioTrack")
        if(numWrite != mixingBuffer.size) {
            Log.d("Metronome", "Could not write all audio samples to audio buffer.")
            // throw RuntimeException("Nonblocking write of ${mixingBuffer.size} samples to AudioTrack not possible")
        }
    }
}

