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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/// Required information for handling the notifications when a playlist item starts
/**
 * @param frameWhenNoteListItemStarts Frame when we have to notify that the
 *   playlist item starts
 * @param noteListItem Object which is passed to the callback
 *   when the playlist item starts playing.
 */
private class MarkerPositionAndNote (var frameWhenNoteListItemStarts : Int = 0,
                                     var noteListItem : NoteListItem? = null)

/// Class which stores tracks which are queued for the playing
/**
 * @param nodeId Note index in #availableNotes
 * @param nextSampleToMix Index of next sample inside the audio file which goes into our mixer
 * @param startDelay wait this number of frames until passing the track to our mixer
 * @param volume Track volume
 *   starts in this many frames.
 */
data class QueuedNotes(var nodeId : Int = 0, var nextSampleToMix : Int = 0, var startDelay : Int = 0, var volume : Float = 0f)

private data class NextNoteInfo(val nextNoteIndex: Int, val nextNoteFrame: Int)

/** Class containing info for synchronize click time to a given reference.
* @param referenceTime Time in uptime millis (from call to SystemClock.uptimeMillis()
*   to which the first beat should be synchronized
* @param beatDuration Duration in seconds for a beat. The playing is then synchronized such,
*   that the first beat of the playlist is played at
*      referenceTime + n * beatDuration
*   where n is a integer number.
*/
private data class SynchronizeTimeInfo(val referenceTime: Long, val beatDuration: Float)

/// We use not the minimum buffer size but scale it with this integer value.
private const val minBufferSizeFactor = 2

/// Check if we need to recreate a player since the audio sink properties changed.
/**
 * @param sampleRate Currently used sample rate
 * @param bufferSizeInFrames Currently used buffer sie in frames (we assume, that PCM_FLOAT is used)
 * @return True, if we should recreate the player
 */
