package com.md

import android.media.MediaRecorder
import android.os.Environment
import com.md.AudioPlayer.Companion.sanitizePath
import com.md.modesetters.TtsSpeaker.error
import com.md.utils.ToastSingleton
import java.io.File
import java.io.IOException
import java.util.Random
import javax.inject.Inject

class AudioRecorder @Inject constructor(
    private val audioPlayer: AudioPlayer,
) {
    private var recorder: MediaRecorder? = null
    private val path: String
    val generatedAudioFileNameWithExtension: String = (System.currentTimeMillis() / 100).toString() + sessionSuffixTwoDigitNumberWithExtension
    var isRecorded = false

    /** AudioRecorder that generates its own new from current time and suffix  */
    init {
        // Force the last two digits of the time to be the same to always write to the same dir.
        // To allow backups to exhibit some temporal locality and decrease the number of directory
        // specific zips that need to be updated.
        path = sanitizePath(generatedAudioFileNameWithExtension)
    }

    fun deleteFile() {
        if (isRecorded) {
            isRecorded = false
            val file = File(path)
            file.delete()
        }
    }

    /**
     * Stops a recording that has been previously started.
     */
    @Throws(IOException::class)
    fun stop() {
        try {
            //TtsSpeaker.speak("Max amp is: " + recorder.getMaxAmplitude());
            recorder!!.stop()
        } catch (e: Exception) {
            print("Error during stop and release$e")
        } finally {
            try {
                recorder!!.release()
                val audioFileExists = File(path)
                if (audioFileExists.length() > 4_000_000) {
                    // Typically this means that the recording didn't stop correctly.
                    audioFileExists.delete()
                }
            } catch (e: Exception) {
                print("Removed in finally$e")
            }
        }
        val audioFileExists = File(path)
        if (!audioFileExists.exists()) {
            ToastSingleton.getInstance().error(path + " does not exist!")
        } else if (audioFileExists.length() < 4000) {
            audioFileExists.delete()
            throw RecordingTooSmallException()
        } else {
            // TODOJNOW maybe check that recording volume and warn if too low.
            isRecorded = true
        }
    }

    /**
     * Starts a new recording.
     */
    @Throws(IOException::class)
    fun start() {
        start(false)
    }

    /**
     * Starts a new recording.
     */
    private fun start(isRetry: Boolean) {
        // Try write without checking if dir exists. Correct thing upon error.
        try {
            recorder = MediaRecorder()
            recorder!!.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            recorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)
            recorder!!.setAudioChannels(1)
            recorder!!.setAudioEncodingBitRate(128000)
            recorder!!.setAudioSamplingRate(44100)
            recorder!!.setOutputFile(path)
            recorder!!.prepare()
            recorder!!.start()
        } catch (e: Exception) {
            val state = Environment.getExternalStorageState()
            if (state != Environment.MEDIA_MOUNTED) {
                error("SD Card is not mounted.  It is $state")
            }
            // make sure the directory we plan to store the recording in exists
            val directory = File(path).parentFile
            if (!directory.exists() && !directory.mkdirs()) {
                error("Path to file could not be created.")
            }
            if (!isRetry) {
                // Retry once after directory creation.
                start(true)
            }
        }
    }

    suspend fun playFile() : Boolean {
       return audioPlayer.suspendPlayReturningTrueIfLoadedFileChanged(generatedAudioFileNameWithExtension)
    }

    companion object {
        private val sessionSuffixTwoDigitNumber = createSuffix()
        private val sessionSuffixTwoDigitNumberWithExtension = sessionSuffixTwoDigitNumber + ".m4a"
        private fun createSuffix(): String {
            val random = Random()

            // String with two digit ints between 00 and 99.
            return random.nextInt(10).toString() + "" + random.nextInt(10)
        }

        fun deleteFile(fileName: String?): Boolean {
            val path = sanitizePath(fileName!!)
            val file = File(path)
            return file.delete()
        }
    }
}