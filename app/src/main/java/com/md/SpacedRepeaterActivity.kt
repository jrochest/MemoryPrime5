package com.md

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import androidx.core.view.InputDeviceCompat.SOURCE_KEYBOARD
import androidx.lifecycle.LifecycleOwner
import com.md.AudioPlayer.Companion.instance
import com.md.modesetters.*
import com.md.workers.BackupPreferences
import com.md.workers.BackupToUsbManager.createAndWriteZipBackToNewLocation
import com.md.workers.IncrementalBackupManager
import com.md.workers.IncrementalBackupPreferences


class SpacedRepeaterActivity : LifecycleOwner, PlaybackServiceControl(), ToneManager by ToneManagerImpl() {

    private val externalClickCounter = ExternalClickCounter(this)

    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        AudioPlayer.instance.setLifeCycleOwner(this)

       MemPrimeNotificationManager.createChannel(this)

        DbContants.setup(this)
        volumeControlStream = AudioManager.STREAM_MUSIC
        val activityHelper = ActivityHelper()
        activityHelper.commonActivitySetup(this)
        // Normal mode.
        CreateModeSetter.setUp(this, modeHand)
        BrowsingModeSetter.getInstance().setup(this, modeHand)
        DeckChooseModeSetter.getInstance()?.setUp(this, modeHand)
        LearningModeSetter.instance.setUp(this, modeHand)
        DeckChooseModeSetter.getInstance().switchMode(this)
        SettingModeSetter.setup(this, modeHand)
        CleanUpAudioFilesModeSetter.getInstance().setup(this, modeHand)

        playbackServiceOnCreate()

