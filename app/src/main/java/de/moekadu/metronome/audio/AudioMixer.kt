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
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import de.moekadu.metronome.metronomeproperties.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.math.*

/** Structure for notifying that a note started playing.
 * @warning: Set this only from the NoteStarterHandling-class (in theory, this should not be visible outside ...)
 * @param delayInMillis Delay in milliseconds when the NoteStartedListener should be called after
 *   the note started playing.
 * @param noteStartedListener Callback which is called when a note is started.
 * @param callbackWhen Defines, when the callback is actually called, either when the note is
 *   started (NoteStarted) or as early as possible (NoteQueued).
 * @param coroutineContext CoroutineContext in which the noteStartedListener is called.
 *   If this is null, we will call it directly within the player thread. In this case it should
 *   return immediately.
 * @param coroutineScope Scope for launching the coroutines. Only relevant, if coroutineContext
 *   is not null.
 */
class NoteStartedHandler(
    @Volatile var delayInMillis: Int, val noteStartedListener: AudioMixer.NoteStartedListener,
    val callbackWhen: CallbackWhen,
    val coroutineContext: CoroutineContext?,
    private val coroutineScope: CoroutineScope?) {

    /** Define when the callback should be called. Either as early as possible (NoteQueued) or
     * when the note actually starts playing (NoteStarted).*/
    enum class CallbackWhen { NoteQueued, NoteStarted }
    /** Info about a started note.
     * @param noteListItem Copy of noteListItem which is started
     * @param startTimeNanos Time in System.nanoTime(), when the note started.
     * @param noteDurationNanos Total note duration in nano seconds (as derived from the bpm).
     * @param count Counter of started note starting with 0 when the AudioMixer starts playing.
     * @param handlerDelayNanos Delay of how long after the note start, the handler is called, in
     *   nanoseconds. If negative, the handler is called before the note starts.
     */
    private data class NoteStartedInfo(
        val noteListItem: NoteListItem, val startTimeNanos: Long, val noteDurationNanos: Long,
        val count: Long, val handlerDelayNanos: Long)

    /** Channel which transfers signals from the audio mixer to the noteStartedListener context. */
    private val channel = if (coroutineContext == null) null else Channel<NoteStartedInfo>(Channel.UNLIMITED)

    init {
        coroutineContext?.let { context ->
            require(coroutineScope != null)
            coroutineScope.launch(context) {
                channel?.consumeEach {
//                    Log.v("Metronome", "NoteStartedChannel consumeEah: noteListItem.uid = ${it.noteListItem.uid}")
                    noteStartedListener.onNoteStarted(
                        it.noteListItem, it.startTimeNanos, it.noteDurationNanos, it.count,
                        it.handlerDelayNanos
                    )
                }
            }
        }
    }

    /** Offer a new message of a started notes to trigger a call to the note started listener.
     * @note This only has an effect if the coroutineContext of the class is not null.
     * @param noteListItem NoteListItem which starts playing.
     * @param startTimeNanos System.nanoTime() when the note started playing.
     * @param noteDurationNanos Total note duration in nano seconds (as derived from the bpm).
     * @param count Note count of the started note, counting the notes played after starting plying.
     * @param handlerDelayNanos Delay of how long after the note start, the handler is called, in
     *   nanoseconds. If negative, the handler is called before the note starts.
     */
    fun offer(
        noteListItem: NoteListItem, startTimeNanos: Long, noteDurationNanos: Long, count: Long,
        handlerDelayNanos: Long
    ) {
        channel?.trySend(
            NoteStartedInfo(noteListItem, startTimeNanos, noteDurationNanos, count, handlerDelayNanos)
        )
    }

    /** Call this to disconnect the channel, when this class is not needed anymore.
     * This function is thread-safe. */
    fun destroy() {
        channel?.close()
    }
}

///** Task class which tells if a channel should be registered or unregistered.
// * @param noteStartedHandler Channel which should be registered or unregistered.
// * @param unregisterChannel If true, channel will be unregistered, if false, channel will be registered
// */
//private class NoteStartedChannelWithAddOrRemoveInfo(val noteStartedHandler: NoteStartedHandler,
//                                                    val unregisterChannel: Boolean)

/** Required information for handling the notifications when a playlist item starts.
 *  @param noteStartedHandler NoteStartedHandler where the info is sent.
 *  @param queuedNote Note with extra info to be started.
 */
private class NoteStartedHandlerAndQueuedNote(
    val noteStartedHandler: NoteStartedHandler,
    val queuedNote: QueuedNote
) {
    /** NoteListItem which will be started. */
    val noteListItem get() = queuedNote.noteListItem
    /** Frame number when the noteStartedListener should be called. */
    val frameNumber get() = queuedNote.startFrame
    /** Counter for played notes since player start. */
    val noteCount get() = queuedNote.noteCount
    val noteDurationNanos get() = queuedNote.noteDurationNanos
}

/** Class which stores tracks which are queued for the playing.
 * @param noteListItem NoteListItem which is queued.
 * @param startFrame Frame index when this note starts playing.
 * @param noteDurationNanos Note duration in nano seconds (as derived from bpm)
 * @param volume Track volume.
 * @param noteCount Counter for played notes since player start.
 */
