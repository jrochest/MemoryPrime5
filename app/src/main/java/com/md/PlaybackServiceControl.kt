package com.md

import android.content.ComponentName
import android.media.AudioManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.appcompat.app.AppCompatActivity

open class PlaybackServiceControl : AppCompatActivity() {
    private var mAudioManager: AudioManager? = null
    private var mediaController: MediaControllerCompat? = null

    // Change this to make this app a media playback service and take media events like play
    // pause next prev etc
    protected val shouldCreatePlaybackService = false
    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnectionSuspended() {
        }

        override fun onConnectionFailed() {

        }

        override fun onConnected() {
            // Get the token for the MediaSession
            mediaBrowser?.let {
                it.sessionToken.also { token ->
                    // Create a MediaControllerCompat
                    mediaController = MediaControllerCompat(
                            this@PlaybackServiceControl, // Context
                            token
                    ).apply {
                        // Save the controller
                        MediaControllerCompat.setMediaController(this@PlaybackServiceControl, this)
                        // Register a callback to stay in sync
                        registerCallback(controllerCallback)
                        transportControls.prepare()
                    }
                }
            }
        }
    }
    private var controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {}

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            println("TODOJ " + state?.playbackState)
        }
    }
    private var mediaBrowser: MediaBrowserCompat? = null
    protected var hasAudioFocus = false
    protected val afListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> hasAudioFocus = true
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> hasAudioFocus = false
        }
    }

    protected fun playbackServiceOnCreate() {
        if (shouldCreatePlaybackService) {
            mAudioManager = this.getSystemService(AUDIO_SERVICE) as AudioManager
            //startService(Intent(this, PlayerService::class.java))
            mediaBrowser = MediaBrowserCompat(
                    this,
                    ComponentName(this, PlayerService::class.java),
                    connectionCallbacks,
                    null).also {
                if (!it.isConnected) {
                    it.connect()
                }
            }
        }
    }

    protected fun playbackServiceOnResume() {
        mediaController?.transportControls?.prepare()
    }

    protected fun playbackServiceOnPause() {
        mediaController?.transportControls?.stop()
    }

    protected fun playbackServiceOnDestroy() {
        mediaBrowser?.disconnect()
    }

    fun hasAudioFocus(): Boolean {
        return hasAudioFocus
    }
}