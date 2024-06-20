package com.md

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.lifecycle.lifecycleScope
import com.md.AudioPlayer.Companion.sanitizePath
import com.md.composeModes.SettingsModeStateModel
import com.md.modesetters.TtsSpeaker.error
import com.md.utils.ToastSingleton
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Random
import javax.inject.Inject
import kotlin.math.max

class AudioRecorder @Inject constructor(
    @ActivityContext val context: Context,
    private val audioPlayer: AudioPlayer,
    private val stateModel: SettingsModeStateModel,
) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }
    private var recorder: MediaRecorder? = null
    private val path: String
    val generatedAudioFileNameWithExtension: String = (System.currentTimeMillis() / 100).toString() + sessionSuffixTwoDigitNumberWithExtension
    var isRecorded = false
    var isRecording = false
    var maxRecordingAmplitude = 0

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
    @Throws(java.lang.Exception::class)
    fun stop() {
        try {
            isRecording = false
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

        } else if (maxRecordingAmplitude < 900) {
            // When I whisper reasonably loud the level is about 2000.
            audioFileExists.delete()
            throw RecordingTooQuiet()
        } else {
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
            val localRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                 MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            recorder = localRecorder
            // This sounds better than default with Kimura boom mic.
            localRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            localRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            // I switched to this and test just with the Shokz headphones. Be ready to revert.
            localRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC_ELD)
            localRecorder.setAudioChannels(1)
            localRecorder.setAudioEncodingBitRate(128000)
            localRecorder.setAudioSamplingRate(44100)
            localRecorder.setOutputFile(path)
            localRecorder.prepare()
            localRecorder.start()
            localRecorder.preferredDevice = stateModel.preferredMic.value
            isRecording = true

            activity.lifecycleScope.launch {
                while (isRecording) {
                    maxRecordingAmplitude = max(localRecorder.maxAmplitude, maxRecordingAmplitude)
                    delay(300)
                }
            }
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