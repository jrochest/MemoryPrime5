package com.md

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.VolumeProviderCompat
import androidx.media.session.MediaButtonReceiver
import com.md.modesetters.LearningModeSetter

class PlayerService : Service() {
    private var mediaSession: MediaSessionCompat? = null
    var position = 10L

    fun setPlaybackState(newState: Int) {
        mediaSession?.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(newState, position++, 1f) //you simulate a player which plays something.
                .setActions(PlaybackStateCompat.ACTION_FAST_FORWARD or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_FAST_FORWARD or
                        PlaybackStateCompat.ACTION_PREPARE or
                        PlaybackStateCompat.ACTION_REWIND or
                        PlaybackStateCompat.ACTION_STOP)
                .build())
    }


    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "PlayerService")

        val mediaSession = mediaSession!!

        // Note we don't se flags because they are all automatically enabled.

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                println("TODOJ onPlay called")
                setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                // Good.
                handleTapCount(1)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                println("TODOJ next called")
                setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                // Go back
                handleTapCount(3)
            }

            // Note we don't any info about bluetooth device in:
            // mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent
            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                return super.onMediaButtonEvent(mediaButtonEvent)
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                println("TODOJ prev called")
                setPlaybackState(PlaybackStateCompat.STATE_PLAYING)

                // Bad in answer, repeat in question
                handleTapCount(2)
            }

            override fun onPause() {
                super.onPause()
                println("TODOJ pause called")
                // Always set to playing because if we do pause next and prev actions are not sent
                // by the bluetooth headphones, both vistas and jabra elite 75T active.
                setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                // Good grade.
                handleTapCount(1)
            }
        })

        mediaSession.isActive = true
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    private fun handleTapCount(tapCount: Int) {
        val learningModeSetter = LearningModeSetter.getInstance() ?: return
        learningModeSetter.handleTapCount(tapCount)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
    }
}