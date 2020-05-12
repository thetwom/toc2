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
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import java.lang.RuntimeException
import kotlin.math.*

class AudioMixer (availableTrackResources : IntArray, context: Context, maximumLatency : Float = 250.0f) {
    companion object {
        fun createAvailableTracks(availableTrackResources: IntArray, context: Context) : Array<FloatArray> {
            return Array(availableTrackResources.size) {
                i -> audioToPCM(availableTrackResources[i], context)
            }
        }

//        fun getMaximumTrackLength(tracks : Array<FloatArray>) : Int {
//            var maxLength = 0
//            for (t in tracks)
//                maxLength = max(maxLength, t.size)
//            return maxLength
//        }
    }


    /// Device sample rate
    private val nativeSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)

    /// Minimum buffer size of our AudioTrack in bytes
    private val minBufferSize = AudioTrack.getMinBufferSize(nativeSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)

    /// Maximum latency measured in frames instead of milliseconds
    private val maximumLatencyInFrames = (maximumLatency * nativeSampleRate / 1000.0f).roundToInt()

    /// Period in audio frames when we ask for new data in the audio buffer
    /**
     * We take this period to be half the time of the maximum latency, we copy the first half to
     * the AudioTrack and prepare the half to be ready writing it in time.
     */
    private val audioBufferUpdatePeriod = floor(maximumLatencyInFrames / 2.0f).toInt()

    /// Buffer size of Audio Track in bytes (the factor for is because the size of 1 float is 4 bytes)
    private val audioTrackBufferSize = max(minBufferSize, 4 * 2 * audioBufferUpdatePeriod)

    /// The playing audio track itself
    private val player = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(nativeSampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(audioTrackBufferSize)
        .build()

    /// These are all available tracks which we can play, samples are stored as as FloatArrays
    private val availableTracks = createAvailableTracks(availableTrackResources, context)

    /// Class which stores tracks which are queued for the playing
    /**
     * @param trackIndex Track index in #availableTracks
     * @param nextSampleToMix Next track sample list which goes into our mixer
     * @param startDelay wait this number of frames until passing the track to our mixer
     * @param volume Track volume
     *   starts in this many frames.
     */
    class QueuedTracks(var trackIndex : Int = 0, var nextSampleToMix : Int = 0, var startDelay : Int = 0, var volume : Float = 0f)

    /// List of tracks which are currently queued for playing.
    private val queuedTracks = InfiniteCircularBuffer(32) {QueuedTracks()}

    /// Total number of frames for which we queued track for playing. Is zeroed when player starts.
    private var queuedFrames = 0

    /// Mixing buffer where we mix our audio
    private val mixingBuffer = FloatArray(audioBufferUpdatePeriod)

    /// Item in the playlist.
    /**
     * @param trackIndex Track index in #availableTracks
     * @param volume Track volume
     * @param duration Time in seconds until the next track starts playing
     * @param objectReference Some reference which is passed to the callback function, when this
     *   item starts playing.
     */
    class PlayListItem (var trackIndex : Int, var volume : Float, var duration : Float, var objectReference : Any?) {
        fun clone() : PlayListItem {
            return PlayListItem(trackIndex, volume, duration, objectReference)
        }
        fun set(value : PlayListItem) {
            trackIndex = value.trackIndex
            volume = value.volume
            duration = value.duration
            objectReference = value.objectReference
        }
    }

    /// Playlist with tracks which are played in a loop
    var playList = Array(0) {PlayListItem(0, 0f, 0f, null)}
        set(newPlayList) {
            require(newPlayList.isNotEmpty()) {"The play list size must be at least 1"}
            if (field.size == newPlayList.size) {
                for(i in field.indices)
                    field[i].set(newPlayList[i])
            }
            else {
                field = Array(newPlayList.size) { i -> newPlayList[i].clone() }
            }
        }

    /// Index of next playlist item which will be queued for playing
    private var nextPlaylistIndex = 0

    /// Frame when next playlist item starts playing
    private var nextTrackFrame = 0

    /// Required information for handling the notifications when a playlist item starts
    /**
     * @param frameWhenPlaylistItemStarts Frame when we have to notify that the
     *   playlist item starts
     * @param objectReferenceOfPlaylistItem Object reference which is passed to the callback
     *   when the playlist item starts playing.
     */
    class MarkerPositionAndObject (var frameWhenPlaylistItemStarts : Int = 0,
                                   var objectReferenceOfPlaylistItem : Any? = null)

    /// Markers where we call a listener
    private val markers = InfiniteCircularBuffer(32) { MarkerPositionAndObject() }

    /// Interface for listener which is used when a new playlist item starts
    interface TrackStartedListener {
        /// Callback function which is called when a playlist item starts
        /**
         * @param objectReference reference which is stored within the playlist item.
         */
        fun onTrackStarted(objectReference: Any?)
    }

    /// Callback when a track starts
    private var trackStartedListener : TrackStartedListener ?= null

    /// Set listener which is called, when a track starts
    fun setTrackStartedListener(trackStartedListener: TrackStartedListener?) {
        this.trackStartedListener = trackStartedListener
    }

    /// Variable which tells us if our player is running.
    private var isPlaying = false

    /// Start playing
    fun start() {
        require(playList.isNotEmpty()) {"Playlist must not be empty"}

        player.playbackHeadPosition = 0
        markers.clear()

        queuedTracks.clear()
        queuedFrames = 0

        // lets add a delay for the first track to play to avoid playing artifacts
        nextTrackFrame = audioBufferUpdatePeriod
        nextPlaylistIndex = 0

        player.flush()

        // Log.v("AudioMixer", "AudioMixer: start")
        // Log.v("AudioMixer", "AudioMixer:start : minimumBufferSize=$minBufferSize , periodicBaseSize: ${4 * 2 * audioBufferUpdatePeriod}")
        player.play()

        player.positionNotificationPeriod = audioBufferUpdatePeriod

        // Log.v("AudioMixer", "AudioMixer:start, positionPeriod = $audioBufferUpdatePeriod")

        // queue the track which start playing during the first audioBufferUpdatePeriod frames and play them
        // since the first periodic update is not at frame zero , we have to queue the next tracks already here
        for(i in 0 .. 1) {
            queueNextTracks()
            mixAndPlayQueuedTracks()
        }

        // Log.v("AudioMixer", "AudioMixer: start, first marker = ${player.notificationMarkerPosition}")

        isPlaying = true
    }

    /// Stop playing
    fun stop() {
        player.pause()
        player.flush()
        isPlaying = false
    }

    /// Synchronize first beat to playlist to given time and beat duration
    /**
     * @param referenceTime Time in uptime millis (from call to SystemClock.uptimeMillis()
     *   to which the first beat should be synchronized
     * @param beatDuration Duration in seconds for a beat. The playing is then synchronized such,
     *   that the first beat of the playlist is played at
     *      referenceTime + n * beatDuration
     *   where n is a integer number.
     */
    fun synchronizeTime(referenceTime : Long, beatDuration : Float) {

        val currentTimeMillis  = SystemClock.uptimeMillis()
        val currentTimeInFrames = player.playbackHeadPosition
        val referenceTimeInFrames = currentTimeInFrames + (referenceTime - currentTimeMillis).toInt() * nativeSampleRate / 1000
        val beatDurationInFrames = (beatDuration * nativeSampleRate).roundToInt()

        if(nextPlaylistIndex >= playList.size)
            nextPlaylistIndex = 0

        var referenceTimeForNextPlaylistItem = referenceTimeInFrames
        for (i in 0 until nextPlaylistIndex)
            referenceTimeForNextPlaylistItem += (playList[i].duration * nativeSampleRate).roundToInt()

        // remove multiples of beat duration from our reference, so that it is always smaller than the nextTrackFrame
        if(referenceTimeForNextPlaylistItem > 0)
            referenceTimeForNextPlaylistItem -= (referenceTimeForNextPlaylistItem / beatDurationInFrames) * (beatDurationInFrames + 1)
        require(referenceTimeForNextPlaylistItem <= nextTrackFrame)

        val correctedNextFrameIndex = (referenceTimeForNextPlaylistItem +
                ((nextTrackFrame - referenceTimeForNextPlaylistItem).toFloat()
                        / beatDurationInFrames).roundToInt()
                * beatDurationInFrames)
        // Log.v("AudioMixer", "AudioMixer.synchronizeTime : correctedNextFrame=$correctedNextFrameIndex, nextTrackFrame=$nextTrackFrame")
        nextTrackFrame = correctedNextFrameIndex
    }


    init {

       // Log.v("AudioMixer", "AudioMixer: audioBufferUpdatePeriod = $audioBufferUpdatePeriod")
       // Log.v("AudioMixer", "AudioMixer: minBufferSize = $minBufferSize")
       // Log.v("AudioMixer", "AudioMixer: maxTrackSize = ${getMaximumTrackLength(availableTracks)}")
       // Log.v("AudioMixer", "AudioMixer: bufferSize = $audioTrackBufferSize")

        player.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {

            override fun onMarkerReached(track: AudioTrack?) {
//                Log.v("AudioMixer", "AudioMixer: onMarkerReached, headPos=${track?.playbackHeadPosition}")
                val markerAndPosition = markers.pop()
//                Log.v("AudioMixer", "AudioMixer: onMarkerReached, nextMarker=${markerAndPosition.nextTrackPosition}")
                val objectReference = markerAndPosition.objectReferenceOfPlaylistItem
                trackStartedListener?.onTrackStarted(objectReference)

                if(markers.size > 0) {
                    val nextMarker = markers.first()
                    track?.notificationMarkerPosition = nextMarker.frameWhenPlaylistItemStarts
                }
            }

            override fun onPeriodicNotification(track: AudioTrack?) {
//                Log.v("AudioMixer", "AudioMixer: onPeriodicNotification")
                if(track != null) {
                    queueNextTracks()
                    mixAndPlayQueuedTracks()
                }
            }
        })
    }

    private fun queueNextTracks() {
//        Log.v("AudioMixer", "AudioMixer:queueNextTracks")
        while (nextTrackFrame < queuedFrames + audioBufferUpdatePeriod) {
            if (nextPlaylistIndex >= playList.size)
                nextPlaylistIndex = 0
//            Log.v("AudioMixer", "AudioMixer:queueNextTracks nextPlaylistIndex=$nextPlaylistIndex")
            val track = playList[nextPlaylistIndex]

            val queueItem = queuedTracks.add()
            queueItem.trackIndex = track.trackIndex
            queueItem.startDelay = max(0, nextTrackFrame - queuedFrames)
            queueItem.nextSampleToMix = 0
            queueItem.volume = track.volume

            nextTrackFrame += (track.duration * nativeSampleRate).roundToInt()

            val nextMarker = markers.add()
            nextMarker.frameWhenPlaylistItemStarts = nextTrackFrame
            nextMarker.objectReferenceOfPlaylistItem = track.objectReference

            if( markers.size == 1)
                player.notificationMarkerPosition = queuedFrames + queueItem.startDelay

            ++nextPlaylistIndex
        }
        queuedFrames += audioBufferUpdatePeriod
    }

    private fun mixAndPlayQueuedTracks() {
//        Log.v("AudioMixer", "AudioMixer:mixAndQueueTracks")
        mixingBuffer.fill(0.0f)

        for (i in queuedTracks.indexStart until queuedTracks.indexEnd) {

            val queuedItem = queuedTracks[i]

            val trackIndex = queuedItem.trackIndex
            val sampleStart = queuedItem.nextSampleToMix
            val startDelay = queuedItem.startDelay
            val volume = queuedItem.volume
//            Log.v("AudioMixer", "AudioMixer:mixAndQueueTracks : iTrack = $i, trackIndex=$trackIndex, startDelay=$startDelay")
            val trackSamples = availableTracks[trackIndex]

            val numSamplesToWrite = min(trackSamples.size - sampleStart, audioBufferUpdatePeriod - startDelay)
            val sampleEnd = sampleStart + numSamplesToWrite
//            Log.v("AudioMixer", "AudioMixer:mixAndQueueTracks : sampleStart=$sampleStart, sampleEnd=$sampleEnd, sampleSize=${trackSamples.size}")

            var j = startDelay
            for(k in sampleStart until sampleEnd) {
                mixingBuffer[j] = mixingBuffer[j] + volume * trackSamples[k]
                ++j
            }

            queuedItem.startDelay = 0
            queuedItem.nextSampleToMix = sampleEnd
        }

        while(queuedTracks.size > 0) {
            val queuedItem = queuedTracks.first()
            val numSamples = availableTracks[queuedItem.trackIndex].size
            if (queuedItem.nextSampleToMix >= numSamples)
                queuedTracks.pop()
            else
                break
        }
        val numWrite = player.write(mixingBuffer, 0, mixingBuffer.size, AudioTrack.WRITE_NON_BLOCKING)
//        Log.v("AudioMixer", "AudioMixer:mixAndQueueTracks : wrote $numWrite to audioTrack")
        if(numWrite != mixingBuffer.size)
            throw RuntimeException("Nonblocking write of ${mixingBuffer.size} samples to AudioTrack not possible")
    }
}

