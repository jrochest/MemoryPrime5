package com.md

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.VolumeProviderCompat
import androidx.media.session.MediaButtonReceiver

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

        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                println("TODOJ onPlay called")
                setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                println("TODOJ next called")
                setPlaybackState(PlaybackStateCompat.STATE_PLAYING)

            }


            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {

                println("TODOJ received event $mediaButtonEvent")
                return super.onMediaButtonEvent(mediaButtonEvent)


            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                println("TODOJ prev called")
                setPlaybackState(PlaybackStateCompat.STATE_PLAYING)

            }


            override fun onPause() {
                super.onPause()
                println("TODOJ pause called")
                setPlaybackState(PlaybackStateCompat.STATE_PAUSED)

            }
        })

        //this will only work on Lollipop and up, see https://code.google.com/p/android/issues/detail?id=224134
        val myVolumeProvider: VolumeProviderCompat = object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE,  /*max volume*/100,  /*initial volume level*/50) {
            override fun onAdjustVolume(direction: Int) {
                println("TODOJ got a volume direction $direction")
                /*
                -1 -- volume down
                1 -- volume up
                0 -- volume button released
                 */
            }
        }
        //mediaSession.setPlaybackToRemote(myVolumeProvider)
        mediaSession.isActive = true
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
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