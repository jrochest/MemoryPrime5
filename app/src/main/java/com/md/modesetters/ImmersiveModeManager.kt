package com.md.modesetters

import android.content.Context
import android.os.Build
import android.view.WindowInsetsController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.md.*
import com.md.composeModes.CurrentNotePartManager
import com.md.composeModes.Mode
import com.md.provider.Deck
import com.md.viewmodel.InteractionModelFlowProvider
import com.md.viewmodel.InteractionType
import com.md.viewmodel.TopModeFlowProvider
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@ActivityScoped
class ImmersiveModeManager @Inject constructor(
    @ActivityContext val context: Context,
    private val topModeProvider: TopModeFlowProvider,
    private val interactionProvider: InteractionModelFlowProvider,
) {

    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    init {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                topModeProvider.modeModel.combine(interactionProvider.mostRecentInteraction) { mode, iteraction ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        return@combine
                    }
                    val controller = activity.window.insetsController ?: return@combine
                    if (mode == Mode.Practice && iteraction == InteractionType.RemoteHumanInterfaceDevice) {
                        controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                        controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                    } else {
                        controller.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                        controller.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                    }
                }.collect {

                }
            }
        }
    }
}

