@file:OptIn(ExperimentalCoroutinesApi::class)

package com.md

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN
import android.media.MediaPlayer.OnCompletionListener
import android.media.audiofx.LoudnessEnhancer
import android.os.SystemClock
import android.provider.MediaStore.Audio
import android.util.LruCache
import androidx.lifecycle.LifecycleOwner
import com.md.modesetters.TtsSpeaker
import com.md.utils.ToastSingleton
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.lang.Exception
import javax.inject.Inject
import kotlin.coroutines.resumeWithException
@ActivityScoped
class AudioPlayer @Inject constructor() : OnCompletionListener, MediaPlayer.OnErrorListener {
    private var lifecycleOwner: LifecycleOwner? = null
    private var focusedPlayer: MediaPlayerForASingleFile? = null
    private var playbackSpeedBaseOnErrorRates = 1.5f

    private var repeatsRemaining: Int? = null
    private var mFiredOnceCompletionListener: OnCompletionListener? = null
    private var lastFile: String? = null
    private var wantsToPlay = false
        get() = field
        set(value) {
            field = value
        }

    data class ValidatedAudioFileName(val originalMediaFile: String)

    init {
        AudioPlayer.instance = this
    }

    /**
     * Preloads the file and returns true if successful. False if successful
     */
    suspend fun preload(originalMediaFile: String): MediaPlayerForASingleFile? {
        // Verify the new file exists prior to switching to it.
        val path = sanitizePath(originalMediaFile)
        // TODOJNOW
        val audioFile = File(path)
        if (!audioFile.exists()) {
            TtsSpeaker.speak("Missing audio file")
            // This seem like it might be using a branch newly recorded file when it
            // messes up. The missing file it found was:
            // com.jrochest.mp.debug/files/com.md.MemoryPrime/AudioMemo/46/1661565629946.m4a
            // which did not exist.
            System.err.println("$path does not exist. original $originalMediaFile")
            return null
        }

        val validatedFile = ValidatedAudioFileName(path)
        val oldPlayer = fileToMediaPlayerCache.get(validatedFile)

        if (oldPlayer != null && !oldPlayer.isCleanedUp) {
            return oldPlayer
        }

        // Example time to execute call this call in milliseconds:
        // 194 (first call is the slowest typically)
        // 68, 42,82, 59, 89...
        val newPlayer = MediaPlayerForASingleFile(validatedFile)

        fileToMediaPlayerCache.put(validatedFile, newPlayer)
        return newPlayer
    }

    private val fileToMediaPlayerCache =
        object : LruCache<ValidatedAudioFileName, MediaPlayerForASingleFile>(20) {
            override fun entryRemoved(
                evicted: Boolean,
                key: ValidatedAudioFileName,
                oldValue: MediaPlayerForASingleFile,
                newValue: MediaPlayerForASingleFile?
            ) {
                super.entryRemoved(evicted, key, oldValue, newValue)
                oldValue.cleanup()
            }
        }


    /** A wrapper for a combination of a file and media player. */
    inner class MediaPlayerForASingleFile(audioFileName: ValidatedAudioFileName) {
        var hasCompletedPlaybackSinceBecomingPrimary: Boolean = false
        var isCleanedUp: Boolean = false
        val mediaPlayer = MediaPlayer()
        private val loudnessEnhancer: LoudnessEnhancer =
            LoudnessEnhancer(mediaPlayer.audioSessionId)

        init {
            with(mediaPlayer) {
                val loudnessEnhancer = LoudnessEnhancer(mediaPlayer.audioSessionId)
                // As of 2024 Q1 this does increase volume.
                // 7000 is easily perceivable as loader than 700, and 0 easily perceivable as
                // quieter than 700.
                loudnessEnhancer.setTargetGain(700)
                loudnessEnhancer.enabled = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(audioFileName.originalMediaFile)
                prepare()
                playbackParams = playbackParams.setSpeed(playbackSpeedBaseOnErrorRates)
                isLooping = false
                pause()
            }
        }

        fun cleanup() {
            isCleanedUp = true
            mediaPlayer.release()
            loudnessEnhancer.release()
        }
    }

