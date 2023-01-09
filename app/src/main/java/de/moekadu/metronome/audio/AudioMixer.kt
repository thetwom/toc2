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

package de.moekadu.metronome.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import de.moekadu.metronome.metronomeproperties.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.coroutines.CoroutineContext
import kotlin.math.*

/** Structure for notifying that a note started playing.
 * @param delayInMillis Initial delay in milliseconds when the NoteStartedListener should be called after
 *   the note started playing.
 * @param noteStartedListener Callback which is called when a note is started.
 * @param coroutineContext CoroutineContext in which the noteStartedListener is called.
 *   If this is null, we will call it directly within the player thread. In this case it should
 *   return immediately.
 * @param coroutineScope Scope for launching the coroutines. Only relevant, if coroutineContext
 *   is not null.
 */
class NoteStartedChannel(delayInMillis: Float, val noteStartedListener: AudioMixer.NoteStartedListener,
                         val coroutineContext: CoroutineContext?, private val coroutineScope: CoroutineScope?) {

    /** Info about a started note.
     * @param noteListItem Copy of noteListItem which is started
     * @param startTimeUptimeMillis Time in SystemClock.uptimeMillis, when the note started.
     * @param count Counter of started note starting with 0 when the AudioMixer starts playing.
     */
    private data class NoteStartedInfo(val noteListItem: NoteListItem, val startTimeUptimeMillis: Long, val count: Long)

    /** Channel which transfers signals from the audio mixer to the noteStartedListener context. */
    private val channel = if (coroutineContext == null) null else Channel<NoteStartedInfo>(Channel.UNLIMITED)

    /** Current delay in millis. This MUST only be called within the player thread! */
    var delayInMillis = delayInMillis
        private set
        get() {
            changeDelayChannel.tryReceive().getOrNull()?.let {
                if (field != it)
                    field = it
            }
            return field
        }

    /** Channel for transferring delay updates to the player thread. */
    private var changeDelayChannel = Channel<Float>(Channel.CONFLATED)

    init {
        coroutineContext?.let { context ->
            require(coroutineScope != null)
            coroutineScope.launch(context) {
                channel?.consumeEach {
//                    Log.v("Metronome", "NoteStartedChannel consumeEah: noteListItem.uid = ${it.noteListItem.uid}")
                    noteStartedListener.onNoteStarted(it.noteListItem, it.startTimeUptimeMillis, it.count)
                }
            }
        }
    }

    /** Offer a new message of a started notes to trigger a call to the note started listener.
     * @note This only has an effect if the coroutineContext of the lass is not null.
     * @param noteListItem NoteListItem which starts playing.
     * @param startTimeUptimeMillis SystemClock.uptimeMillis when the note started playing
     * @param count Note count of the started note, counting the notes played after starting plying.
     */
    fun offer(noteListItem: NoteListItem, startTimeUptimeMillis: Long, count: Long) {
        channel?.trySend(NoteStartedInfo(noteListItem, startTimeUptimeMillis, count))
    }

    /** Call this to disconnect the channel, when this class is not needed anymore. */
    fun onDestroy() {
        channel?.close()
    }

    /** Set a new delay. Can be called from any thread
     * @param delayInMillis New delay.
     */
    fun setDelay(delayInMillis: Float) {
        changeDelayChannel.trySend(delayInMillis)
    }
}

/** Task class which tells if a channel should be registered or unregistered.
 * @param noteStartedChannel Channel which should be registered or unregistered.
 * @param unregisterChannel If true, channel will be unregistered, if false, channel will be registered
 */
private class NoteStartedChannelWithAddOrRemoveInfo(val noteStartedChannel: NoteStartedChannel,
                                                    val unregisterChannel: Boolean)

/** Required information for handling the notifications when a playlist item starts.
 *  @param noteListItem NoteListItem which will be started
 *  @param noteStartedChannel NoteStartedChannel where the info is sent.
 *  @param frameNumber Frame number when the noteStartedListener should be called.
 *  @param noteCount Counter for played notes since player start
 *  @param uptimeMillis Time (as given by SystemClock.uptimeMillis) when the note starts playing.
 */
private class NoteStartedChannelAndFrame(
    val noteListItem: NoteListItem,
    val noteStartedChannel: NoteStartedChannel,
    val frameNumber: Int,
    val noteCount: Long,
    val uptimeMillis: Long
)

/** Class which stores tracks which are queued for the playing.
 * @param noteId Note index in #availableNotes.
 * @param startFrame Frame index when this note starts playing.
 * @param volume Track volume.
 */
private data class QueuedNote(val noteId: Int, val startFrame: Int, val volume: Float)

/** Info about next next note which must be queued for being played.
 * @param nextNoteIndex Note index in note list of the note to be played.
 * @param nextNoteFrame Frame, when the note should be played.
 * @param noteCount Absolute counter of notes, since starting playing.
 */