private data class QueuedNote(
    val noteListItem: NoteListItem,
    val startFrame: Int,
    val noteDurationNanos: Long,
    val volume: Float,
    val noteCount: Long
    )

/** Info about next next note which must be queued for being played.
 * @param nextNoteIndex Note index in note list of the note to be played.
 * @param nextNoteFrame Frame, when the note should be played.
 * @param noteCount Absolute counter of notes, since starting playing.
 */
private data class NextNoteInfo(val nextNoteIndex: Int, val nextNoteFrame: Int, val noteCount: Long)

/** Class containing info for synchronize click time to a given reference.
 * @param referenceTimeNanos Time in uptime millis (from call to System.nanoTime())
 *   to which the first beat should be synchronized.
 * @param beatDurationInSeconds Duration in seconds for a beat. The playing is then synchronized such,
 *   that the first beat of the playlist is played at
 *      referenceTime + n * beatDuration
 *   where n is a integer number.
 */
private data class SynchronizeTimeInfo(val referenceTimeNanos: Long, val beatDurationInSeconds: Float)

/** We use not the minimum buffer size but scale it with this integer value.
 * A factor of 4 or larger seems to be necessary, to have noteStartedListeners registered early
 * enough to get a correct vibration/visualization behavior. */
private const val MIN_BUFFER_SIZE_FACTOR = 4

private val getLatencyMethod = try {
    AudioTrack::class.java.getMethod("getLatency")
} catch (e: NoSuchMethodException) {
    null
}

/** Converter between frame number since player start and system time in nano seconds.
 * @param sampleRate Player sample rate in Hz.
 * @param framePosition FramePosition at nanoTime.
 * @param nanoTime Time corresponding to framePosition in System.nanoTime()
 * @param synchronizedBy Info about how the frame-time synchronization took place.
 */
private data class FrameTimeConversion(val sampleRate: Int, val framePosition: Long, val nanoTime: Long, val synchronizedBy: SynchronizedBy) {
    enum class SynchronizedBy {TimeStamp, Latency, None}

    /** Convert frame number to nanoTime.
     * @param frameNumber Frame number to be converted.
     * @return time in nano seconds which corresponds to the given frame number.
     */
    fun frameToNanos(frameNumber: Int): Long {
        return ((1000_000_000L * (frameNumber - framePosition)) / sampleRate) + nanoTime
    }
    /** Convert frame nano time to frame number.
     * @param nanoTime Time in nano seconds that should be converted.
     * @return Frame number which corresponds to the given time.
     */
    fun nanosToFrames(nanoTime: Long): Int {
        return (((nanoTime - this.nanoTime) * sampleRate) / 1000_000_000L + framePosition).toInt()
    }

    class Factory {
        private val audioTimeStamp = AudioTimestamp()

        fun create(player: AudioTrack, oldValue: FrameTimeConversion?): FrameTimeConversion {
            val success = player.getTimestamp(audioTimeStamp)

            val sampleRate = player.sampleRate
            val framePosition: Long
            val nanoTime: Long
            var syncBy = SynchronizedBy.None

            if (success) {
                framePosition = audioTimeStamp.framePosition
                nanoTime = audioTimeStamp.nanoTime
                syncBy = SynchronizedBy.TimeStamp
//                Log.v("Metronome", "FrameNumberToMillis.Factory.create : conversion is based on time stamp")
            } else {
                if (getLatencyMethod != null) {
                    val latencyNanos =
                        (getLatencyMethod.invoke(player) as Int) * 1000_000L // getLatency returns latency is in millis
                    val bufferSizeNanos =
                        (player.bufferSizeInFrames * 1000_000_000L) / player.sampleRate
                    val latencyWithoutBufferSizeNanos = latencyNanos - bufferSizeNanos

                    framePosition = player.playbackHeadPosition.toLong()
                    nanoTime = System.nanoTime() + latencyWithoutBufferSizeNanos
                    syncBy = SynchronizedBy.Latency
//                    Log.v("Metronome", "FrameNumberToMillis.Factory.create : conversion is based on latency")
                } else {
                    framePosition = player.playbackHeadPosition.toLong()
                    nanoTime = System.nanoTime()
//                    Log.v("Metronome", "FrameNumberToMillis.Factory.create : conversion without syncrhonization info")
                }
            }
            return if (oldValue != null && framePosition != oldValue.framePosition
                && nanoTime != oldValue.nanoTime
                && syncBy != oldValue.synchronizedBy && sampleRate != oldValue.sampleRate
            ) {
                oldValue
            } else {
                FrameTimeConversion(sampleRate, framePosition, nanoTime, syncBy)
            }
        }
    }
}

private class NoteStartedHandling(private val scope: CoroutineScope) {
    /** Callback handlers when a note starts together. */
    private val noteStartedHandlers = ArrayList<NoteStartedHandler>()
    /** Mutex for accessing noteStartedHandlers. */
    private val noteStartedHandlersMutex = Mutex()

