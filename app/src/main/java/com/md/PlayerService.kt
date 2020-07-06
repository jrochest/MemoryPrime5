package com.md

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.md.modesetters.LearningModeSetter

class PlayerService : MediaBrowserServiceCompat() {
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
        mediaSession = MediaSessionCompat(this, "PlayerService").also {
            sessionToken = it.sessionToken
        }


        val mediaSession = mediaSession!!

        // Note we don't se flags because they are all automatically enabled.

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                mediaSession.isActive = true
                println("TODOJ onPlay called")
                setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                // Good.
                handleTapCount(1)
            }

            override fun onPrepare() {
                super.onPrepare()
                mediaSession.isActive = true
                println("TODOJ onPrepare called")
                setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
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
                println("TODOJ mediaButtonEvent called")
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


            override fun onStop() {
                super.onStop()
                println("TODOJ stopped called")
                setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
                // Don't take media events while activity stopped.
                mediaSession.isActive = false
            }
        })

        mediaSession.isActive = true
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        println("TODOJ send back onLoadChildren empty list!")
        result.sendResult(mutableListOf())
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // Let everyone connect!
        println("TODOJ connected to me root!")
        return BrowserRoot("Good", null)
    }

    private fun handleTapCount(tapCount: Int) {
        val learningModeSetter = LearningModeSetter.getInstance() ?: return
        learningModeSetter.handleTapCount(tapCount)
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