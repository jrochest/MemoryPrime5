package com.md.eventHandler

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import com.md.AudioPlayer
import com.md.ExternalClickCounter
import com.md.SpacedRepeaterActivity
import com.md.composeModes.CurrentNotePartManager
import com.md.composeModes.Mode
import com.md.modesetters.TtsSpeaker
import com.md.utils.KeepScreenOn
import com.md.viewmodel.InteractionModelFlowProvider
import com.md.viewmodel.InteractionType
import com.md.viewmodel.TopModeFlowProvider
import dagger.Lazy
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class RemoteInputDeviceManager @Inject constructor(
    @ActivityContext val context: Context,
    private val topModeFlowProvider: TopModeFlowProvider,
    private val interactionProvider: InteractionModelFlowProvider,
    private val keepScreenOn: Lazy<KeepScreenOn>,
    private val externalClickCounter: Lazy<ExternalClickCounter>,
    private val audioPlayer: Lazy<AudioPlayer>,
    private val currentNotePartManager: Lazy<CurrentNotePartManager>,
) : InputManager.InputDeviceListener {

    private val inputManager by lazy { context.getSystemService(Context.INPUT_SERVICE) as InputManager }
    private val activeShutterDeviceIds = mutableSetOf<Int>()

    fun register() {
        refreshShutterDeviceIds()
        inputManager.registerInputDeviceListener(this, null)
    }

    fun unregister() {
        inputManager.unregisterInputDeviceListener(this)
    }

    private fun refreshShutterDeviceIds() {
        val deviceIds = inputManager.inputDeviceIds
        for (id in deviceIds) {
            val device = inputManager.getInputDevice(id)
            if (device != null && isDeviceMemprime(device)) {
                activeShutterDeviceIds.add(id)
            }
        }
    }

    fun simulateShutterConnection(connected: Boolean) {
        TtsSpeaker.speak("simulate $connected")
        val fakeDeviceId = -1
        if (connected) {
            activeShutterDeviceIds.add(fakeDeviceId)
        } else {
            if (activeShutterDeviceIds.contains(fakeDeviceId)) {
                activeShutterDeviceIds.remove(fakeDeviceId)
                handleShutterDisconnection()
            }
        }
    }

    /** Used to indicate a remote control device send a click event. */
    fun onClick(event: KeyEvent): Boolean {
        return handleRemoteClick(event.eventTime)
    }

    /** Returns true if the event is a remote click (Volume Up/Down from a shutter). */
    fun isRemoteClickEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return false
        }
        return isFromMemprimeDevice(event)
    }

    fun handleRemoteClick(eventTimeMs: Long): Boolean {
        topModeFlowProvider.modeModel.value = Mode.Practice
        interactionProvider.mostRecentInteraction.value = InteractionType.RemoteHumanInterfaceDevice
        interactionProvider.clearMostRecentPocketModeTap()
        keepScreenOn.get().keepScreenOn(updatedDimScreenAfterBriefDelay = true)
        return externalClickCounter.get().handleRhythmUiTaps(
            eventTimeMs,
            SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_BLUETOOTH
        )
    }

    fun isFromMemprimeDevice(event: KeyEvent): Boolean {
        val isMemprime = if (event.deviceId >= 5) {
            true
        } else {
            val device = event.device
            device != null && isDeviceMemprime(device)
        }

        if (isMemprime && !activeShutterDeviceIds.contains(event.deviceId)) {
            activeShutterDeviceIds.add(event.deviceId)
        }

        return isMemprime
    }

    private fun isDeviceMemprime(device: InputDevice): Boolean {
        if (device.id >= 5) {
            return true
        }
        val name = device.name
        return name.contains("Virtual") ||
                name.contains("Mpow") ||
                name.contains("AB Shutter3") ||
                name.contains("AK LIFE BT") ||
                name.contains("BLE") ||
                name.contains("BR301") ||
                name.contains("memprime") ||
                name.contains("STRIM-BTN10") ||  // MARREX.
                name.contains("Button Jack") ||
                name.contains("PhotoShot") ||
                name.contains("Shutter Camera")
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = inputManager.getInputDevice(deviceId)
        if (device != null && isDeviceMemprime(device)) {
            TtsSpeaker.speak("Connected")
            activeShutterDeviceIds.add(deviceId)
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {}

    override fun onInputDeviceRemoved(deviceId: Int) {
        if (activeShutterDeviceIds.contains(deviceId)) {
            activeShutterDeviceIds.remove(deviceId)
            handleShutterDisconnection()
        }
    }

    private fun handleShutterDisconnection() {
        TtsSpeaker.speak("Disconnected")
        audioPlayer.get().pause()
        currentNotePartManager.get().transitionToDeckStagingMode()
        interactionProvider.mostRecentInteraction.value = InteractionType.TouchScreen
        interactionProvider.clearMostRecentPocketModeTap()
        keepScreenOn.get().keepScreenOn(updatedDimScreenAfterBriefDelay = false)
    }
}