        TtsSpeaker.setup(this.applicationContext)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra(Extras.Keys.IS_TIMER_REQUEST.name, false) == true) {
            MemPrimeNotificationManager.startTimer(this.baseContext)
        }
    }

    override fun onResume() {
        super.onResume()

        playbackServiceOnResume()

        val modeSetter = modeHand.whoseOnTop() ?: return
        // Take back media session focus if we lost it.
        modeSetter.handleReplay()
    }


    override fun onPause() {
        super.onPause()

        // Hiding stops the repeat playback in learning mode.
        instance.pause()
        MoveManager.cancelJobs()

        playbackServiceOnPause()
        maybeStopTone()
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackServiceOnDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        TtsSpeaker.speak("Config change")
    }

    var modeHand = ModeHandler(this)
    override fun onBackPressed() {
        MoveManager.cancelJobs()
        if (modeHand.goBack()) {
            return
        }

        super.onBackPressed()
    }

    override fun onStart() {
        super.onStart()

        maybeStartTone(this)
    }

    override fun onStop() {
        super.onStop()

        maybeStopTone()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val activityHelper = ActivityHelper()
        activityHelper.createCommonMenu(menu, this)
        return true
    }

    private fun isFromMemprimeDevice(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) {
            return false
        }


        // On Pixel 7 Pro in 2022 Android T

        // Real volume Up button Volume Button:
        // KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_VOLUME_UP, scanCode=115, metaState=0, flags=0x8, repeatCount=0, eventTime=1532328658000, downTime=1532328658000, deviceId=2, source=0x101, displayId=-1 }
        // KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_VOLUME_UP, scanCode=115, metaState=0, flags=0x8, repeatCount=0, eventTime=1532328658000, downTime=1532328658000, deviceId=2, source=0x101, displayId=-1 }
        // Phone's Real volume down button:
        // KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_VOLUME_DOWN, scanCode=114, metaState=0, flags=0x8, repeatCount=0, eventTime=3061704102000, downTime=3061704102000, deviceId=2, source=0x101, displayId=-1 }

        // Bluetooth 5 AB shutter:
        // KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_VOLUME_UP, scanCode=115, metaState=0, flags=0x8, repeatCount=0, eventTime=1459039036000, downTime=1459039036000, deviceId=10, source=0x101, displayId=-1 }
        // Bluetooth 5 AB shutter same device after reconnect (deviceId=11,):
        // KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_VOLUME_UP, scanCode=115, metaState=0, flags=0x8, repeatCount=0, eventTime=3119110978000, downTime=3119110978000, deviceId=11, source=0x101, displayId=-1 }
        // MPow
        // KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_VOLUME_UP, scanCode=115, metaState=0, flags=0x8, repeatCount=0, eventTime=3304164273000, downTime=3304164273000, deviceId=12, source=0x301, displayId=-1 }
        // After reboot
        // Pixel 7 Pro volume button (deviceId = 2): KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_VOLUME_UP, scanCode=115, metaState=0, flags=0x8, repeatCount=0, eventTime=96892264000, downTime=96892264000, deviceId=2, source=0x101, displayId=-1 }
        // Mpow
        // KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_VOLUME_UP, scanCode=115, metaState=0, flags=0x8, repeatCount=0, eventTime=166876092000, downTime=166876092000, deviceId=5, source=0x301, displayId=-1 }
        // ab shutter:
        // KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_VOLUME_UP, scanCode=115, metaState=0, flags=0x8, repeatCount=0, eventTime=226887697000, downTime=226887697000, deviceId=6, source=0x101, displayId=-1 }
        // So Heuristic:
        if (event.deviceId >= 5) {
            return true
        }

        val device = event.device ?: return false

        // Perhaps delete all of this?
        // On Android 12 the Mpow device is:
        // Mpow isnap X2 Mouse
        val name = device.name
        if (name.contains("Virtual") ||
                name.contains("Mpow") ||
                name.contains("AB Shutter3") ||
                name.contains("AK LIFE BT") ||
                name.contains("BLE") ||
                name.contains("BR301") ||
                name.contains("memprime") ||
                name.contains("STRIM-BTN10") ||  // MARREX.
                name.contains("Button Jack") ||
                name.contains("PhotoShot")) {
            return true
        }

        return isFromMultiButtonMemprimeDevice(keyCode, event)
    }

    private fun isFromMultiButtonMemprimeDevice(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) {
            return false
        }
        val device = event.device ?: return false
        val name = device.name
        return name.contains("Shutter Camera")
    }

    // These never happen with mpop override:
    // fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent?): Boolean
    // fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        println("TODOJ key up event$event")
        val modeSetter = modeHand.whoseOnTop()
        // BR301 sends an enter command, which we want to ignore.
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true
        }
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyUp(keyCode, event)
        }
        println("TODOJ event$event")
        return if (modeSetter == null || !isFromMemprimeDevice(keyCode, event)) {
            super.onKeyUp(keyCode, event)
        } else true
    }

    /**
     * We pay more attention to down events because for some reason they are much more likely
     * to be sent. At least that's true on the AK life BT shutters. Perhaps it's acting like a stuck
     * press. There's a repeat count:
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        println("TODOJ key down event$event")
        val modeSetter = modeHand.whoseOnTop()
        println("TODOJ event$event")
        // BR301 sends an enter command, which we want to ignore.
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true
        }
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyDown(keyCode, event)
        }
        if (modeSetter == null || !isFromMemprimeDevice(keyCode, event)) {
            return super.onKeyDown(keyCode, event)
        }
        if (modeSetter !is LearningModeSetter) {
            LearningModeSetter.instance.switchMode(this)
            return true
        }
        val eventTimeMs = event.eventTime
        return externalClickCounter.handleRhythmUiTaps(modeSetter, eventTimeMs, PRESS_GROUP_MAX_GAP_MS_BLUETOOTH, 1)
    }

    fun maybeChangeAudioFocus(shouldHaveFocus: Boolean) {
        if (!shouldCreatePlaybackService) {
            return
        }
        if (hasAudioFocus == shouldHaveFocus) { // The audiofocus matches request already.
            return
        }
        val audioManager = this.getSystemService(
                Context.AUDIO_SERVICE) as AudioManager
        val mPlaybackAttributes = AudioAttributes.Builder().setUsage(
                AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        val mFocusRequest = AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(mPlaybackAttributes)
                .setOnAudioFocusChangeListener(afListener).build()
        hasAudioFocus = if (shouldHaveFocus) {
            val res = audioManager.requestAudioFocus(mFocusRequest)
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            audioManager.abandonAudioFocus(afListener)
            false
        }
    }


    fun maybeDim() {
        val modeSetter = modeHand.whoseOnTop()
        modeSetter.toggleDim()
    }

    companion object {
        private const val LOG_TAG = "SpacedRepeater"
        const val PRESS_GROUP_MAX_GAP_MS_BLUETOOTH = 700L
        const val PRESS_GROUP_MAX_GAP_MS_INSTANT = 2L
        const val PRESS_GROUP_MAX_GAP_MS_SCREEN = 400L
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return
        // else if ok user probably selected a file

        if (data == null) return

        if (BackupPreferences.requestCodeToKey.containsKey(requestCode) &&
                createAndWriteZipBackToNewLocation(
                        this,
                        data,
                        requestCode,
                        contentResolver
                )) {
            SettingModeSetter.refreshSettings(this)
            return
        }

        if (IncrementalBackupPreferences.requestCodeToKey.containsKey(requestCode) &&
                IncrementalBackupManager.createAndWriteZipBackToNewLocation(
                        this,
                        data,
                        requestCode,
                        contentResolver
                )) {
            SettingModeSetter.refreshSettings(this)
            return
        }

        if (requestCode == RestoreFromZipManager.REQUEST_CODE && RestoreFromZipManager.restoreFromZip(this, data, requestCode, contentResolver)) return

        if (requestCode == RestoreFromIncrementalDirectoryManager.REQUEST_CODE && RestoreFromIncrementalDirectoryManager.restoreFromZip(this, data, requestCode, contentResolver)) return
    }

    @JvmOverloads
    fun handleRhythmUiTaps(learningModeSetter: LearningModeSetter, uptimeMillis: Long, pressGroupMaxGapMsScreen: Long, tapCount: Int = 1) {
        externalClickCounter.handleRhythmUiTaps(learningModeSetter, uptimeMillis, pressGroupMaxGapMsScreen, tapCount)
    }
}