private fun audioRoutingChangeRequiresNewPlayer(sampleRate: Int, bufferSizeInFrames:Int) : Boolean {
    val nativeSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
    val bufferSizeInBytes = minBufferSizeFactor * AudioTrack.getMinBufferSize(nativeSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
//    Log.v("Metronome", "audioRoutingChangesRequiresNewPlayer: nativeSampleRate=$nativeSampleRate, currentSampleRate=${sampleRate}, bufferSize=$bufferSizeInBytes, currentBufferSize=${4*bufferSizeInFrames}")
    return (sampleRate != nativeSampleRate || bufferSizeInBytes != 4 * bufferSizeInFrames)
}

/// Create a new audio track instance.
/**
 * @return Audio track instance.
 */
private fun createPlayer(): AudioTrack {
    val nativeSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
    val bufferSize = minBufferSizeFactor * AudioTrack.getMinBufferSize(nativeSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)

    return AudioTrack.Builder()
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
            .setBufferSizeInBytes(bufferSize)
            .build()
}

/// Put notes in to queue, which will be played.
/**
 * @param nextNoteInfo Info about the next note, that will be put into the queue.
 * @param noteList Contains all notes to be played
 * @param alreadyQueuedFrames Frame number up to which we did already queued the notes.
 * @param numFramesToQueue Number of frames after alreadyQueuedFrames, for which we should queue the notes.
 * @param sampleRate Currently used sample rate
 * @param queuedNotes This is the queue where we add our notes.
 * @return An updated nextNoteInfo which serves as input to this function on the next cycle.
 */
private suspend fun queueNextNotes(nextNoteInfo: NextNoteInfo,
                                   noteList: NoteList, alreadyQueuedFrames: Int, numFramesToQueue: Int,
                                   markers: SendChannel<MarkerPositionAndNote>,
                                   sampleRate: Int,
                                   queuedNotes: InfiniteCircularBuffer<QueuedNotes>) : NextNoteInfo{
    require(noteList.isNotEmpty())
    var nextNoteIndex = nextNoteInfo.nextNoteIndex
    var nextNoteFrame = nextNoteInfo.nextNoteFrame

    while (nextNoteFrame < alreadyQueuedFrames + numFramesToQueue) {
        if (nextNoteIndex >= noteList.size)
            nextNoteIndex = 0

        val noteListItem = noteList[nextNoteIndex]

        val queuedNote = queuedNotes.add()
        queuedNote.nodeId = noteListItem.id
        queuedNote.startDelay = max(0, nextNoteFrame - alreadyQueuedFrames)
        queuedNote.nextSampleToMix = 0
        queuedNote.volume = noteListItem.volume

        markers.send(MarkerPositionAndNote(nextNoteFrame, noteListItem))

        // notes can have a duration of -1 if it is not yet set ... in this case we pretend directly play the next note
        nextNoteFrame += (max(0f, noteListItem.duration) * sampleRate).roundToInt()
        ++nextNoteIndex
    }
    return NextNoteInfo(nextNoteIndex, nextNoteFrame)
}

/// Mix all currently queued frames to the mixing buffer.
/**
 * @param mixingBuffer Array where we will put the mixed notes.
 * @param queuedNotes Notes which are queued for playing.
 * @param noteSamples For each possible note, this contains the track samples as PCM float.
 */
fun mixQueuedNotes(mixingBuffer: FloatArray, queuedNotes: InfiniteCircularBuffer<QueuedNotes>,
                   noteSamples: Array<FloatArray>) {
    mixingBuffer.fill(0.0f)
//    Log.v("Metronome", "AudioMixer queuedNotes.size: ${queuedNotes.size}")
    for (i in queuedNotes.indexStart until queuedNotes.indexEnd) {

        val queuedItem = queuedNotes[i]

        val noteId = queuedItem.nodeId
        val sampleStart = queuedItem.nextSampleToMix
        val startDelay = queuedItem.startDelay
        val volume = queuedItem.volume
//            Log.v("AudioMixer", "AudioMixer:mixAndQueueTracks : iTrack = $i, trackIndex=$trackIndex, startDelay=$startDelay")
        val samples = noteSamples[noteId]

        val numSamplesToWrite = min(samples.size - sampleStart,mixingBuffer.size - startDelay)
        val sampleEnd = sampleStart + numSamplesToWrite
//            Log.v("AudioMixer", "AudioMixer:mixAndQueueTracks : sampleStart=$sampleStart, sampleEnd=$sampleEnd, sampleSize=${trackSamples.size}")

        var j = startDelay
        for(k in sampleStart until sampleEnd) {
            mixingBuffer[j] = mixingBuffer[j] + volume * samples[k]
            ++j
        }

        queuedItem.startDelay = max(0, startDelay - mixingBuffer.size) // should always give zeo
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
}

/// Synchronize first beat to note list to given time and beat duration
/**
 * @param synchronizeTimeInfo Instance with the information how to synchronize.
 * @param noteList Note list which is currently played.
 * @param nextNoteInfo Info about the next note which is about to be queued
 * @param player The audio track which does the playing.
 */
private fun synchronizeTime(synchronizeTimeInfo: SynchronizeTimeInfo, noteList: NoteList,
                            nextNoteInfo: NextNoteInfo, player: AudioTrack): NextNoteInfo {
    if (noteList.isEmpty())
        return nextNoteInfo

    var nextNoteIndex = nextNoteInfo.nextNoteIndex
    var nextNoteFrame = nextNoteInfo.nextNoteFrame

    val sampleRate = player.sampleRate
    val currentTimeMillis = SystemClock.uptimeMillis()
    val currentTimeInFrames = player.playbackHeadPosition
    val referenceTimeInFrames = (currentTimeInFrames
            + (synchronizeTimeInfo.referenceTime - currentTimeMillis).toInt() * sampleRate / 1000)
    val beatDurationInFrames = (synchronizeTimeInfo.beatDuration * sampleRate).roundToInt()

    if (nextNoteInfo.nextNoteIndex >= noteList.size)
        nextNoteIndex = 0

    var referenceTimeForNextNoteListItem = referenceTimeInFrames
    for (i in 0 until nextNoteIndex)
        referenceTimeForNextNoteListItem += (max(0f, noteList[i].duration) * sampleRate).roundToInt()

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
    return NextNoteInfo(nextNoteIndex, nextNoteFrame)
}

/// Read note tracks and store them in an array.
/**
 * @param context Context needed for obtaining our note track files.
 * @param sampleRate Sample rate of the player.
 */
private fun createNoteSamples(context: Context, sampleRate: Int) : Array<FloatArray> {
    return Array(getNumAvailableNotes()) {
        // i -> audioToPCM(audioResourceIds[i], context)
        i -> waveToPCM(getNoteAudioResourceID(i, sampleRate), context)
    }
}

/// Audio mixer class which mixes and plays a note list.
/**
 * @param context Context needed for obtaining the note samples
 * @param scope Coroutine scope inside which we will start the player.
 */
class AudioMixer (val context: Context, private val scope: CoroutineScope) {

    ///  Note list with tracks which are played in a loop
    var noteList : NoteList? = null

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

    /// Job which does the playing.
    private var job: Job? = null

    /// Channel for transferring our synchronising information to the playing coroutine.
    private val synchronizeTimeChannel = Channel<SynchronizeTimeInfo>(Channel.CONFLATED)

    /// Start playing
    fun start() {
        job = scope.launch(Dispatchers.Default) {
        //job = scope.launch(newSingleThreadContext("Player")) {

            val player = createPlayer()
            val noteSamples = createNoteSamples(context, player.sampleRate)

            val queuedNotes = InfiniteCircularBuffer(32) {QueuedNotes()}

            val channelWithMarkerPosition = Channel<MarkerPositionAndNote>(Channel.UNLIMITED)
            val notifiedMarker = Channel<MarkerPositionAndNote>(Channel.CONFLATED)

            player.setPlaybackPositionUpdateListener(object: AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {
                    notifiedMarker.poll()?.noteListItem?.original?.let {note ->
                        noteStartedListener?.onNoteStarted(note)
                    }
                }
                override fun onPeriodicNotification(track: AudioTrack?) {}
            }, Handler(Looper.getMainLooper()))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sampleRate = player.sampleRate
                val bufferSize = player.bufferSizeInFrames
                player.addOnRoutingChangedListener ({
                    if(audioRoutingChangeRequiresNewPlayer(sampleRate, bufferSize))
                        restart()
                }, Handler(Looper.getMainLooper()))
            }

            val mixingBufferSize = min(player.bufferSizeInFrames / 2, 512)
            val mixingBuffer = FloatArray(mixingBufferSize)

            // Total number of frames for which we queued track for playing. Is zeroed when player starts.
            var queuedFrames = 0
            var nextNoteInfo = NextNoteInfo(0, player.bufferSizeInFrames / 2)

            val noteListCopy = NoteList()

            player.play()

            while(true) {
                if (!isActive)
                    break

                // update our local notelist copy
                noteListCopy.assignIfNotLocked(noteList)

                nextNoteInfo = queueNextNotes(nextNoteInfo, noteListCopy, queuedFrames,
                        mixingBuffer.size, channelWithMarkerPosition, player.sampleRate, queuedNotes)

                synchronizeTimeChannel.poll()?.let { synchronizeTimeInfo ->
                    nextNoteInfo = synchronizeTime(synchronizeTimeInfo, noteListCopy, nextNoteInfo, player)
                }

                queuedFrames += mixingBuffer.size
//                Log.v("Metronome", "AudioMixer queuedNotes.size: ${queuedNotes.size}")
                mixQueuedNotes(mixingBuffer, queuedNotes, noteSamples)

//                Log.v("Metronome", "AudioMixer notificationMarkerPosition: ${player.notificationMarkerPosition}")
                // set next notification marker if available
                while (player.playbackHeadPosition > player.notificationMarkerPosition) {
                    val markerPosition = channelWithMarkerPosition.poll()
                    if (markerPosition == null) {
                        break
                    }
                    else {
                        player.notificationMarkerPosition = markerPosition.frameWhenNoteListItemStarts
                        notifiedMarker.send(markerPosition)
                    }
                }

                //Log.v("Metronome", "AudioMixer mixingBuffer:max: ${mixingBuffer[0]}")
                player.write(mixingBuffer, 0, mixingBuffer.size, AudioTrack.WRITE_BLOCKING)

//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
//                    Log.v("Metronome", "AudioMixer underrun count: ${player.underrunCount}")
            }

            player.stop()
            player.flush()
            player.release()
        }
    }

    /// Stop playing
    fun stop() {
        val j = job
        scope.launch {
            j?.cancel()
            j?.join()
        }
        job = null
    }

    /// Restart player
    private fun restart() {
        stop()
        start()
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
        synchronizeTimeChannel.offer(SynchronizeTimeInfo(referenceTime, beatDuration))
    }
}

