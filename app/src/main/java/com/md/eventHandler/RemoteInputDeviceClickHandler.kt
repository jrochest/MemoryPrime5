package com.md.eventHandler

import com.md.ExternalClickCounter
import com.md.SpacedRepeaterActivity
import com.md.composeModes.Mode
import com.md.utils.KeepScreenOn
import com.md.viewmodel.InteractionModelFlowProvider
import com.md.viewmodel.InteractionType
import com.md.viewmodel.TopModeFlowProvider
import dagger.Lazy
import javax.inject.Inject

class RemoteInputDeviceClickHandler @Inject constructor(
    val topModeFlowProvider: TopModeFlowProvider,
    val interactionProvider: InteractionModelFlowProvider,
    private val keepScreenOn: Lazy<KeepScreenOn>,
    private val externalClickCounter: Lazy<ExternalClickCounter>,
) {
    /** Used to indicate a remote control device send a click event. */
    fun onClick(eventTimeMs: Long): Boolean {
        topModeFlowProvider.modeModel.value = Mode.Practice
        interactionProvider.mostRecentInteraction.value = InteractionType.RemoteHumanInterfaceDevice
        interactionProvider.clearMostRecentPocketModeTap()
        keepScreenOn.get().keepScreenOn(updatedDimScreenAfterBriefDelay = true)
        return externalClickCounter.get().handleRhythmUiTaps(eventTimeMs,
            SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_BLUETOOTH, 1)
    }

}