private data class NextNoteInfo(val nextNoteIndex: Int, val nextNoteFrame: Int, val noteCount: Long)

/** Class containing info for synchronize click time to a given reference.
 * @param referenceTime Time in uptime millis (from call to SystemClock.uptimeMillis()
 *   to which the first beat should be synchronized
 * @param beatDurationInSeconds Duration in seconds for a beat. The playing is then synchronized such,
 *   that the first beat of the playlist is played at
 *      referenceTime + n * beatDuration
 *   where n is a integer number.
 */
private data class SynchronizeTimeInfo(val referenceTime: Long, val beatDurationInSeconds: Float)

/** We use not the minimum buffer size but scale it with this integer value.
 * A factor of 4 or larger seems to be necessary, to have noteStartedListeners registered early
 * enough to get a correct vibration/visualization behavior. */
private const val minBufferSizeFactor = 4

/** Check if we need to recreate a player since the audio sink properties changed.
 * @param sampleRate Currently used sample rate
 * @param bufferSizeInFrames Currently used buffer sie in frames (we assume, that PCM_FLOAT is used)
 * @return True, if we should recreate the player
 */
private fun audioRoutingChangeRequiresNewPlayer(sampleRate: Int, bufferSizeInFrames:Int) : Boolean {
    val nativeSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
    // here we skip the minBufferSizeFactor, since we only want to restart the player if really needed!
    val bufferSizeInBytes = AudioTrack.getMinBufferSize(nativeSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
//    Log.v("Metronome", "audioRoutingChangesRequiresNewPlayer: nativeSampleRate=$nativeSampleRate, currentSampleRate=${sampleRate}, bufferSize=$bufferSizeInBytes, currentBufferSize=${4*bufferSizeInFrames}")
    return (sampleRate != nativeSampleRate || bufferSizeInBytes > 4 * bufferSizeInFrames)
}

/** Create a new audio track instance.
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

/** Add a single note to our note queue and listeners.
 * @param noteListItem Note list item to be queued
 * @param noteFrame Frame at which the note should be queued.
 * @param noteCount Count to be used for messaging the number of played notes since start of playing.
 * @param queuedNoteStartedChannel Channel wehre we send the newly queued note with some extra infos.
 * @param noteStartedChannels Array with all registered NoteStartedChannels together with the delay.
 * @param sampleRate Currently used sample rate
 * @param queuedNotes This is the queue where we add our notes.
 * @param delayInFrames Note delay in frames.
 */
private fun queueSingleNote(
    noteListItem: NoteListItem,
    noteFrame: Int,
    noteCount: Long,
    queuedNoteStartedChannel: Channel<NoteStartedChannelAndFrame?>,
                            //noteStartedChannelsAndFrames: ArrayList<NoteStartedChannelAndFrame>,
    noteStartedChannels: ArrayList<NoteStartedChannel>,
    sampleRate: Int,
    queuedNotes: ArrayList<QueuedNote>,
    delayInFrames: Int,
    frameNumberToMillis: FrameNumberToMillis
) {

    val queuedNote = QueuedNote(noteListItem.id, noteFrame + delayInFrames, noteListItem.volume)
    queuedNotes.add(queuedNote)

    for (noteStartedChannel in noteStartedChannels) {
        //noteStartedChannelsAndFrames.add(
        val frameNumber = noteFrame + delayInFrames + (noteStartedChannel.delayInMillis / 1000f * sampleRate).roundToInt()
        queuedNoteStartedChannel.trySend(
            NoteStartedChannelAndFrame(noteListItem,
                noteStartedChannel,
                frameNumber,
                noteCount,
                frameNumberToMillis.frameToMillis(frameNumber)
            )
        )
    }
}

/** Put notes in to queue, which will be played.
 * @param nextNoteInfo Info about the next note, that will be put into the queue.
 * @param noteList Contains all notes to be played.
 * @param bpmQuarter Quarter notes per minute
 * @param alreadyQueuedFrames Frame number up to which we did already queued the notes.
 * @param numFramesToQueue Number of frames after alreadyQueuedFrames, for which we should queue the notes.
 * @param queuedNoteStartedChannel Channel where we send the newly queued notes with some extra infos.
 * @param noteStartedChannels Array with all registered NoteStartedChannels together with the delay.
 * @param sampleRate Currently used sample rate
 * @param queuedNotes This is the queue where we add our notes.
 * @param delayInFrames Note delay in frames.
 * @return An updated nextNoteInfo which serves as input to this function on the next cycle.
 */
private fun queueNextNotes(
    nextNoteInfo: NextNoteInfo,
    noteList: ArrayList<NoteListItem>,
    bpmQuarter: Float,
    alreadyQueuedFrames: Int,
    numFramesToQueue: Int,
//                           noteStartedChannelsAndFrames: ArrayList<NoteStartedChannelAndFrame>,
    queuedNoteStartedChannel: Channel<NoteStartedChannelAndFrame?>,
    noteStartedChannels: ArrayList<NoteStartedChannel>,
    sampleRate: Int,
    queuedNotes: ArrayList<QueuedNote>,
    delayInFrames: Int,
    frameNumberToMillis: FrameNumberToMillis) : NextNoteInfo{
    require(noteList.isNotEmpty())
    var maxDuration = -1f
    for (n in noteList)
        maxDuration = max(n.duration.durationInSeconds(bpmQuarter), maxDuration)
    require(maxDuration > 0f)

    var nextNoteIndex = nextNoteInfo.nextNoteIndex
    var nextNoteFrame = nextNoteInfo.nextNoteFrame
    var noteCount = nextNoteInfo.noteCount

    while (nextNoteFrame < alreadyQueuedFrames + numFramesToQueue) {
        if (nextNoteIndex >= noteList.size)
            nextNoteIndex = 0

        val noteListItem = noteList[nextNoteIndex]
        // this will add the note list item to the queuedNotes and noteStartedChannelsAndFrames
//        queueSingleNote(noteListItem, nextNoteFrame, noteCount, noteStartedChannelsAndFrames, noteStartedChannels, sampleRate, queuedNotes, delayInFrames)
        queueSingleNote(noteListItem, nextNoteFrame, noteCount, queuedNoteStartedChannel, noteStartedChannels, sampleRate, queuedNotes, delayInFrames, frameNumberToMillis)

        // notes can have a duration of -1 if it is not yet set ... in this case we directly play the next note
        nextNoteFrame += (max(0f, noteListItem.duration.durationInSeconds(bpmQuarter)) * sampleRate).roundToInt()
        ++nextNoteIndex
        ++noteCount
    }
    return NextNoteInfo(nextNoteIndex, nextNoteFrame, noteCount)
}

/** Mix all currently queued frames to the mixing buffer.
 * @param mixingBuffer Array where we will put the mixed notes.
 * @param nextFrameToMix Next frame index for which we mix our audio. In other words
 *    mixingBuffer[0] belongs to nextFrameToMix, mixingBuffer[1] belongs to nextFrameToMix+1, ...
 * @param queuedNotes Notes which are queued for playing. After a note is fully mixed, it
 *     will be removed from this list inside this function.
 * @param noteSamples For each possible note, this contains the track samples as PCM float.
 */
private fun mixQueuedNotes(mixingBuffer: FloatArray,
                   nextFrameToMix: Int,
                   queuedNotes: ArrayList<QueuedNote>,
                   noteSamples: Array<FloatArray>) {
    mixingBuffer.fill(0.0f)
//    Log.v("Metronome", "AudioMixer queuedNotes.size: ${queuedNotes.size}")
    for (queuedItem in queuedNotes) {
        val noteId = queuedItem.noteId
        val noteSamplePosition = max(0, nextFrameToMix - queuedItem.startFrame)
        val mixingBufferPosition = max(0, queuedItem.startFrame - nextFrameToMix)
        val volume = queuedItem.volume
        val samples = noteSamples[noteId]

        val numSamplesToWrite = max(0, min(mixingBuffer.size - mixingBufferPosition, samples.size - noteSamplePosition))

        for (j in 0 until numSamplesToWrite) {
            mixingBuffer[mixingBufferPosition + j] = mixingBuffer[mixingBufferPosition + j] + volume * samples[noteSamplePosition + j]
        }
    }

    val lastMixedFrame = nextFrameToMix + mixingBuffer.size
    queuedNotes.removeAll { lastMixedFrame >= it.startFrame + noteSamples[it.noteId].size }
}

/** Synchronize first beat to note list to given time and beat duration
 * @param synchronizeTimeInfo Instance with the information how to synchronize.
 * @param noteList Note list which is currently played.
 * @param bpmQuarter Metronome speed in quarter notes per minute
 * @param nextNoteInfo Info about the next note which is about to be queued
 * @param sampleRate Sample rate in Hz
 * @param delayInFrames Delay which is used for playing notes.
 * @param alreadyQueuedFrames Frame number up to which we did already queued the notes.
 * @param frameNumberToMillis Conversion between uptime millis and frame number.
 * @return Info about next note to be played.
 */
private fun synchronizeTime(
    synchronizeTimeInfo: SynchronizeTimeInfo,
    noteList: ArrayList<NoteListItem>,
    bpmQuarter: Float,
    nextNoteInfo: NextNoteInfo,
    sampleRate: Int,
    delayInFrames: Int,
    alreadyQueuedFrames: Int,
    frameNumberToMillis: FrameNumberToMillis
): Array<NextNoteInfo> {
    if (noteList.isEmpty())
        return arrayOf(nextNoteInfo)

    var nextNoteIndex = nextNoteInfo.nextNoteIndex
    val nextNoteFrame = nextNoteInfo.nextNoteFrame
    var noteCount = nextNoteInfo.noteCount

//    val sampleRate = player.sampleRate
//    val currentTimeMillis = SystemClock.uptimeMillis()
//    val currentTimeInFrames = player.playbackHeadPosition
    // reference time, for the first note of the play list to be played. Actually the time of the
    // first note would be timeOfFirstNoteInFrames = referenceTimeInFrames + i * beatDurationInFrames
    // where i is an integer number.
    val referenceTimeInFrames = frameNumberToMillis.millisToFrames(synchronizeTimeInfo.referenceTime) - delayInFrames
//    val referenceTimeInFrames = (currentTimeInFrames - delayInFrames
//            + (synchronizeTimeInfo.referenceTime - currentTimeMillis).toInt() * sampleRate / 1000)
    val beatDurationInFrames = (synchronizeTimeInfo.beatDurationInSeconds * sampleRate).roundToInt()

    if (nextNoteInfo.nextNoteIndex >= noteList.size)
        nextNoteIndex = 0

    // determine reference time for the next note to be played. Actually the time of this note must be
    // timeOfNextNote = referenceTimeForNextNoteListItem + i * beatDurationInFrames
    // where i is a integer number.
    var referenceTimeForNextNoteListItem = referenceTimeInFrames

    for (i in 0 until nextNoteIndex)
        referenceTimeForNextNoteListItem += (max(0f, noteList[i].duration.durationInSeconds(bpmQuarter)) * sampleRate).roundToInt()

    // find the closest possible time in frames for our next note to be played, relative to the
    // previously used frame.
    // The three different values in the following must be considered, since integer divisions
    // of positive/negative numbers can occur and depending on this we get different behaviors.
    // So we we first remove multiples of beatDurations and then check if we remove one beatDuration
    // less ore more ist closer.
    val closeTimeForNextNoteListItemInit = referenceTimeForNextNoteListItem - beatDurationInFrames * ((referenceTimeForNextNoteListItem - nextNoteFrame) / beatDurationInFrames)
    val closeTimeDistanceInit = (closeTimeForNextNoteListItemInit - nextNoteFrame).absoluteValue
    val closeTimeForNextNoteListItemUpper = closeTimeForNextNoteListItemInit + beatDurationInFrames
    val closeTimeDistanceUpper = (closeTimeForNextNoteListItemUpper - nextNoteFrame).absoluteValue
    val closeTimeForNextNoteListItemLower = closeTimeForNextNoteListItemInit - beatDurationInFrames
    val closeTimeDistanceLower = (closeTimeForNextNoteListItemLower - nextNoteFrame).absoluteValue

    var correctedTimeForNextNote = if (closeTimeDistanceUpper < closeTimeDistanceInit)
        closeTimeForNextNoteListItemUpper
    else if (closeTimeDistanceLower < closeTimeDistanceInit)
        closeTimeForNextNoteListItemLower
    else
        closeTimeForNextNoteListItemInit

    var noteToBeQueuedImmediately: NextNoteInfo? = null
    // we now have the time, when the nextNoteListItem should actually be played
    // however, it is possible, that this should be played earlier than expected. In this case
    // we play it directly, and directly queue the next note.
    // But, it is possible that we are very late, such that even the note after the next note would
    // be played late, so we skip so many notes, until the next note takes place later than the
    // already queued frames.
    while (correctedTimeForNextNote < alreadyQueuedFrames) {
        val timeInFramesForNoteAfterNextNote = correctedTimeForNextNote + (max(0f, noteList[nextNoteIndex].duration.durationInSeconds(bpmQuarter)) * sampleRate).roundToInt()

        if (timeInFramesForNoteAfterNextNote > alreadyQueuedFrames) {
            noteToBeQueuedImmediately = NextNoteInfo(nextNoteIndex, alreadyQueuedFrames, noteCount)
            correctedTimeForNextNote = timeInFramesForNoteAfterNextNote
            ++nextNoteIndex
            if (nextNoteIndex >= noteList.size)
                nextNoteIndex -= noteList.size
        }
        ++noteCount
    }

    val updatedNextNoteInfo = NextNoteInfo(nextNoteIndex, correctedTimeForNextNote, noteCount)
    return if (noteToBeQueuedImmediately == null)
        arrayOf(updatedNextNoteInfo)
    else
        arrayOf(noteToBeQueuedImmediately, updatedNextNoteInfo)
}

/** Read note tracks and store them in an array.
 * @param context Context needed for obtaining our note track files.
 * @param sampleRate Sample rate of the player.
 */
private fun createNoteSamples(context: Context, sampleRate: Int) : Array<FloatArray> {
    return Array(getNumAvailableNotes()) {
        // i -> audioToPCM(audioResourceIds[i], context)
        i -> waveToPCM(getNoteAudioResourceID(i, sampleRate), context)
    }
}

/** Compute required delay for note to start playing based on a delay array with entries for NoteStartedListeners.
 * This is the absolute value of the largest negative value or 0.
 *
 * @param noteStartedChannels All available NoteStartedListeners with delay.
 * @return Required note delay which is >= 0.
 */
private fun computeNoteDelayInMillis(noteStartedChannels: ArrayList<NoteStartedChannel>): Float {
    val minimumDelay = noteStartedChannels.minByOrNull { it.delayInMillis }?.delayInMillis ?: return 0f
    return max(-minimumDelay, 0f)
}

/** Converter between frame number since player start and uptime millis.
 * @param sampleRate Player sample rate in Hz.
 */
private class FrameNumberToMillis(val sampleRate: Int) {
    /** Reference frame number which corresponds to the millisRef. */
    private var frameNumberRef = 0
    /** Reference time in millis (SystemClock.uptimeMillis), corresonding to frameNumberRef. */
    private var millisRef = 0L

    /** Synchronize the frame number with the current SystemClock.uptimeMillis.
     * @param frameNumber Current frame number of player.
     */
    fun sync(frameNumber: Int) {
        frameNumberRef = frameNumber
        millisRef = SystemClock.uptimeMillis()
    }

    /** Convert frame number to uptimeMillis.
     * @param frameNumber Frame number to be converted.
     * @return uptime millis which corresponds to the given frame number.
     */
    fun frameToMillis(frameNumber: Int): Long {
        return ((1000L * (frameNumber - frameNumberRef)) / sampleRate) + millisRef
    }
    /** Convert frame uptimeMillis to frame number.
     * @param millis Time in milliseconds should be converted.
     * @return frame number which corresponds to the given time.
     */
    fun millisToFrames(millis: Long): Int {
        return (((millis - millisRef) * sampleRate) / 1000L + frameNumberRef).toInt()
    }
}
/** Audio mixer class which mixes and plays a note list.
 * @param context Context needed for obtaining the note samples
 * @param scope Coroutine scope inside which we will start the player.
 */
class AudioMixer (val context: Context, private val scope: CoroutineScope) {

    private val noteSamplesForDifferentSampleRates = mapOf (
        44100 to lazy {
//            Log.v("Metronome", "AudioMixer: load samples for 44100Hz")
            createNoteSamples(context, 44100)
        },
        48000 to lazy {
//            Log.v("Metronome", "AudioMixer: load samples for 48000Hz")
            createNoteSamples(context, 48000)
        }
    )

    /** Note list with tracks which are played in a loop. */
    var noteList = ArrayList<NoteListItem>()
        set(value) {
            scope.launch(Dispatchers.Main) {
                noteListLock.withLock {
                    deepCopyNoteList(value, field)
                }
            }
        }

    /** Lock which is active when we access the noteList. */
    private val noteListLock = Mutex()

    /** Interface for listener which is used when a new note list item starts. */
    fun interface NoteStartedListener {
        /** Callback function which is called when a playlist item starts
         * @param noteListItem Note list item which is started.
         * @param uptimeMillis Result of SystemClock.uptimeMillis when the note is started.
         * @param noteCount Counter for notes since start of playing
         */
        fun onNoteStarted(noteListItem: NoteListItem?, uptimeMillis: Long, noteCount: Long)
    }

    /** Callback channels when a note starts together with a delay. */
    private val noteStartedChannels = ArrayList<NoteStartedChannel>()

    /** Channel where we send the queued notes together with extra info like when they start playing.
     * We allow also sending null, as a signal, that the queued notes list has been cleared
     * which means what all queued notes won't be played any furhter.
     */
    private val queuedNotesChannel = Channel<NoteStartedChannelAndFrame?>(1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Channel for registering or unregistering NoteStartedChannels. */
    private val addOrRemoveNoteStartedChannel = Channel<NoteStartedChannelWithAddOrRemoveInfo>(Channel.UNLIMITED)

    /** Job which does the playing. */
    private var job: Job? = null

    private var noteStartedJob: Job? = null

    /** Channel for transferring our synchronising information to the playing coroutine. */
    private val synchronizeTimeChannel = Channel<SynchronizeTimeInfo>(Channel.CONFLATED)

    /** Channel for modifying the index of the next note to be played. */
    private val nextNoteIndexModificationChannel = Channel<Int>(Channel.CONFLATED)

    /** Channel through which we can reset the player to immediately start playing the current note list from the beginning. */
    private val restartPlayingNoteListChannel = Channel<Boolean>(Channel.CONFLATED)

    /** Channel for modifying the bpmQuarter. */
    private val bpmQuarterChannel = Channel<Float>(Channel.CONFLATED)

    /** Quarter notes per minute defining the speed of the metronome.
     * @warning Set this only within the player to avoid threading issues!
     */
    private var bpmQuarter = -1.0f

    private val isMuteChannel = Channel<Boolean>(Channel.CONFLATED)
    private var isMute: Boolean = false

    private val noteStartedDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r).apply {
            priority = Thread.MAX_PRIORITY
            name = "Note started dispatcher"

        }
    }.asCoroutineDispatcher()

    init {
        // preload all samples for quicker player start
        scope.launch(Dispatchers.Main) {
            // first load with known native sample rate, then load for all sample rates
            val nativeSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            noteSamplesForDifferentSampleRates[nativeSampleRate]?.value

            for (n in noteSamplesForDifferentSampleRates)
                n.value.value
        }
    }

    /** Create a new NoteStartedChannel.
     * @warning When this is not needed anymore, you MUST call unregisterNoteStartedChannel
     * @info The delay can be changed later on by calling noteStartedChannel.changeDelay(...)
     * @param delayInMilliSeconds The NoteStartedListener will be started with the given delay after the
     *   note starts playing. Can be negative, in order to call this before the note starts.
     * @return A new NoteStartedChannel.
     */
    fun getNewNoteStartedChannel(delayInMilliSeconds: Float = 0f, coroutineContext: CoroutineContext?,
                                 noteStartedListener: NoteStartedListener): NoteStartedChannel {
        val noteStartedChannel = NoteStartedChannel(delayInMilliSeconds, noteStartedListener, coroutineContext, scope)
        addOrRemoveNoteStartedChannel.trySend(NoteStartedChannelWithAddOrRemoveInfo(noteStartedChannel, false))
        return noteStartedChannel
    }

    /** Unregister a NoteStartedChannel.
     * @param noteStartedChannel NoteStartedChannel to be unregistered.
     */
    fun unregisterNoteStartedChannel(noteStartedChannel: NoteStartedChannel?) {
        noteStartedChannel?.let {
            it.onDestroy()
            addOrRemoveNoteStartedChannel.trySend(
                NoteStartedChannelWithAddOrRemoveInfo(it, true))
        }
    }

    /** Read the list of channels to be registered/unregistered and update our noteStartedChannels list.
     * @note This must be called within the player thread
     */
    private fun updateNoteStartedChannels() {
        var addOrRemove = addOrRemoveNoteStartedChannel.tryReceive().getOrNull()
        while (addOrRemove != null) {
            if (addOrRemove.unregisterChannel) {
//                Log.v("Metronome", "AudioMixer.updateNoteStartedChannels: unregister channel")
                noteStartedChannels.remove(addOrRemove.noteStartedChannel)
            } else {
                if (!noteStartedChannels.contains(addOrRemove.noteStartedChannel)) {
//                    Log.v("Metronome", "AudioMixer.updateNoteStartedChannels: register channel")
                    noteStartedChannels.add(addOrRemove.noteStartedChannel)
                }
            }
            addOrRemove = addOrRemoveNoteStartedChannel.tryReceive().getOrNull()
        }
    }

    fun setBpmQuarter(bpmQuarter: Float) {
        bpmQuarterChannel.trySend(bpmQuarter)
    }

    fun setMute(state: Boolean) {
        isMuteChannel.trySend(state)
    }

    fun restartPlayingNoteList() {
        restartPlayingNoteListChannel.trySend(true)
    }

    /** Start playing. */
    fun start() {
        stop() // stop a job, if one is running

        while(queuedNotesChannel.tryReceive().isSuccess) { }

        noteStartedJob = scope.launch(noteStartedDispatcher) {
//            var lastTime = 0L
//            var lastNoteFrame = 0
//            var lastNoteMillis = 0L
            val queuedNoteStartedChannels = ArrayList<NoteStartedChannelAndFrame>()

            while (isActive) {
                delay(1)

                while(true) {
                    val value = queuedNotesChannel.tryReceive()
                    if (value.isSuccess) {
                        val receivedValue = value.getOrNull()
                        // if a "null" was sent, this means that all previously queued notes have been
                        // discarded and won't play any further. In this case we must clear the list.
                        if (receivedValue == null)
                            queuedNoteStartedChannels.clear()
                        else
                            queuedNoteStartedChannels.add(receivedValue)
                    } else {
                        break
                    }
                }

                val time = SystemClock.uptimeMillis()
//                val diff = time - lastTime
//                lastTime = time
//                if (diff > 30L)
//                    Log.v("Metronome", "AudioMixer: noteStartedJob, time diff: $diff ")
//
//                Log.v("Metronome", "AudioMixer: noteStartedJob, time = $time, registered = ${queuedNoteStartedChannels.size} ")
                queuedNoteStartedChannels.filter {
                    it.uptimeMillis <= time
                }.forEach {
//                    val frameDiff = it.frameNumber - lastNoteFrame
//                    lastNoteFrame = it.frameNumber
//                    val milliDiff = it.uptimeMillis - lastNoteMillis
//                    lastNoteMillis = it.uptimeMillis
//                    Log.v("Metronome", "AudioMixer: queued note started, frameDiff = $frameDiff, milliDiff = $milliDiff, registered millis=${it.uptimeMillis}")

                    val noteListItemCopy = it.noteListItem.clone()

                    if (it.noteStartedChannel.coroutineContext == null) {
                        it.noteStartedChannel.noteStartedListener.onNoteStarted(noteListItemCopy, it.uptimeMillis, it.noteCount)
                    } else {
                        it.noteStartedChannel.offer(noteListItemCopy, it.uptimeMillis, it.noteCount)
                    }
                }
                queuedNoteStartedChannels.removeAll { it.uptimeMillis <= time }
            }
        }

//        Log.v("Metronome", "TIMECHECK: AudioMixer launching job")
        job = scope.launch(Dispatchers.Default) {
//            Log.v("Metronome", "TIMECHECK: AudioMixer creating player")
            val player = createPlayer()
//            Log.v("Metronome", "TIMECHECK: AudioMixer creating player, done")
            val noteSamples = noteSamplesForDifferentSampleRates[player.sampleRate]!!.value //createNoteSamples(context, player.sampleRate)

            val queuedNotes = ArrayList<QueuedNote>(32)
//            val queuedNoteStartedChannels = ArrayList<NoteStartedChannelAndFrame>()

            val mixingBufferSize = min(player.bufferSizeInFrames / 2, 128)
            val mixingBuffer = FloatArray(mixingBufferSize)

            // Total number of frames for which we queued track for playing.
            var numMixedFrames = 0
            //var nextNoteInfo = NextNoteInfo(0, player.bufferSizeInFrames / 2, 0)
            var nextNoteInfo = NextNoteInfo(0, 1, 0)

            val noteListCopy = ArrayList<NoteListItem>()

            val framesToMillis = FrameNumberToMillis(player.sampleRate).apply { sync(0) }
            player.positionNotificationPeriod = player.bufferSizeInFrames

//            player.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
//                override fun onMarkerReached(track: AudioTrack?) {}
//                override fun onPeriodicNotification(track: AudioTrack?) {
//                    try {
//                        track?.playbackHeadPosition?.let { framesToMillis.sync(it) }
//                    } catch (_: java.lang.Exception) {
//
//                    }
////                    if (player.playState != AudioTrack.PLAYSTATE_PLAYING) {
////                        framesToMillis.sync(player.playbackHeadPosition)
////                    }
//                }
//            })

            player.play()

            // add the routing change listener somewhere AFTER .play() since we don't have the device info before
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sampleRate = player.sampleRate
                val bufferSize = player.bufferSizeInFrames
                val deviceId = player.routedDevice?.id ?: 0
                player.addOnRoutingChangedListener ({
                    val deviceInfo = it.routedDevice
                    // don't restart if the device is the same -> we assume that for the same device the buffer sizes don't need change
                    // and if the system still tells differently, we want to avoid this noise
                    if(deviceInfo?.id != deviceId && audioRoutingChangeRequiresNewPlayer(sampleRate, bufferSize))
                            restart()
                }, Handler(Looper.getMainLooper()))
            }

//            var lastLoopTime = 0L
//            var lastPosition = 0

            var loopCounter = 0L
//            Log.v("Metronome", "AudioMixer start player loop")
            while(true) {
                if (!isActive) {
                    break
                }

                // update our local noteList copy
                if (noteListLock.tryLock()) {
                    try {
                        deepCopyNoteList(noteList, noteListCopy)
                    }
                    finally {
                        noteListLock.unlock()
                    }
                }
                //Log.v("Metronome", "AudioMixer noteList.size: ${noteList.size}")

                // set new speed if available
                bpmQuarterChannel.tryReceive().getOrNull()?.let {
                    bpmQuarter = it
                }
                require(bpmQuarter > 0.0f)

                isMuteChannel.tryReceive().getOrNull()?.let {
                    isMute = it
                }

                nextNoteIndexModificationChannel.tryReceive().getOrNull()?.let { index ->
                    nextNoteInfo = nextNoteInfo.copy(nextNoteIndex = index)
                }

                restartPlayingNoteListChannel.tryReceive().getOrNull()?.let {
                    nextNoteInfo = nextNoteInfo.copy(nextNoteFrame = numMixedFrames, nextNoteIndex = 0)
                    queuedNotes.clear()
                    while(queuedNotesChannel.tryReceive().isSuccess) { }
                    queuedNotesChannel.trySend(null)
//                    queuedNoteStartedChannels.clear()
                }

                // check if there were request to add or remove channels for the noteStartedListeners
                updateNoteStartedChannels()

                val noteDelayInMillis = computeNoteDelayInMillis(noteStartedChannels)
                val delayInFrames = (noteDelayInMillis / 1000f * player.sampleRate).roundToInt()

                synchronizeTimeChannel.tryReceive().getOrNull()?.let { synchronizeTimeInfo ->
                    // at synchronizing there possible must be played a note immediately,
                    // if this is the case, it will be stored in nextNoteInfos[0] and an additional entry will be in the list.
                    val nextNoteInfos = synchronizeTime(synchronizeTimeInfo, noteListCopy, bpmQuarter, nextNoteInfo, player.sampleRate, delayInFrames,
                        numMixedFrames, framesToMillis)
                    if (nextNoteInfos.size > 1 && nextNoteInfos[0].nextNoteFrame == numMixedFrames) {
                        val noteListItem = noteList[nextNoteInfos[0].nextNoteIndex]
                        queueSingleNote(noteListItem, nextNoteInfos[0].nextNoteFrame, nextNoteInfos[0].noteCount, queuedNotesChannel, noteStartedChannels, player.sampleRate, queuedNotes, delayInFrames, framesToMillis)
                    }
                    nextNoteInfo = nextNoteInfos.last()
                }

                // side effects of following function:
                // - to "queuedNoteStartedChannels" we append the infos about when we to sent info to the
                //   different channels that a note has started.
                // - to "queuedNotes", we add the notes which are queued.
                nextNoteInfo = queueNextNotes(nextNoteInfo, noteListCopy, bpmQuarter, numMixedFrames,
                    mixingBuffer.size, queuedNotesChannel, noteStartedChannels,
                    player.sampleRate, queuedNotes, delayInFrames, framesToMillis)

                // fill the mixing buffer with our mixed sound samples.
                // - side effects: when a queued note fully added to the mixing buffer, it
                //    will be removed from the queued notes
                mixQueuedNotes(mixingBuffer, numMixedFrames, queuedNotes, noteSamples)

                numMixedFrames += mixingBuffer.size

//                Log.v("Metronome", "AudioMixer notificationMarkerPosition: ${player.notificationMarkerPosition}")

//                val position = player.playbackHeadPosition
//                val positionDiff = position - lastPosition
//                lastPosition = position
//                val loopTime = SystemClock.uptimeMillis()
//                val diff = loopTime - lastLoopTime
//                lastLoopTime = loopTime
//                Log.v("Metronome", "AudioMixer: mixing loop, duration since last cycle = ${diff}, posdiff = $positionDiff")
                // call registered callbacks
//                Log.v("Metronome", "AudioMixer: numqueuedChannels = ${queuedNoteStartedChannels.size}")

//                queuedNoteStartedChannels.filter {
//                    it.frameNumber <= position
//                }.forEach {
//                    val noteListItemCopy = it.noteListItem.clone()
//                    val uptimeMillis = SystemClock.uptimeMillis() - ((position - it.frameNumber) * 1000L) / player.sampleRate
//                    if (it.noteStartedChannel.coroutineContext == null) {
//                        it.noteStartedChannel.noteStartedListener.onNoteStarted(noteListItemCopy, uptimeMillis, it.noteCount)
//                    } else {
//                        it.noteStartedChannel.offer(noteListItemCopy, uptimeMillis, it.noteCount)
//                    }
//                }
//                queuedNoteStartedChannels.removeAll { it.frameNumber <= position }

                if (isMute)
                    mixingBuffer.fill(0f)

                //Log.v("Metronome", "AudioMixer mixingBuffer:max: ${mixingBuffer[0]}")
                player.write(mixingBuffer, 0, mixingBuffer.size, AudioTrack.WRITE_BLOCKING)

//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
//                    Log.v("Metronome", "AudioMixer underrun count: ${player.underrunCount}")
                ++loopCounter
            }

            player.stop()
            player.flush()
            player.release()
        }//.also { it.invokeOnCompletion { Log.v("Metronome", "AudioMixer player thread done!") } }
    }

    /** Stop playing. */
    fun stop() {
        noteStartedJob?.cancel()
        noteStartedJob = null

        val j = job
        if (j != null) {
            scope.launch {
                j.cancelAndJoin()
            }//.invokeOnCompletion { Log.v("Metronome", "AudioMixer.stop : stop job canceled") }
        }
        // Log.v("Metronome", "AudioMixer.stop : setting old job to null")
        job = null
    }

    /** Restart player. */
    private fun restart() {
        stop()
        start()
    }

    /** Synchronize first beat to note list to given time and beat duration.
     * @param referenceTime Time in uptime millis (from call to SystemClock.uptimeMillis()
     *   to which the first beat should be synchronized
     * @param beatDuration Duration in seconds for a beat. The playing is then synchronized such,
     *   that the first beat of the playlist is played at
     *      referenceTime + n * beatDuration
     *   where n is a integer number.
     */
    fun synchronizeTime(referenceTime: Long, beatDuration: Float) {
        synchronizeTimeChannel.trySend(SynchronizeTimeInfo(referenceTime, beatDuration))
    }

    /** Modify the index of the next note to be played. */
    fun setNextNoteIndex(index: Int) {
        nextNoteIndexModificationChannel.trySend(index)
    }

    fun destroy() {
        noteStartedDispatcher.cancel()
    }
}

