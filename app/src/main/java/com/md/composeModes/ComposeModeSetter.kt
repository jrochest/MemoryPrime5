package com.md.composeModes

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.md.ModeHandler
import com.md.FocusedQueueStateModel
import com.md.SpacedRepeaterActivity
import com.md.modesetters.DeckLoadManager
import com.md.modesetters.ItemDeletedHandler
import com.md.modesetters.ModeSetter
import com.md.testing.TestingMode
import com.md.uiTheme.AppTheme
import com.md.utils.KeepScreenOn
import com.md.viewmodel.InteractionModelFlowProvider
import com.md.viewmodel.InteractionType
import com.md.viewmodel.TopModeFlowProvider
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

enum class Mode {
    Practice,
    NewNote,
    DeckChooser,
    Settings,
    Backup
}

@ActivityScoped
class ComposeModeSetter @Inject constructor(
    @ActivityContext val context: Context,
    private val modeHandler: ModeHandler,
    private val practiceModeViewModel: PracticeModeViewModel,
    private val currentNotePartManager: CurrentNotePartManager,
    private val topModeFlowProvider: TopModeFlowProvider,
    private val deckModeComposableManager: DeckModeComposableManager,
    private val addNoteComposeManager: AddNoteComposeManager,
    private val backupModeComposeManager: BackupModeComposeManager,
    private val practiceModeComposerManager: PracticeModeComposerManager,
    private val focusedQueueStateModel: FocusedQueueStateModel,
    private val deckLoadManager: DeckLoadManager,
    private val keepScreenOn: KeepScreenOn,
    private val interactionProvider: InteractionModelFlowProvider,
    private val testingMode: TestingMode,
) : ModeSetter(), ItemDeletedHandler {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    init {
        parentSetup(activity, modeHandler)
    }

    private var hasAddedContentView = false

    override fun onSwitchToMode(context: Activity) {
        //modeHandler.add(this)
        if (!hasAddedContentView) {
            hasAddedContentView = true
            context.setContentView(ComposeView(context).apply {
                setContent @Composable {
                    AppTheme {
                        Column (Modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.background)) {
                            // This is for testing easily on userdebug devices.
                            if (testingMode.isTestDevice) {
                                Button(onClick = {
                                    interactionProvider.mostRecentInteraction.value =
                                        InteractionType.RemoteHumanInterfaceDevice
                                    interactionProvider.clearMostRecentPocketModeTap()
                                }) {
                                    Text(textAlign = TextAlign.Center,
                                        text = "Fake BT button for testing",
                                        modifier = Modifier.fillMaxWidth())
                                }
                            }
                            // TODOJ delete
                            Surface(color = MaterialTheme.colorScheme.background) {
                                val decks = deckLoadManager.decks.collectAsState().value
                                if (decks == null) {
                                    Text(text = "Loading decks...")
                                    return@Surface
                                }
                                val mode = if (decks.isEmpty()) {
                                    // For the mode to deck chooser to ensure the user
                                    // is guided to a place where a deck can be added.
                                    Mode.DeckChooser
                                } else {
                                    topModeFlowProvider.modeModel.collectAsState().value
                                }
                                if (mode == Mode.Practice) {
                                    val interactionType =
                                        interactionProvider.mostRecentInteraction.collectAsState().value
                                    if (interactionType == InteractionType.RemoteHumanInterfaceDevice) {
                                        practiceModeComposerManager.PocketLowEnergyPracticeComposition()
                                        return@Surface
                                    }
                                }

                                Column {
                                    TopMenu(onPracticeMode = {
                                        topModeFlowProvider.modeModel.value = Mode.Practice
                                        // This initially leaves the recording or deleting state
                                        practiceModeViewModel.practiceStateFlow.value =
                                            PracticeMode.Practicing
                                        currentNotePartManager.clearPending()
                                        this@ComposeModeSetter.keepScreenOn.keepScreenOn()
                                        this@ComposeModeSetter.switchMode(context = activity)
                                    }, onDeckChooseMode = {
                                        topModeFlowProvider.modeModel.value = Mode.DeckChooser
                                        this@ComposeModeSetter.switchMode(context = activity)
                                    }, topModeFlowProvider, focusedQueueStateModel)

                                    when (mode) {
                                        Mode.Practice -> {
                                            practiceModeComposerManager.compose()
                                        }

                                        Mode.DeckChooser -> {
                                            deckModeComposableManager.compose()
                                        }

                                        Mode.NewNote -> {
                                            addNoteComposeManager.compose()
                                        }

                                        Mode.Backup -> {
                                            backupModeComposeManager.compose()
                                        }

                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            })
        }
    }
}

@Composable
fun VerticalDivider() {
    Divider(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .height(48.dp)
            .padding(4.dp)
            .width(1.dp)
    )
}