    /** Tasks definitions for sending noteStartedHandler-change requests. */
    private enum class Task {Register, Unregister, ChangeDelay}
    /** A task to register for a change of noteStartedHandlers. */
    private data class HandlerTask(val task: Task, val handler: NoteStartedHandler, val newDelay: Int? = null)
    /** The channel, through which we send tasks for changing noteStartedHandlers. */
    private val taskChannel = Channel<HandlerTask>(Channel.UNLIMITED)
    /** Job to handle noteStartedHandler-updates without blocking. */
    private val noteStartedHandlerUpdateJob = scope.launch(Dispatchers.IO) {
        taskChannel.consumeEach { task ->
            noteStartedHandlersMutex.withLock {
                when (task.task) {
                    Task.Register -> {
                        if (!noteStartedHandlers.contains(task.handler))
                            noteStartedHandlers.add(task.handler)
                    }
                    Task.Unregister -> {
                        noteStartedHandlers.remove(task.handler)
                    }
                    Task.ChangeDelay -> {
                        task.newDelay?.let { delay ->
                            noteStartedHandlers.filter { it === task.handler }
                                .forEach { it.delayInMillis = delay }
                        }
                    }
                }
                maxNegativeDelayInMillis = max(0, -(noteStartedHandlers.minOfOrNull { it.delayInMillis } ?: 0))
            }
        }
    }.apply { invokeOnCompletion { noteStartedHandlers.forEach { it.destroy() } } }

    @Volatile
    var maxNegativeDelayInMillis = 0
        private set

    inner class JobCommunication(
        val job: Job,
        val queuedNoteChannel: SendChannel<QueuedNote?>,
        val frameTimeConversionChannel: SendChannel<FrameTimeConversion>
    ) {
        val maxNegativeDelayInMillis get() = this@NoteStartedHandling.maxNegativeDelayInMillis

        fun cancel() {
            job.cancel()
        }
    }
    private val noteStartedDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r).apply {
            priority = Thread.MAX_PRIORITY
            name = "Note started dispatcher"

        }
    }.asCoroutineDispatcher()

    fun destroy() {
        noteStartedHandlerUpdateJob.cancel()
        noteStartedDispatcher.cancel()
    }

    fun createNoteStartedHandlerJob(): JobCommunication {
        val queuedNotesChannel = Channel<QueuedNote?>(Channel.UNLIMITED)
        val frameTimeConversionChannel = Channel<FrameTimeConversion>(Channel.CONFLATED)

        val job = scope.launch(noteStartedDispatcher) {
            val queuedNoteStartedHandlers = ArrayList<NoteStartedHandlerAndQueuedNote>()
            var frameTimeConversion = frameTimeConversionChannel.receive()

            while (isActive) {
                delay(1)

                // update frame-to-milliseconds converter
                frameTimeConversionChannel.tryReceive().getOrNull()?.let { frameTimeConversion = it }

                // obtain and store all newly available notes, queued for playing
                while(true) {
                    val value = queuedNotesChannel.tryReceive()
                    if (value.isSuccess) {
                        val receivedValue = value.getOrNull()
                        // if a "null" was sent, this means that all previously queued notes have been
                        // discarded and won't play any further. In this case we must clear the list.
                        if (receivedValue == null) {
                            queuedNoteStartedHandlers.clear()
                        } else {
                            noteStartedHandlersMutex.withLock {
                                noteStartedHandlers.forEach {
                                    queuedNoteStartedHandlers.add(
                                        NoteStartedHandlerAndQueuedNote(it, receivedValue)
                                    )
                                }
                            }
                        }
                    } else {
                        break
                    }
                }

                val timeNanos = System.nanoTime()
                // val frameNumber = frameTimeConversion.millisToFrames(timeMillis)
                queuedNoteStartedHandlers.filter {
                    val frameNumber = frameTimeConversion.nanosToFrames((timeNanos - it.noteStartedHandler.delayInMillis * 1000_000L))
                    (it.frameNumber <= frameNumber
                            || it.noteStartedHandler.callbackWhen == NoteStartedHandler.CallbackWhen.NoteQueued)
                }.forEach {
                    val noteListItemCopy = it.noteListItem.clone()
                    val timeNanosOfNote = frameTimeConversion.frameToNanos(it.frameNumber)
                    if (it.noteStartedHandler.coroutineContext == null) {
                        it.noteStartedHandler.noteStartedListener.onNoteStarted(
                            noteListItemCopy, timeNanosOfNote, it.noteDurationNanos, it.noteCount,
                            it.noteStartedHandler.delayInMillis * 1000_000L
                        )
                    } else {
                        it.noteStartedHandler.offer(
                            noteListItemCopy, timeNanosOfNote, it.noteDurationNanos, it.noteCount,
                            it.noteStartedHandler.delayInMillis * 1000_000L
                        )
                    }
                }
                queuedNoteStartedHandlers.removeAll {
                    val frameNumber = frameTimeConversion.nanosToFrames((timeNanos - it.noteStartedHandler.delayInMillis * 1000_000L))
                    (it.frameNumber <= frameNumber
                            || it.noteStartedHandler.callbackWhen == NoteStartedHandler.CallbackWhen.NoteQueued)
                }
            }
        }

        return JobCommunication(job, queuedNotesChannel, frameTimeConversionChannel)
    }

    /** Create and register a handler to react to note-started events.
     * @param initialDelayInMillis Delay in milli seconds, when the listener should be called
     *   before or after the note ist played.
     * @param callbackWhen Defines, when the noteStartedListener is actually called, either as
     *   early as possible, when the note is queued (NoteStartedHandler.CallbackWhen.NoteQueued)
     *   or when the note actually starts playing (NoteStartedHandler.CallbackWhen.NoteStarted)
     * @param coroutineContext Context within which the listener should be called or null, to
     *   directly call the listener without a surrounding coroutine.
     * @param noteStartedListener Callback method to be called.
     * @return NoteStartedHandler, which can be used to change the delay or to unregister
     *   the handler again.
     */
    fun createAndRegisterHandler(
        initialDelayInMillis: Int = 0, callbackWhen: NoteStartedHandler.CallbackWhen,
        coroutineContext: CoroutineContext?,
        noteStartedListener: AudioMixer.NoteStartedListener): NoteStartedHandler {
        val noteStartedHandler = NoteStartedHandler(
            initialDelayInMillis, noteStartedListener, callbackWhen, coroutineContext, scope
        )
        taskChannel.trySend(HandlerTask(Task.Register, noteStartedHandler))
        return noteStartedHandler
    }

    /** Unregister a NoteStartedHandler.
     * @param noteStartedHandler NoteStartedChannel to be unregistered.
     */
    fun unregisterNoteStartedHandler(noteStartedHandler: NoteStartedHandler?) {
        noteStartedHandler?.let {
            it.destroy()
            taskChannel.trySend(HandlerTask(Task.Unregister, noteStartedHandler))
        }
    }

    fun changeDelay(noteStartedHandler: NoteStartedHandler, newDelay: Int) {
        taskChannel.trySend(HandlerTask(Task.ChangeDelay, noteStartedHandler, newDelay))
    }
}

