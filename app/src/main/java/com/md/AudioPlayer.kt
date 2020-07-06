package com.md

import android.media.MediaPlayer
import android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN
import android.media.MediaPlayer.OnCompletionListener
import android.media.audiofx.LoudnessEnhancer
import com.md.modesetters.TtsSpeaker
import com.md.utils.ToastSingleton
import java.io.File
import java.io.IOException

class AudioPlayer : OnCompletionListener, MediaPlayer.OnErrorListener {
    var shouldRepeat: Boolean = false;
    private var mp: MediaPlayer? = null
    private var mFiredOnceCompletionListener: OnCompletionListener? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var lastFile: String? = null

    /**
     * @param originalFile name of the file, without the save path
     * @param firedOnceCompletionListener
     */
    @JvmOverloads
    fun playFile(originalFile: String? = lastFile,
                 firedOnceCompletionListener: OnCompletionListener? = null,
                 learningMode: Boolean = false,
                 playbackSpeed: Float = 1.5f) {

        if (originalFile == null) {
            ToastSingleton.getInstance().error("Null file path. You should probably delete this note Jacob.")
            return
        }

        lastFile = originalFile
        shouldRepeat = learningMode

        cleanUp()
        // Note: noise supressor seem to fail and say not enough memory. NoiseSuppressor.
        mFiredOnceCompletionListener = firedOnceCompletionListener
        val path = sanitizePath(originalFile)
        val audioFile = File(path)
        if (!audioFile.exists()) {
            ToastSingleton.getInstance().error("$path does not exist.")
            return
        }

        val mediaPlayer = MediaPlayer()
        mp = mediaPlayer
        try {
            mediaPlayer.setDataSource(path)
            loudnessEnhancer = LoudnessEnhancer(mediaPlayer.audioSessionId)
            loudnessEnhancer!!.setTargetGain(700)
            loudnessEnhancer!!.enabled = true

            mediaPlayer.setOnErrorListener(this)
            mediaPlayer.setOnCompletionListener(this)
            mediaPlayer.setOnPreparedListener({
                setSpeed(mediaPlayer, playbackSpeed)
                mediaPlayer.start()
            })
            mediaPlayer.prepareAsync()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun setSpeed(mediaPlayer: MediaPlayer, playbackSpeed: Float) {
        mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(playbackSpeed)
    }

    fun cleanUp() {
        val mediaPlayer = mp ?: return
        mp = null
        mediaPlayer.stop()
            // Once the MP is released it can't be used again.
        mediaPlayer.release()

        if (loudnessEnhancer != null) {
            loudnessEnhancer!!.release()
            loudnessEnhancer = null
        }
    }

    override fun onCompletion(mp: MediaPlayer) {
        if (mFiredOnceCompletionListener != null) {
            mFiredOnceCompletionListener!!.onCompletion(mp)
            mFiredOnceCompletionListener = null
        }
        if (CategorySingleton.getInstance().shouldRepeat() && shouldRepeat) {
            mp.seekTo(0)
            mp.start()
        } else {
            cleanUp()
        }
    }

    override fun onError(mediaPlayer: MediaPlayer, what: Int, extra: Int): Boolean {
        println("TODOJ error during playback what=$what")
        if (MEDIA_ERROR_UNKNOWN == what) {
            TtsSpeaker.speak("play error. speed")
            cleanUp()
            playFile(playbackSpeed = 1f)
        } else {
            TtsSpeaker.speak("play error. code $what")
            cleanUp()
        }

        return true
    }

    companion object {
        @JvmStatic
        val instance: AudioPlayer by lazy {  AudioPlayer() }

        @JvmStatic
        fun transformToM4a(filename: String): String {
            return filename.replace(".mp3", ".m4a").replace(".wav", ".m4a")
        }

        @JvmStatic
        fun basename(filename: String): String {
            return filename.replace(".mp3", "").replace(".wav", "").replace(".m4a", "")
        }

        @JvmStatic
        fun sanitizePath(filename: String): String {
            var filename = filename
            var basename = filename.replace(".mp3", "").replace(".wav", "").replace(".m4a", "")
            // Cut off the negative sign. Just used for dir name.
            basename = basename.substring(1)
            val fileInNumberForm = basename.toLong()
            val NUMBER_OF_DIRS = 100
            val whichDirToPutIn = (fileInNumberForm % NUMBER_OF_DIRS).toInt()
            val zeroPadding: String
            // make this more general some day.
            zeroPadding = if (whichDirToPutIn < 10) {
                "0"
            } else {
                ""
            }
            val uniquePathToString = "$zeroPadding$whichDirToPutIn/"
            filename = DbContants.getAudioLocation() + uniquePathToString + filename
            // TODO
            if (!filename.contains("wav") && !filename.contains("mp3") && !filename.contains("m4a")) {
                filename += ".mp3"
            }
            return filename
        }
    }
}