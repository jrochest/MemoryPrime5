package com.md.composeModes

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import com.md.SpacedRepeaterActivity
import com.md.utils.KeepScreenOn
import com.md.viewmodel.TopModeFlowProvider
import com.md.workers.IncrementalBackupManager
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ActivityScoped
class BackupModeStateModel @Inject constructor() {
    val summary = MutableStateFlow("Starting backup...")
    val remainingItems = MutableStateFlow("")
    val errorMessage = MutableStateFlow("")
    val backupInProgress = MutableStateFlow(false)
}

@ActivityScoped
class BackupModeComposeManager @Inject constructor(
    @ActivityContext val context: Context,
    private val topModeFlowProvider: TopModeFlowProvider,
    private val backupModeStateModel: BackupModeStateModel,
    private val keepScreenOn: KeepScreenOn

) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    init {
        activity.lifecycleScope.launch {
            topModeFlowProvider.modeModel.collect() { mode ->
                if (mode == Mode.Backup && !backupModeStateModel.backupInProgress.value) {
                    keepScreenOn.keepScreenOn(extraScreenOnDuration = java.time.Duration.ofMinutes(1000))
                    backupModeStateModel.backupInProgress.value = true
                    IncrementalBackupManager.createAndWriteZipBackToPreviousLocation(
                        activity,
                        activity.contentResolver,
                        shouldSpeak = true,
                        runExtraValidation = true,
                        backupModeStateModel = backupModeStateModel
                    )
                }
            }
        }


    }

    @Composable
    fun compose() {
        ShowUiForState()
    }

    @Composable
    fun ShowUiForState() {
        val summary = backupModeStateModel.summary.collectAsState()
        val errorMessage = backupModeStateModel.errorMessage.collectAsState()
        val remainingItems = backupModeStateModel.remainingItems.collectAsState()
        val inProgress = backupModeStateModel.backupInProgress.collectAsState()
        if (inProgress.value) {
            Text(text = "Backup in progress... ", style = MaterialTheme.typography.headlineSmall)
        }
        Text(text = "Backup status: ", style = MaterialTheme.typography.headlineSmall)
        Text(text = summary.value, style = MaterialTheme.typography.bodyLarge)
        Text(text = remainingItems.value, style = MaterialTheme.typography.bodyMedium)
        Text(text = errorMessage.value, style = MaterialTheme.typography.bodyLarge)
    }
}