    suspend fun suspendPlayReturningTrueIfLoadedFileChanged(
        audioFileName: String?,
    ) : Boolean {
        if (audioFileName == null) {
            ToastSingleton.getInstance()
                .error("Null file path. You should probably delete this note Jacob.")
            return false
        }
        var localCurrentPlayer: MediaPlayerForASingleFile? = null
        try {
            localCurrentPlayer = preload(audioFileName)
        } catch (e: IOException) {
            TtsSpeaker.speak("IOException loading audio file. Delete or record again: " + e.message)
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            TtsSpeaker.speak("IllegalStateException loading audio file. Delete or record again: "+ e.message)
            e.printStackTrace()
        }
        if (localCurrentPlayer == null)  {
            return false
        }
        val fileNeededToBeFreshlyLoaded: Boolean
        if (localCurrentPlayer != focusedPlayer) {
            fileNeededToBeFreshlyLoaded = true
            focusedPlayer?.mediaPlayer?.pause()
            focusedPlayer = localCurrentPlayer
            localCurrentPlayer.hasCompletedPlaybackSinceBecomingPrimary = false
        } else {
            fileNeededToBeFreshlyLoaded = false
        }
        val mediaPlayer = localCurrentPlayer.mediaPlayer
        mediaPlayer.seekTo(0)
        mediaPlayer.start()
        suspend fun awaitCompletion(): MediaPlayer? = suspendCancellableCoroutine { continuation ->
            val callback =
                OnCompletionListener { mp ->  // Implementation of some callback interface
                    continuation.resume(mp) {
                        mediaPlayer.setOnCompletionListener(null)
                        mediaPlayer.setOnErrorListener(null)
                    }
                }

            val errorCallback =
                MediaPlayer.OnErrorListener { mp, what, extra ->
                    // Implementation of some callback interface
                    println("OnErrorListener: $what")
                    TtsSpeaker.speak("playback error $what")
                    continuation.resumeWithException(Exception("MediaPlayer Error is what = $what"))
                    mediaPlayer.setOnCompletionListener(null)
                    mediaPlayer.setOnErrorListener(null)
                    true
                }
            mediaPlayer.setOnErrorListener(errorCallback)
            mediaPlayer.setOnCompletionListener(callback)
        }

        awaitCompletion()
        localCurrentPlayer.hasCompletedPlaybackSinceBecomingPrimary = true
        return fileNeededToBeFreshlyLoaded
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
        autoPlay: Boolean = true
    ) {
        lifecycleOwner?.lifecycleScope?.launch {
            if (originalFile == null) {
                ToastSingleton.getInstance()
                    .error("Null file path. You should probably delete this note Jacob.")
                return@launch
            }

            if (!lifecycleOwner.isAtLeastResumed()) {
                return@launch;
            }
            mFiredOnceCompletionListener = firedOnceCompletionListener
            lastFile = originalFile


            var localCurrentPlayer: MediaPlayerForASingleFile? = null
            try {
                localCurrentPlayer = preload(originalFile)
            } catch (e: IOException) {
              TtsSpeaker.speak("Error loading audio file. Delete or record again")
              e.printStackTrace()
            } catch (e: IllegalStateException) {
                TtsSpeaker.speak("Error loading audio file. Delete or record again")
                e.printStackTrace()
            }
            if (localCurrentPlayer == null)  {
                return@launch
            }

            focusedPlayer?.mediaPlayer?.pause()
            focusedPlayer = localCurrentPlayer
            localCurrentPlayer.hasCompletedPlaybackSinceBecomingPrimary = false
            localCurrentPlayer.mediaPlayer.seekTo(0)
            if (autoPlay) {
                localCurrentPlayer.mediaPlayer.start()
            }

            // Note: noise supressor seem to fail and say not enough memory. NoiseSuppressor.

            val mediaPlayer = localCurrentPlayer.mediaPlayer
            wantsToPlay = autoPlay
            repeatsRemaining = if (shouldRepeat) 3 else 0
            try {
                mediaPlayer.setOnErrorListener(this@AudioPlayer)
                mediaPlayer.setOnCompletionListener(this@AudioPlayer)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun onCompletion(mp: MediaPlayer) {
        val localFocusedPlayer = focusedPlayer
        if (localFocusedPlayer == null || mp != localFocusedPlayer.mediaPlayer) {
            return
        }

        localFocusedPlayer.hasCompletedPlaybackSinceBecomingPrimary = true

        lifecycleOwner?.lifecycleScope?.launch {
            if (mp != localFocusedPlayer.mediaPlayer) {
                return@launch
            }
            repeatsRemaining?.let { repeats ->
                if ((repeats <= 0 && wantsToPlay)) {
                    //pause()
                    localFocusedPlayer.mediaPlayer.pause()
                } else if (wantsToPlay) {
                    // This was added to avoid playing when not resumed.
                    if (lifecycleOwner.isAtLeastResumed()) {
                        localFocusedPlayer.mediaPlayer.seekTo(0)
                        localFocusedPlayer.mediaPlayer.start()
                        if (repeats <= 1) {
                            wantsToPlay = false
                        }
                        repeatsRemaining = repeats - 1
                        // This avoid firing the completion listener
                        return@launch
                    }
                }
                if (mFiredOnceCompletionListener != null) {
                    mFiredOnceCompletionListener!!.onCompletion(mp)
                    mFiredOnceCompletionListener = null
                }
            }
        }
    }

    override fun onError(mediaPlayer: MediaPlayer, what: Int, extra: Int): Boolean {
        println("error during playback what=$what extra $extra $lastFile")
        if (MEDIA_ERROR_UNKNOWN == what) {
            TtsSpeaker.speak("play MEDIA_ERROR_UNKNOWN. Trying normal speed")
            playbackSpeedBaseOnErrorRates = 1f
        } else {
            TtsSpeaker.speak("play error. code $what")
            //cleanUp(mediaPlayer)
        }

        return true
    }

    fun pause() {
        wantsToPlay = false
        val player = focusedPlayer ?: return
        if (player.mediaPlayer.isPlaying) {
            player.mediaPlayer.pause()
        }
    }

    fun playWhenReady() {
        wantsToPlay = true
        val player = focusedPlayer ?: return
        player.mediaPlayer.start()
    }

    fun setLifeCycleOwner(lifecycleOwner: LifecycleOwner) {
        this.lifecycleOwner = lifecycleOwner
    }

    companion object {
        @JvmStatic
        var instance: AudioPlayer = AudioPlayer() // TODOJNOW this is a dummy so that we can switch to DI.

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