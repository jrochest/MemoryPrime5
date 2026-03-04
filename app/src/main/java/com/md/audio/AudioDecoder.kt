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
        if (!file.exists()) {
            android.util.Log.w("AudioDecoder", "File not found: $filePath")
            return null
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
        } catch (e: Exception) {
            android.util.Log.e("AudioDecoder", "Failed to set data source for $filePath", e)
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
            android.util.Log.w("AudioDecoder", "No audio track found in file: $filePath")
            extractor.release()
            return null
        }

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        // These are the input format values — may differ from actual output
        // (e.g. HE-AAC SBR doubles sample rate and can change channels)
        var actualSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var actualChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

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
                // Codec output format may differ from input (HE-AAC SBR, etc.)
                // Use the ACTUAL output format for resampling and channel conversion.
                val outputFormat = codec.outputFormat
                actualSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                actualChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                android.util.Log.d("AudioDecoder", "Output format changed: sampleRate=$actualSampleRate, channels=$actualChannels")
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

        android.util.Log.d("AudioDecoder", "Decoded ${pcmData.size} samples at ${actualSampleRate}Hz, ${actualChannels}ch. Target: ${targetSampleRate}Hz mono.")

        val floatData = FloatArray(pcmData.size) { pcmData[it].toFloat() }
        val monoData = if (actualChannels >= 2) {
            val mono = FloatArray(floatData.size / actualChannels)
            for (i in mono.indices) {
                var sum = 0f
                for (ch in 0 until actualChannels) {
                    sum += floatData[i * actualChannels + ch]
                }
                mono[i] = sum / actualChannels
            }
            mono
        } else {
            floatData
        }

        // Resample from actual output rate to target rate
        val resampledData = resample(monoData, actualSampleRate, targetSampleRate)
        android.util.Log.d("AudioDecoder", "After downmix+resample: ${resampledData.size} samples (${resampledData.size / targetSampleRate}s at ${targetSampleRate}Hz)")
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