private class Mixer(context: Context, val scope: CoroutineScope) {

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

    @Volatile
    var isMute = false
        set(value) {
            require(Looper.getMainLooper().isCurrentThread) // volatile variable should only be set from the same thread to avoid data races
            field = value
        }

    @Volatile
    var bpmQuarter = -1f
        set(value) {
            require(Looper.getMainLooper().isCurrentThread) // volatile variable should only be set from the same thread to avoid data races
            field = value
        }

    /** Note list with notes which are played in a loop. */
    var noteList = ArrayList<NoteListItem>()
        set(value) {
            // we must make sure that during copy the original is not changed, so we do the copy on the main thread and request that it is called on the main thread
            require(Looper.getMainLooper().isCurrentThread)
            scope.launch(Dispatchers.Main) {
                noteListLock.withLock {
                    deepCopyNoteList(value, field)
                    noteListHash += 1
                }
            }
        }

    /** Lock which is active when we access the noteList. */
    private val noteListLock = Mutex()
    /** Hash, tracking changed on the note list ... so we can don't have to compare the whole list all the time */
    @Volatile
    private var noteListHash = 0L

    /** Class for communicating with the job.
     * @param job The job itself.
     * @param nextNoteIndexModificationChannel Channel for modifying the index of the next note to
     *   be played.
     * @param restartPlayingNoteListChannel Channel through which we can reset the player to
     *   immediately start playing the current note list from the beginning.
     * @param synchronizeTimeChannel Channel for transferring synchronising information to the
     *   playing coroutine.
     */
    class JobCommunication(
        val job: Job,
        private val nextNoteIndexModificationChannel: SendChannel<Int>,
        private val restartPlayingNoteListChannel: SendChannel<Boolean>,
        private val synchronizeTimeChannel: SendChannel<SynchronizeTimeInfo>
    ) {
        fun setNextPlayedNoteListIndex(noteListIndex: Int) {
            nextNoteIndexModificationChannel.trySend(noteListIndex)
        }
        fun restartPlayingNoteList() {
            restartPlayingNoteListChannel.trySend(true)
        }
        fun synchronizeWithSystemNanos(synchronizeTimeInfo: SynchronizeTimeInfo) {
            synchronizeTimeChannel.trySend(synchronizeTimeInfo)
        }
        fun cancel() {
            job.cancel()
        }
    }

    init {
        // preload all samples for quicker player start
        scope.launch(Dispatchers.IO) {
            // first load with known native sample rate, then load for all sample rates
            val nativeSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            noteSamplesForDifferentSampleRates[nativeSampleRate]?.value

            for (n in noteSamplesForDifferentSampleRates)
                n.value.value
        }
    }

