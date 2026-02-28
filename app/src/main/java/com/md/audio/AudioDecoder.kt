package com.md.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

class AudioDecoder {

    /**
     * Decodes an audio file into a raw 16-bit PCM ShortArray.
     * Extracts at the original sample rate and then resamples to targetSampleRate (e.g., 16000 for Whisper).
     */
    fun decodeToPcm(filePath: String, targetSampleRate: Int = 16000): ShortArray? {
        val file = File(filePath)
        if (!file.exists()) return null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        var format: MediaFormat? = null
        var trackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                format = f
                trackIndex = i
                break
            }
        }

        if (format == null || trackIndex == -1) {
            extractor.release()
            return null
        }

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val originalSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmData = mutableListOf<Short>()
        var isEOS = false
        val info = MediaCodec.BufferInfo()
        val timeoutUs = 10000L

        while (true) {
            if (!isEOS) {
                val inputBufferIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(info, timeoutUs)
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Ignore format change
            } else if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && info.size > 0) {
                    outputBuffer.position(info.offset)
                    outputBuffer.limit(info.offset + info.size)
                    val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val outData = ShortArray(shortBuffer.remaining())
                    shortBuffer.get(outData)
                    for (i in outData.indices) {
                        pcmData.add(outData[i])
                    }
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // Wait
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val floatData = FloatArray(pcmData.size) { pcmData[it].toFloat() }
        val monoData = if (channels == 2) {
            val mono = FloatArray(floatData.size / 2)
            for (i in mono.indices) {
                mono[i] = (floatData[i * 2] + floatData[i * 2 + 1]) / 2f
            }
            mono
        } else {
            floatData
        }

        // Resample
        val resampledData = resample(monoData, originalSampleRate, targetSampleRate)
        return ShortArray(resampledData.size) { resampledData[it].toInt().toShort() }
    }

    private fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (sourceRate == targetRate) return input
        val ratio = sourceRate.toDouble() / targetRate.toDouble()
        val outLength = (input.size / ratio).toInt()
        val output = FloatArray(outLength)
        for (i in 0 until outLength) {
            val inIndex = i * ratio
            val index1 = inIndex.toInt()
            val index2 = (index1 + 1).coerceAtMost(input.size - 1)
            val fraction = inIndex - index1
            output[i] = (input[index1] * (1.0 - fraction) + input[index2] * fraction).toFloat()
        }
        return output
    }
}
