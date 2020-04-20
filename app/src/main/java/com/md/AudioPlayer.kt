package com.md

import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.PlaybackParams
import android.media.audiofx.LoudnessEnhancer
import com.md.utils.ToastSingleton
import java.io.File
import java.io.IOException

class AudioPlayer : OnCompletionListener, MediaPlayer.OnErrorListener {
    private var learningMode: Boolean = false;
    private var mp: MediaPlayer? = null
    private var mFiredOnceCompletionListener: OnCompletionListener? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    /**
     * @param originalFile name of the file, without the save path
     * @param firedOnceCompletionListener
     */
    @Synchronized
    @JvmOverloads
    fun playFile(originalFile: String?,
                 firedOnceCompletionListener: OnCompletionListener? = null,  learningMode: Boolean = false) {
        this.learningMode = learningMode

        if (originalFile == null) {
            ToastSingleton.getInstance().error("Null file path. You should probably delete this note Jacob.")
            return
        }
        cleanUp()
        mFiredOnceCompletionListener = firedOnceCompletionListener
        val path = sanitizePath(originalFile)
        val audioFile = File(path)
        if (!audioFile.exists()) {
            ToastSingleton.getInstance().error("$path does not exist.")
            return
        }
        mp = MediaPlayer()
        try {
            mp!!.setDataSource(path)
            loudnessEnhancer = LoudnessEnhancer(mp!!.audioSessionId)
            loudnessEnhancer!!.setTargetGain(700)
            loudnessEnhancer!!.enabled = true
            mp!!.playbackParams = PlaybackParams().setSpeed(1.5f)
            mp!!.prepare()
        } catch (e: IllegalArgumentException) { // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: IllegalStateException) { // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: IOException) { // TODO Auto-generated catch block
            e.printStackTrace()
        }
        mp!!.setOnErrorListener(this)
        mp!!.setOnCompletionListener(this)
        mp!!.start()
    }

    @Synchronized
    fun cleanUp() {
        if (mp != null) {
            mp!!.stop()
            // Once the MP is released it can't be used again.
            mp!!.release()
            mp = null
        }
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
        if (CategorySingleton.getInstance().shouldRepeat() && learningMode) {
            mp.seekTo(0)
        } else {
            cleanUp()
        }
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        println("TODOJ error during playback what=$what")
        cleanUp()
        return true
    }

    companion object {
        // Note: noise supressor seem to fail and say not enough memory. NoiseSuppressor.
        @JvmStatic
        var instance: AudioPlayer? = null
            get() {
                if (field == null) {
                    field = AudioPlayer()
                }
                return field
            }
            private set

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