    fun createMixerJob(
        noteStartedJobCommunication: NoteStartedHandling.JobCommunication,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        restartHandler: () -> Unit
    ): JobCommunication {
        // Channel for modifying the index of the next note to be played.
        val nextNoteIndexModificationChannel = Channel<Int>(Channel.CONFLATED)
        // Channel through which we can reset the player to immediately start playing the current
        // note list from the beginning.
        val restartPlayingNoteListChannel = Channel<Boolean>(Channel.CONFLATED)
        // Channel for transferring our synchronising information to the playing coroutine.
        val synchronizeTimeChannel = Channel<SynchronizeTimeInfo>(Channel.CONFLATED)

        val job = scope.launch(dispatcher) {

            val player = createPlayer()
            val noteSamples = noteSamplesForDifferentSampleRates[player.sampleRate]!!.value
            val queuedNotes = ArrayList<QueuedNote>(32)

            val mixingBufferSize = player.bufferSizeInFrames / 4
            val mixingBuffer = FloatArray(mixingBufferSize)

            // we must enqueue notes early enough, such that the noteStartedHandlers can react on time
            var prefetchToQueueInFrames = (
                    noteStartedJobCommunication.maxNegativeDelayInMillis * player.sampleRate
                    ) / 1000
            // Total number of frames for which mixed and wrote to audio sink.
            var numMixedFrames = 0
            // Total number of frames which are already considered for queued notes.
            var numQueuedFrames = 0
            var nextNoteInfo = NextNoteInfo(0, 1 + prefetchToQueueInFrames, 0)

            val noteListCopy = ArrayList<NoteListItem>()
            var noteListCopyHash = -1L // hash of the note list, for quick check if there are changes.

            val frameTimeConversionFactory = FrameTimeConversion.Factory()
            var frameTimeConversion = frameTimeConversionFactory.create(player, null)
            noteStartedJobCommunication.frameTimeConversionChannel.send(frameTimeConversion)

            player.positionNotificationPeriod = ((30 * player.sampleRate) / mixingBufferSize) * mixingBufferSize // sync frames and time roughly each 30 seconds ..
            player.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {}
                override fun onPeriodicNotification(track: AudioTrack?) {
                    try {
                        track?.let {
                            val oldFrameTimeConversion = frameTimeConversion
                            frameTimeConversion = frameTimeConversionFactory.create(player, oldFrameTimeConversion)
                            if (oldFrameTimeConversion != frameTimeConversion)
                                noteStartedJobCommunication.frameTimeConversionChannel.trySend(frameTimeConversion)
                        }
                    } catch (_: java.lang.Exception) {

                    }
                }
            })

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
                    if(deviceInfo?.id != deviceId && audioRoutingChangeRequiresNewPlayerCheck(sampleRate, bufferSize))
                            restartHandler()
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

                // synchronize our timer very early but not too early, afterward we do a periodic
                // sync in large time periods
                if (frameTimeConversion.synchronizedBy != FrameTimeConversion.SynchronizedBy.TimeStamp
                    && numMixedFrames < 10 * player.bufferSizeInFrames) {
                    val oldFrameTimeConversion = frameTimeConversion
                    frameTimeConversion = frameTimeConversionFactory.create(player, oldFrameTimeConversion)
                    if (oldFrameTimeConversion != frameTimeConversion)
                        noteStartedJobCommunication.frameTimeConversionChannel.send(frameTimeConversion)
                }

                // update our local noteList copy
                if (noteListCopyHash != noteListHash) {
                    if (noteListLock.tryLock()) {
                        try {
                            deepCopyNoteList(noteList, noteListCopy)
                            noteListCopyHash = noteListHash
                        } finally {
                            noteListLock.unlock()
                        }
                    }
                }

                nextNoteIndexModificationChannel.tryReceive().getOrNull()?.let { index ->
                    nextNoteInfo = nextNoteInfo.copy(nextNoteIndex = index)
                }

                restartPlayingNoteListChannel.tryReceive().getOrNull()?.let {
                    nextNoteInfo = nextNoteInfo.copy(nextNoteFrame = numMixedFrames, nextNoteIndex = 0)
                    queuedNotes.clear()
                    noteStartedJobCommunication.queuedNoteChannel.trySend(null)
                }

                synchronizeTimeChannel.tryReceive().getOrNull()?.let { synchronizeTimeInfo ->
                    // at synchronizing there possible must be played a note immediately,
                    // if this is the case, it will be stored in nextNoteInfos[0] and an additional entry will be in the list.
                    val nextNoteInfos = synchronizeWithSystemNanos(
                        synchronizeTimeInfo, noteListCopy, bpmQuarter, nextNoteInfo,
                        player.sampleRate, numQueuedFrames, frameTimeConversion
                    )
                    if (nextNoteInfos.size > 1 && nextNoteInfos[0].nextNoteFrame == numQueuedFrames) {
                        val noteListItem = noteList[nextNoteInfos[0].nextNoteIndex]
                        queueSingleNote(
                            noteListItem,
                            nextNoteInfos[0].nextNoteFrame,
                            max(0L, noteListItem.duration.durationInNanos(bpmQuarter)),
                            nextNoteInfos[0].noteCount,
                            noteStartedJobCommunication.queuedNoteChannel, queuedNotes
                        )
                    }
                    nextNoteInfo = nextNoteInfos.last()
                }

                prefetchToQueueInFrames = (noteStartedJobCommunication.maxNegativeDelayInMillis * player.sampleRate) / 1000
                val queueNotesUpToFrame = numMixedFrames + mixingBuffer.size + prefetchToQueueInFrames
                val numFramesToQueue = queueNotesUpToFrame - numQueuedFrames

                // side effects of following function:
                // - to "queuedNoteChannel" we will send the queued notes.
                // - to "queuedNotes", we add the notes which are queued.
                nextNoteInfo = queueNextNotes(nextNoteInfo, noteListCopy, bpmQuarter, numQueuedFrames,
                    numFramesToQueue, noteStartedJobCommunication.queuedNoteChannel, queuedNotes,
                    player.sampleRate)
                numQueuedFrames += numFramesToQueue

