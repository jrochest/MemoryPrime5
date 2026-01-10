package com.md

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.md.modesetters.*
import com.md.workers.IncrementalBackupManager
import com.md.workers.IncrementalBackupPreferences
import com.md.composeModes.ComposeModeSetter
import com.md.composeModes.CurrentNotePartManager
import com.md.composeModes.SettingsModeComposeManager
import com.md.eventHandler.RemoteInputDeviceManager
import com.md.viewmodel.TopModeFlowProvider
import com.md.viewmodel.InteractionModelFlowProvider
import com.md.viewmodel.InteractionType
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject


@AndroidEntryPoint
@ActivityScoped
class SpacedRepeaterActivity
// Activity must have a zero argument constructor
@Inject constructor()
    : LifecycleOwner, PlaybackServiceControl(), ToneManager by ToneManagerImpl() {

    @Inject
    lateinit var externalClickCounter: Lazy<ExternalClickCounter>
    @Inject
    lateinit var workingModeSetter: Lazy<ComposeModeSetter>
    @Inject
    lateinit var modeHandler: Lazy<ModeHandler>
    @Inject
    lateinit var activityHelper: ActivityHelper

    @Inject
    lateinit var deckLoadManager: Lazy<DeckLoadManager>

    @Inject
    lateinit var immersiveModelManager: Lazy<ImmersiveModeManager>

    @Inject lateinit var toneManager: Lazy<ToneManagerImpl>

    @Inject lateinit var settingsModeComposeManager: Lazy<SettingsModeComposeManager>

    @Inject
    lateinit var topModeFlowProvider: TopModeFlowProvider

    @Inject
    lateinit var interactionProvider: InteractionModelFlowProvider

    @Inject
    lateinit var remoteInputDeviceManager: Lazy<RemoteInputDeviceManager>

    @Inject
    lateinit var currentNotePartManager: Lazy<CurrentNotePartManager>

    @Inject
    lateinit var audioPlayer: Lazy<AudioPlayer>

    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val handler = modeHandler.get()

        DbContants.setup(this)
        volumeControlStream = AudioManager.STREAM_MUSIC
        activityHelper.commonActivitySetup()
        // Normal mode.

        // Uncomment these to clean up.
        // CleanUpAudioFilesModeSetter.getInstance().setup(this, handler)
        // CleanUpAudioFilesModeSetter.getInstance().switchMode(this)
        // and comment this out temporarily.
        workingModeSetter.get().switchMode(this)

        playbackServiceOnCreate()

        TtsSpeaker.setup(this.applicationContext)

        deckLoadManager.get()

        immersiveModelManager.get()


        remoteInputDeviceManager.get().register()

        checkPermission()
    }

    public override fun onResume() {
        super.onResume()
        currentNotePartManager.get().transitionToDeckStagingMode()
        playbackServiceOnResume()
        val modeBackStack = modeHandler.get()
        val modeSetter = modeBackStack.whoseOnTop() ?: return
        // Take back media session focus if we lost it.
        modeSetter.handleReplay()
    }

    // Function to check and request permission.
    private fun checkPermission() {
        val recordAudioPermissionString = "android.permission.RECORD_AUDIO"
        if (ContextCompat.checkSelfPermission(this, recordAudioPermissionString) == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            ActivityCompat.requestPermissions(this, arrayOf(recordAudioPermissionString), REQUEST_CODE_MIC_PERMISSION)
        }
    }

    override fun onPause() {
        super.onPause()

        // Hiding stops the repeat playback in learning mode.
        audioPlayer.get().pause()

        playbackServiceOnPause()
        maybeStopTone()
    }

    override fun onDestroy() {
        super.onDestroy()
        remoteInputDeviceManager.get().unregister()
        playbackServiceOnDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onStart() {
        super.onStart()

        maybeStartTone(this)
    }

    override fun onStop() {
        super.onStop()

        maybeStopTone()
    }

    // These never happen with mpop override:
    // fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent?): Boolean
    // fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // BR301 sends an enter command, which we want to ignore.
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true
        }
        if (remoteInputDeviceManager.get().isRemoteClickEvent(event)) {
             return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * We pay more attention to down events because for some reason they are much more likely
     * to be sent. At least that's true on the AK life BT shutters. Perhaps it's acting like a stuck
     * press. There's a repeat count:
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // BR301 sends an enter command, which we want to ignore.
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true
        }
        if (remoteInputDeviceManager.get().isRemoteClickEvent(event)) {
            return remoteInputDeviceManager.get().onClick(event)
        }
        return super.onKeyDown(keyCode, event)
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


    companion object {
        private const val LOG_TAG = "SpacedRepeater"
        const val PRESS_GROUP_MAX_GAP_MS_BLUETOOTH = 700L
        const val PRESS_GROUP_MAX_GAP_MS_INSTANT = 2L
        const val PRESS_GROUP_MAX_GAP_MS_SCREEN = 600L


        const val REQUEST_CODE_MIC_PERMISSION = 11235
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_MIC_PERMISSION) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                TtsSpeaker.error("Microphone permission not granted.")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return
        // else if ok user probably selected a file

        if (data == null) return

        if (IncrementalBackupPreferences.requestCodeToKey.containsKey(requestCode) &&
                IncrementalBackupManager.createAndWriteZipBackToNewLocation(
                        this,
                        data,
                        requestCode,
                        contentResolver
                )) {

            settingsModeComposeManager.get().updateStateModel()
            return
        }

        if (requestCode == RestoreFromZipManager.REQUEST_CODE && RestoreFromZipManager.restoreFromZip(this, data, requestCode, contentResolver, toneManager.get())) return

        if (requestCode == RestoreFromIncrementalDirectoryManager.REQUEST_CODE && RestoreFromIncrementalDirectoryManager.restoreFromZip(this, data, requestCode, contentResolver)) return
    }

    fun deckLoadManager(): DeckLoadManager? {

        return deckLoadManager.get()

    }
}

