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
import java.lang.RuntimeException
import java.nio.ByteOrder
import kotlin.math.ceil

fun audioToPCM(id : Int, context : Context) : FloatArray {

    val sampleFD = context.resources.openRawResourceFd(id)
    val mediaExtractor = MediaExtractor()
    mediaExtractor.setDataSource(sampleFD.fileDescriptor, sampleFD.startOffset, sampleFD.length)
    val format = mediaExtractor.getTrackFormat(0)

//    val mime = format.getString(MediaFormat.KEY_MIME)
    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val duration = format.getLong(MediaFormat.KEY_DURATION)
    val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    val mediaCodecName = mediaCodecList.findDecoderForFormat(format)

    // Log.v("AudioMixer", "AudioEncoder.decode: MIME TYPE: $mime")
    // Log.v("AudioMixer", "AudioEncoder.decode: duration $duration")
    // Log.v("AudioMixer", "AudioEncoder.decode: sampleRate = $sampleRate")
    // Log.v("AudioMixer", "AudioEncoder.decode: channel count $channelCount")
    // Log.v("AudioMixer", "AudioEncoder.decode: track count: " + mediaExtractor.trackCount)
    // Log.v("AudioMixer", "AudioEncoder.decode: media codec name: $mediaCodecName")

    val result = FloatArray((ceil((duration * sampleRate).toDouble() / 1000000.0)).toInt()) { 0f }
    // Log.v("AudioMixer", "AudioEncoder.decode: result.size = " + result.size)

    val codec = MediaCodec.createByCodecName(mediaCodecName)

    codec.configure(format, null, null, 0)
    codec.start()

    mediaExtractor.selectTrack(0)
    var numSamples = 0
    val bufferInfo = MediaCodec.BufferInfo()

    while (true) {

        // get input buffer index and then the input buffer itself
        val inputBufferIndex = codec.dequeueInputBuffer(300000)
        if (inputBufferIndex < 0) {
            throw RuntimeException("AudioEncoder.decode: failed to get input buffer index")
        }

        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
            ?: throw RuntimeException("AudioEncoder.decode: failed to acquire input buffer")

        // write the next bunch of data from our media file to the input buffer
        val sampleSize = mediaExtractor.readSampleData(inputBuffer, 0)

        // queue the input buffer such that the codec can decode it
        if (sampleSize < 0) {
            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        } else {
            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, mediaExtractor.sampleTime, 0)
            mediaExtractor.advance()
        }

        // we are done decoding and can now read our result
        var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 200000)

        // sometimes this output format changed appears, then we have to try again to get
        // the output buffer index again
        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // Log.v("AudioMixer", "AudioEncoder.decode: output format changed")
            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 200000)
        }

        // if something fails ....
        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // Log.v("AudioMixer", "AudioEncoder.decode: output format changed")
            throw RuntimeException("Cannot acquire valid output buffer index")
        } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            // Log.v("AudioMixer", "AudioEncoder.decode: try again later")
            throw RuntimeException("Cannot acquire valid output buffer index")
        }

        // finally get our output data and create a view to a short buffer which is the
        // standard data type for 16bit audio
        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
            ?: throw RuntimeException("Cannot acquire output buffer")

        val shortBuffer = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()

        // convert the short data to floats and store it to the result-array which will be
        // returned later. We want to have mono output stream, so we add different channel
        // to the same index.
        while (shortBuffer.position() < shortBuffer.limit()) {
            result[numSamples / channelCount] += shortBuffer.get().toFloat()
            ++numSamples
        }

        codec.releaseOutputBuffer(outputBufferIndex, false)
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            break
    }

    mediaExtractor.release()
    codec.release()

    val channelCountInv = 1.0f / channelCount
    for(i in result.indices)
        result[i] = channelCountInv * result[i] / 32768.0f //peak * peakValue
    // val peak = result.max() ?: 0f
    // Log.v("AudioMixer", "AudioEncoder.decode: peak value = $peak")
    return result
}