                // fill the mixing buffer with our mixed sound samples.
                // - side effects: when a queued note fully added to the mixing buffer, it
                //    will be removed from the queued notes
                mixQueuedNotes(mixingBuffer, numMixedFrames, queuedNotes, noteSamples)
                numMixedFrames += mixingBuffer.size

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

        return JobCommunication(
            job,
            nextNoteIndexModificationChannel,
            restartPlayingNoteListChannel,
            synchronizeTimeChannel
        )
    }

    companion object {
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

        /** Create a new audio track instance.
         * @return Audio track instance.
         */
        private fun createPlayer(): AudioTrack {
            val nativeSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            val bufferSize = MIN_BUFFER_SIZE_FACTOR * AudioTrack.getMinBufferSize(nativeSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)

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

        /** Check if we need to recreate a player since the audio sink properties changed.
         * @param sampleRate Currently used sample rate
         * @param bufferSizeInFrames Currently used buffer sie in frames (we assume, that PCM_FLOAT is used)
         * @return True, if we should recreate the player
         */
        private fun audioRoutingChangeRequiresNewPlayerCheck(sampleRate: Int, bufferSizeInFrames:Int) : Boolean {
            val nativeSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            // here we skip the minBufferSizeFactor, since we only want to restart the player if really needed!
            val bufferSizeInBytes = AudioTrack.getMinBufferSize(nativeSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            //    Log.v("Metronome", "audioRoutingChangesRequiresNewPlayer: nativeSampleRate=$nativeSampleRate, currentSampleRate=${sampleRate}, bufferSize=$bufferSizeInBytes, currentBufferSize=${4*bufferSizeInFrames}")
            return (sampleRate != nativeSampleRate || bufferSizeInBytes > 4 * bufferSizeInFrames)
        }

        /** Synchronize first beat to note list to given time and beat duration
         * @param synchronizeTimeInfo Instance with the information how to synchronize.
         * @param noteList Note list which is currently played.
         * @param bpmQuarter Metronome speed in quarter notes per minute
         * @param nextNoteInfo Info about the next note which is about to be queued
         * @param sampleRate Sample rate in Hz
         * @param alreadyQueuedFrames Frame number up to which we did already queued the notes.
         * @param frameTimeConversion Conversion between uptime millis and frame number.
         * @return Info about next notes to be played.
         */
        private fun synchronizeWithSystemNanos(
            synchronizeTimeInfo: SynchronizeTimeInfo,
            noteList: ArrayList<NoteListItem>,
            bpmQuarter: Float,
            nextNoteInfo: NextNoteInfo,
            sampleRate: Int,
            alreadyQueuedFrames: Int,
            frameTimeConversion: FrameTimeConversion
        ): Array<NextNoteInfo> {
            if (noteList.isEmpty())
                return arrayOf(nextNoteInfo)

            var nextNoteIndex = nextNoteInfo.nextNoteIndex
            val nextNoteFrame = nextNoteInfo.nextNoteFrame
            var noteCount = nextNoteInfo.noteCount

            // reference time, for the first note of the play list to be played. Actually the time of the
            // first note would be timeOfFirstNoteInFrames = referenceTimeInFrames + i * beatDurationInFrames
            // where i is an integer number.
            val referenceTimeInFrames = frameTimeConversion.nanosToFrames(synchronizeTimeInfo.referenceTimeNanos)
            val beatDurationInFrames = (synchronizeTimeInfo.beatDurationInSeconds * sampleRate).roundToInt()
//            Log.v("Metronome", "next note frame = $nextNoteFrame, reference time in frames = $referenceTimeInFrames, diff=${nextNoteFrame-referenceTimeInFrames}")
            if (nextNoteInfo.nextNoteIndex >= noteList.size)
                nextNoteIndex = 0

            // determine reference time for the next note to be played. Actually the time of this note must be
            // timeOfNextNote = referenceTimeForNextNoteListItem + i * beatDurationInFrames
            // where i is a integer number.
            var referenceFrameForNextNoteListItem = referenceTimeInFrames

            for (i in 0 until nextNoteIndex)
                referenceFrameForNextNoteListItem += (max(0f, noteList[i].duration.durationInSeconds(bpmQuarter)) * sampleRate).roundToInt()

            // find the closest possible time in frames for our next note to be played, relative to the
            // previously used frame.
            // The three different values in the following must be considered, since integer divisions
            // of positive/negative numbers can occur and depending on this we get different behaviors.
            // So we we first remove multiples of beatDurations and then check if we remove one beatDuration
            // less or more ist closer.
            val closeFrameForNextNoteListItemInit = referenceFrameForNextNoteListItem - beatDurationInFrames * ((referenceFrameForNextNoteListItem - nextNoteFrame) / beatDurationInFrames)
            val closeFrameDistanceInit = (closeFrameForNextNoteListItemInit - nextNoteFrame).absoluteValue
            val closeFrameForNextNoteListItemUpper = closeFrameForNextNoteListItemInit + beatDurationInFrames
            val closeFrameDistanceUpper = (closeFrameForNextNoteListItemUpper - nextNoteFrame).absoluteValue
            val closeFrameForNextNoteListItemLower = closeFrameForNextNoteListItemInit - beatDurationInFrames
            val closeFrameDistanceLower = (closeFrameForNextNoteListItemLower - nextNoteFrame).absoluteValue

            var correctedFrameForNextNote = if (closeFrameDistanceUpper < closeFrameDistanceInit)
                closeFrameForNextNoteListItemUpper
            else if (closeFrameDistanceLower < closeFrameDistanceInit)
                closeFrameForNextNoteListItemLower
            else
                closeFrameForNextNoteListItemInit

            var noteToBeQueuedImmediately: NextNoteInfo? = null
            // we now have the time when the nextNoteListItem should actually be played,
            // however it is possible that this should be played earlier than expected. In this case
            // we play it directly, and directly queue the next note.
            // But, it is possible that we are very late, such that even the note after the next note would
            // be played late, so we skip so many notes, until the next note takes place later than the
            // already queued frames.
            while (correctedFrameForNextNote < alreadyQueuedFrames) {
                val timeInFramesForNoteAfterNextNote = correctedFrameForNextNote + (max(0f, noteList[nextNoteIndex].duration.durationInSeconds(bpmQuarter)) * sampleRate).roundToInt()

                if (timeInFramesForNoteAfterNextNote > alreadyQueuedFrames) {
                    noteToBeQueuedImmediately = NextNoteInfo(nextNoteIndex, alreadyQueuedFrames, noteCount)
                    correctedFrameForNextNote = timeInFramesForNoteAfterNextNote
                    ++nextNoteIndex
                    if (nextNoteIndex >= noteList.size)
                        nextNoteIndex -= noteList.size
                }
                ++noteCount
            }

            val updatedNextNoteInfo = NextNoteInfo(nextNoteIndex, correctedFrameForNextNote, noteCount)
            return if (noteToBeQueuedImmediately == null)
                arrayOf(updatedNextNoteInfo)
            else
                arrayOf(noteToBeQueuedImmediately, updatedNextNoteInfo)
        }

        /** Add a single note to our note queue and listeners.
         * @param noteListItem Note list item to be queued
         * @param noteFrame Frame at which the note should be queued.
         * @param noteDurationNanos Total note duration in nano seconds (as derived from the bpm).
         * @param noteCount Count to be used for messaging the number of played notes since start of playing.
         * @param queuedNoteStartedChannel Channel wehre we send the newly queued note with some extra infos.
         * @param queuedNotes This is the queue where we add our notes.
         */
        private fun queueSingleNote(
            noteListItem: NoteListItem,
            noteFrame: Int,
            noteDurationNanos: Long,
            noteCount: Long,
            queuedNoteStartedChannel: SendChannel<QueuedNote?>,
            queuedNotes: ArrayList<QueuedNote>
        ) {
            val queuedNote = QueuedNote(noteListItem, noteFrame, noteDurationNanos, noteListItem.volume, noteCount)
            queuedNotes.add(queuedNote)
            queuedNoteStartedChannel.trySend(queuedNote)
        }

        /** Put notes in to queue, which will be played.
         * @param nextNoteInfo Info about the next note, that will be put into the queue.
         * @param noteList Contains all notes to be played.
         * @param bpmQuarter Quarter notes per minute
         * @param alreadyQueuedFrames Frame number up to which we did already queued the notes.
         * @param numFramesToQueue Number of frames after alreadyQueuedFrames, for which we should queue the notes.
         * @param queuedNoteStartedChannel Channel where we send the newly queued notes with some extra infos.
         * @param queuedNotes This is the queue where we add our notes.
         * @param sampleRate Currently used sample rate
         * @return An updated nextNoteInfo which serves as input to this function on the next cycle.
         */
        private fun queueNextNotes(
            nextNoteInfo: NextNoteInfo,
            noteList: ArrayList<NoteListItem>,
            bpmQuarter: Float,
            alreadyQueuedFrames: Int,
            numFramesToQueue: Int,
            queuedNoteStartedChannel: SendChannel<QueuedNote?>,
            queuedNotes: ArrayList<QueuedNote>,
            sampleRate: Int
        ) : NextNoteInfo{

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
                queueSingleNote(
                    noteListItem,
                    nextNoteFrame,
                    max(0L, noteListItem.duration.durationInNanos(bpmQuarter)),
                    noteCount,
                    queuedNoteStartedChannel,
                    queuedNotes)

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
                val noteId = queuedItem.noteListItem.id
                val noteSamplePosition = max(0, nextFrameToMix - queuedItem.startFrame)
                val mixingBufferPosition = max(0, queuedItem.startFrame - nextFrameToMix)
                val volume = queuedItem.volume
                val samples = noteSamples[noteId]

                val numSamplesToWrite = max(0, min(mixingBuffer.size - mixingBufferPosition, samples.size - noteSamplePosition))

                for (j in 0 until numSamplesToWrite) {
                    mixingBuffer[mixingBufferPosition + j] += volume * samples[noteSamplePosition + j]
                }
            }

            val lastMixedFrame = nextFrameToMix + mixingBuffer.size
            queuedNotes.removeAll { lastMixedFrame >= it.startFrame + noteSamples[it.noteListItem.id].size }
        }
    }
}

