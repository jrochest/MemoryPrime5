package com.md.workingMemory

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.ComposeView
import com.md.ModeHandler
import com.md.SpacedRepeaterActivity
import com.md.modesetters.ItemDeletedHandler
import com.md.modesetters.ModeSetter
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

enum class Mode {
    Learning,
    NewNote,
    DeckChooser,
    Settings
}

@ActivityScoped
class ModeViewModel @Inject constructor() {
    val modeModel = MutableStateFlow(Mode.Learning)
}

@ActivityScoped
class ComposeModeSetter @Inject constructor(
    val activity: SpacedRepeaterActivity,
    private val modeHandler: ModeHandler,
    private val modeViewModel: ModeViewModel,
    private val recordButtonController: RecordButtonController
) : ModeSetter(), ItemDeletedHandler {
    init {
        parentSetup(activity, modeHandler)
    }

    var hasAddedContentView = false

    override fun switchModeImpl(context: Activity) {
        modeHandler.add(this)
        if (!hasAddedContentView) {
            hasAddedContentView = true
            context.setContentView(ComposeView(context).apply {
                setContent  @Composable {
                    val mode = modeViewModel.modeModel.collectAsState()
                    val notes = SnapshotStateList<ShortTermNote>()
                    if (mode.value == Mode.Learning) {
                        WorkingMemoryScreenComposable(
                            notes,
                            onAudioRecorderTripleTap = { recordButtonController.onTripleTap() },
                            onNotePress = { note -> note.onTap(notes) },
                            onLearningMode = { switchMode(context = activity) })
                    }
                }
            })
        }
    }

}