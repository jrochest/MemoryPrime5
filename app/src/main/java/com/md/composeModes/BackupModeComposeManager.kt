package com.md.composeModes

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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
    val pendingZipDirSets = mutableSetOf <String>()
    val remainingZipsToWriteCount = MutableStateFlow(0)
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
    fun KeepScreenOn() {
        val currentView = LocalView.current
        DisposableEffect(Unit) {
            currentView.keepScreenOn = true
            onDispose {
                currentView.keepScreenOn = false
            }
        }
    }

    @Composable
    fun compose() {
        // Don't interrupt the backup.
        KeepScreenOn()
        Column (modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          ShowUiForState()
        }
    }

    @Composable
    fun ShowUiForState() {


        val summary = backupModeStateModel.summary.collectAsState()
        val errorMessage = backupModeStateModel.errorMessage.collectAsState()
        val writtenItemsLog = backupModeStateModel.remainingItems.collectAsState()

        val remainingZipsToWriteCount = backupModeStateModel.remainingZipsToWriteCount.collectAsState()
        val inProgress = backupModeStateModel.backupInProgress.collectAsState()
        if (inProgress.value) {
            Text(
                text = "Backup in progress… ",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Text(
            text = "Backup status:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Text(
            text = summary.value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
        )
        Text(
            text = "Zips to write: ${remainingZipsToWriteCount.value}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
        )
        Text(
            text = writtenItemsLog.value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
        )
        if (errorMessage.value.isNotBlank()) {
            Text(
                text = errorMessage.value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}