/** Audio mixer class which mixes and plays a note list.
 * @param context Context needed for obtaining the note samples
 * @param scope Coroutine scope inside which we will start the player.
 */
class AudioMixer (val context: Context, private val scope: CoroutineScope) {
    /** Interface for listener which is used when a new note list item starts. */
    fun interface NoteStartedListener {
        /** Callback function which is called when a playlist item starts
         * @param noteListItem Note list item which is started.
         * @param timeNanos Result of System.nanoTime() when the note is started.
         * @param durationNanos Total note duration in nano seconds (as derived from the bpm)
         * @param noteCount Counter for notes since start of playing
         * @param handlerStartDelayNanos Delay when handler is called after the note starts playing.
         *   Can be negative if the handler is called before the note starts playing.
         */
        fun onNoteStarted(
            noteListItem: NoteListItem?, timeNanos: Long, durationNanos: Long, noteCount: Long,
            handlerStartDelayNanos: Long
        )
    }

    private val noteStartedHandling = NoteStartedHandling(scope)
    private val mixer = Mixer(context, scope)

    /** Job which does the playing. */
    private var mixerJob: Mixer.JobCommunication? = null
    /** Job and structures for handling callbacks when a note is started. */
    private var noteStartedJob: NoteStartedHandling.JobCommunication? = null

