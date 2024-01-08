package com.md.composeModes

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.md.ModeHandler
import com.md.FocusedQueueStateModel
import com.md.SpacedRepeaterActivity
import com.md.modesetters.ItemDeletedHandler
import com.md.modesetters.ModeSetter
import com.md.uiTheme.AppTheme
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

enum class Mode {
    Practice,
    NewNote,
    DeckChooser,
    Settings,
    Backup
}

@ActivityScoped
class TopModeViewModel @Inject constructor() {
    val modeModel = MutableStateFlow(Mode.Practice)
}

@ActivityScoped
class ComposeModeSetter @Inject constructor(
    @ActivityContext val context: Context,
    private val modeHandler: ModeHandler,
    private val practiceModeViewModel: PracticeModeViewModel,
    private val currentNotePartManager: CurrentNotePartManager,
    private val topModeViewModel: TopModeViewModel,
    private val deckModeComposableManager: DeckModeComposableManager,
    private val addNoteComposeManager: AddNoteComposeManager,
    private val backupModeComposeManager: BackupModeComposeManager,
    private val practiceModeComposerManager: PracticeModeComposerManager,
    private val focusedQueueStateModel: FocusedQueueStateModel,
) : ModeSetter(), ItemDeletedHandler {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }
    init {
        parentSetup(activity, modeHandler)
    }

    var hasAddedContentView = false

    override fun onSwitchToMode(context: Activity) {
        //modeHandler.add(this)
        if (!hasAddedContentView) {
            hasAddedContentView = true
            context.setContentView(ComposeView(context).apply {
                setContent  @Composable {
                    AppTheme {
                        Surface (color = MaterialTheme.colorScheme.background) {
                            Column {
                                val mode = topModeViewModel.modeModel.collectAsState()
                                TopMenu(onPracticeMode = {
                                    topModeViewModel.modeModel.value = Mode.Practice
                                    // This initially leaves the recording or deleting state
                                    practiceModeViewModel.practiceStateFlow.value = PracticeMode.Practicing
                                    currentNotePartManager.clearPending()
                                    this@ComposeModeSetter.switchMode(context = activity)
                                }, onDeckChooseMode = {
                                    topModeViewModel.modeModel.value = Mode.DeckChooser
                                    this@ComposeModeSetter.switchMode(context = activity)
                                }, topModeViewModel, focusedQueueStateModel)
                                when (mode.value) {
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
