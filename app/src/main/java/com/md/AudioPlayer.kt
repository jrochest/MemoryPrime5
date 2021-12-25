package com.md

import android.media.MediaPlayer
import android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN
import android.media.MediaPlayer.OnCompletionListener
import android.media.audiofx.LoudnessEnhancer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.md.modesetters.TtsSpeaker
import com.md.utils.ToastSingleton
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class AudioPlayer : OnCompletionListener, MediaPlayer.OnErrorListener {
    private var lifecycleOwner: LifecycleOwner? = null
    private var isPrepared: Boolean = false
    private var mp: MediaPlayer? = null
    private var repeatsRemaining: Int? = null
    private var mFiredOnceCompletionListener: OnCompletionListener? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var lastFile: String? = null
    var wantsToPlay = false
        get() = field
        set(value) {
            println("TEMP set wantsToPlay = $wantsToPlay")
            field = value
        }

    /**
     * @param originalFile name of the file, without the save path
     * @param firedOnceCompletionListener
     */
    @JvmOverloads
    fun playFile(
        originalFile: String? = lastFile,
        firedOnceCompletionListener: OnCompletionListener? = null,
        shouldRepeat: Boolean = false,
        playbackSpeed: Float = 1.5f,
        autoPlay: Boolean = true
    ) {
        println("TEMPJ playFile autoPlay= $autoPlay")
        lifecycleOwner?.lifecycleScope?.launch {
            if (originalFile == null) {
                ToastSingleton.getInstance()
                    .error("Null file path. You should probably delete this note Jacob.")
                return@launch
            }

            lastFile = originalFile

            mp?.let { cleanUp(it) }
            mp = null
            // Note: noise supressor seem to fail and say not enough memory. NoiseSuppressor.
            mFiredOnceCompletionListener = firedOnceCompletionListener
            val path = sanitizePath(originalFile)
            val audioFile = File(path)
            if (!audioFile.exists()) {
                ToastSingleton.getInstance().error("$path does not exist.")
                return@launch
            }

            val mediaPlayer = MediaPlayer()
            mp = mediaPlayer
            println("TEMPJ playFile set wants to play autoPlay= $autoPlay")

            wantsToPlay = autoPlay
            isPrepared = false
            mediaPlayer.isLooping = false
            repeatsRemaining = if (shouldRepeat) 1 else 0
            try {
                mediaPlayer.setDataSource(path)
                loudnessEnhancer = LoudnessEnhancer(mediaPlayer.audioSessionId)
                loudnessEnhancer!!.setTargetGain(700)
                loudnessEnhancer!!.enabled = true

                mediaPlayer.setOnErrorListener(instance)
                mediaPlayer.setOnCompletionListener(instance)
                mediaPlayer.setOnPreparedListener {
                    onPrepared(it, playbackSpeed)
                }
                mediaPlayer.prepareAsync()
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun onPrepared(mediaPlayer: MediaPlayer, playbackSpeed: Float) {
        if (mp != mediaPlayer) {
            cleanUp(mediaPlayer)
            return
        }

        isPrepared = true
        setSpeed(mediaPlayer, playbackSpeed)
        println("TEMP onPrepared wantsToPlay = $wantsToPlay")
        if (wantsToPlay) {
            mediaPlayer.start()
        } else {
            // It seems like the looping = true auto plays.
            mediaPlayer.pause()
        }
    }

    private fun setSpeed(mediaPlayer: MediaPlayer, playbackSpeed: Float) {
        mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(playbackSpeed)
    }

    override fun onCompletion(mp: MediaPlayer) {
        lifecycleOwner?.lifecycleScope?.launch {
                repeatsRemaining?.let {
                    if ((it == 0 && wantsToPlay)) {
                        pause()
                    } else {
                        if (true == lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)) {
                            mp.seekTo(0)
                            mp.start()
                            repeatsRemaining = it - 1
                            // This avoid firing the completion listener
                            return@launch
                        }
                }
                if (mFiredOnceCompletionListener != null) {
                    mFiredOnceCompletionListener!!.onCompletion(mp)
                    mFiredOnceCompletionListener = null
                }
                if (!CategorySingleton.getInstance().shouldRepeat()) {
                    cleanUp(mp)
                }
            }
        }
    }

    @JvmOverloads
    fun cleanUp(mediaPlayer: MediaPlayer? = mp) {
        if (mediaPlayer == null) return

        mediaPlayer.stop()
        // Once the MP is released it can't be used again.
        mediaPlayer.release()

        if (loudnessEnhancer != null) {
            loudnessEnhancer!!.release()
            loudnessEnhancer = null
        }
    }

    override fun onError(mediaPlayer: MediaPlayer, what: Int, extra: Int): Boolean {
        println("TODOJ error during playback what=$what extra $extra $lastFile")
        if (MEDIA_ERROR_UNKNOWN == what) {
            TtsSpeaker.speak("play error. speed")
            cleanUp(mediaPlayer)
            playFile(playbackSpeed = 1f, shouldRepeat = true)
        } else {
            TtsSpeaker.speak("play error. code $what")
            cleanUp(mediaPlayer)
        }

        return true
    }

    fun pause() {
        wantsToPlay = false
        val mp = mp ?: return
        if (isPrepared) {
            mp.pause()
        }
    }

    fun playWhenReady() {
        wantsToPlay = true
        val mp = mp ?: return
        if (isPrepared) {
            mp.start()
        }
    }

    fun setLifeCycleOwner(lifecycleOwner: LifecycleOwner) {
        this.lifecycleOwner = lifecycleOwner
    }

    companion object {
        @JvmStatic
        val instance: AudioPlayer by lazy { AudioPlayer() }

        @JvmStatic
        fun transformToM4a(filename: String): String {
            return filename.replace(".mp3", ".m4a").replace(".wav", ".m4a")
        }

        @JvmStatic
        fun basename(filename: String): String {
            return filename.replace(".mp3", "").replace(".wav", "").replace(".m4a", "")
        }

        @JvmStatic
        fun getAudioDirectory(filename: String): String {
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
            val twoCharDirectoryString = "$zeroPadding$whichDirToPutIn/"
            return DbContants.getAudioLocation() + twoCharDirectoryString
        }

        @JvmStatic
        fun sanitizePath(original: String): String {
            var filename = getAudioDirectory(original) + original

            if (!filename.contains("wav") && !filename.contains("mp3") && !filename.contains("m4a")) {
                filename += ".mp3"
                TtsSpeaker.error("mp3 had to be concatenated")
            }
            return filename
        }
    }
}