    /** Create a new NoteStartedChannel.
     * @warning When this is not needed anymore, you MUST call unregisterNoteStartedChannel
     * @info The delay can be changed later on by calling noteStartedChannel.changeDelay(...)
     * @param initialDelayInMillis The NoteStartedListener will be started with the given delay after the
     *   note starts playing. Can be negative, in order to call this before the note starts.
     * @param callbackWhen Defines, when the noteStartedListener is actually called, either as
     *   early as possible, when the note is queued (NoteStartedHandler.CallbackWhen.NoteQueued)
     *   or when the note actually starts playing (NoteStartedHandler.CallbackWhen.NoteStarted)
     * @param coroutineContext Context within which the noteStartedListener should be called or
     *   null if the listener should be called directly. Only use null, if the listener returns
     *   immediately and if the calling thread is not important.
     * @return A new NoteStartedChannel.
     */
    fun createAndRegisterNoteStartedHandler(
        initialDelayInMillis: Int = 0,
        callbackWhen: NoteStartedHandler.CallbackWhen,
        coroutineContext: CoroutineContext?,
        noteStartedListener: NoteStartedListener
    ): NoteStartedHandler {
        return noteStartedHandling.createAndRegisterHandler(
            initialDelayInMillis, callbackWhen, coroutineContext, noteStartedListener
        )
    }

    fun changeDelayOfNoteStartedHandler(
        noteStartedHandler: NoteStartedHandler?,
        delayInMillis: Int
    ) {
        noteStartedHandler?.let {
            noteStartedHandling.changeDelay(it, delayInMillis)
        }
    }
    /** Unregister a NoteStartedChannel.
     * @param noteStartedHandler NoteStartedChannel to be unregistered.
     */
    fun unregisterNoteStartedChannel(noteStartedHandler: NoteStartedHandler?) {
        noteStartedHandling.unregisterNoteStartedHandler(noteStartedHandler)
    }

    /** Set metronome speed.
     * @param bpmQuarter Beats per minute of a quarter note.
     */
    fun setBpmQuarter(bpmQuarter: Float) {
        mixer.bpmQuarter = bpmQuarter
    }

    /** (Un)mute the metronome.
     * @param isMute Mute state.
     */
    fun setMute(isMute: Boolean) {
        mixer.isMute = isMute
    }

    /** Update the note list to be played.
     * @param noteList New note list to be played.
     */
    fun setNoteList(noteList: ArrayList<NoteListItem>) {
        mixer.noteList = noteList
    }

    /** Start playing the note list from the beginning. */
    fun restartPlayingNoteList() {
        mixerJob?.restartPlayingNoteList()
    }

    /** Modify the index of the next note to be played.
     * @param index Index of note in the currently played note list.
     */
    fun setNextNoteIndex(index: Int) {
        mixerJob?.setNextPlayedNoteListIndex(index)
    }

    /** Synchronize first beat to note list to given time and beat duration.
     * @param referenceTimeNanos Time in millis (from call to System.nanoTime())
     *   to which the first beat should be synchronized.
     * @param beatDurationInSeconds Duration in seconds for a beat. The playing is then synchronized such,
     *   that the first beat of the playlist is played at
     *      referenceTime + n * beatDuration
     *   where n is a integer number.
     */
    fun synchronizeWithSystemNanos(referenceTimeNanos: Long, beatDurationInSeconds: Float) {
        mixerJob?.synchronizeWithSystemNanos(SynchronizeTimeInfo(referenceTimeNanos, beatDurationInSeconds))
    }

    /** Start playing. */
    fun start() {
        stop() // make sure, we have no two players at the same time

        val newNoteStartedJob = noteStartedHandling.createNoteStartedHandlerJob()
        noteStartedJob = newNoteStartedJob
        mixerJob = mixer.createMixerJob(newNoteStartedJob, Dispatchers.Default) { restart() }
    }

    /** Stop playing. */
    fun stop() {
        noteStartedJob?.cancel()
        noteStartedJob = null
        mixerJob?.cancel()
        mixerJob = null
    }

    /** Restart player. */
    private fun restart() {
        stop()
        start()
    }

    /** Close all background jobs and stuff, when the class is not needed anymore. */
    fun destroy() {
        noteStartedHandling.destroy()
    }
}
