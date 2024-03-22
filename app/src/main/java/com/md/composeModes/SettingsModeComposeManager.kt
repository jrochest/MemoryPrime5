package com.md.composeModes

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.md.SpacedRepeaterActivity
import com.md.viewmodel.TopModeFlowProvider
import com.md.workers.IncrementalBackupManager
import com.md.workers.IncrementalBackupPreferences
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ActivityScoped
class SettingsModeStateModel @Inject constructor() {
    val enableLookAhead = MutableStateFlow(false)
    val backupLocationName1 = MutableStateFlow<String?>(null)

    val backupLocationName2 = MutableStateFlow<String?>(null)

    val backupLocationName3 = MutableStateFlow<String?>(null)

    val backupLocationName4 = MutableStateFlow<String?>(null)
}

@ActivityScoped
class SettingsModeComposeManager @Inject constructor(
    @ActivityContext val context: Context,
    private val topModeFlowProvider: TopModeFlowProvider,
    private val stateModel: SettingsModeStateModel

) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    init {
        activity.lifecycleScope.launch {
            topModeFlowProvider.modeModel.collect { mode ->

                if (mode == Mode.Settings) {
                    updateStateModel()
                }
            }
        }
    }

    fun updateStateModel() {
        val prefFile = context.getSharedPreferences(
            IncrementalBackupPreferences.BACKUP_LOCATION_FILE,
            Context.MODE_PRIVATE
        )
        stateModel.backupLocationName1.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_KEY_1, null)
        stateModel.backupLocationName2.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_KEY_2, null)
        stateModel.backupLocationName3.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_KEY_3, null)
        stateModel.backupLocationName4.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_KEY_4, null)
    }

    @Composable
    fun compose() {
        ShowUiForState()
    }

    @Composable
    fun SpaceBetweenSettings() {
        Spacer(modifier = Modifier.height(10.dp))
    }
    @Composable
    fun SpaceLabelAndValue() {
        Spacer(modifier = Modifier.height(2.dp))
    }

    @Composable
    fun ShowUiForState() {
        Column {
            Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

            SpaceBetweenSettings()
            val lookAhead = stateModel.enableLookAhead.collectAsState()
            Text(text = "Look ahead", style = MaterialTheme.typography.labelLarge)
            SpaceLabelAndValue()
            Switch(
                checked = lookAhead.value,
                onCheckedChange = {
                    stateModel.enableLookAhead.value = it
                }
            )

            BackLocationForIndex(IncrementalBackupPreferences.location1) { stateModel.backupLocationName1 }
            BackLocationForIndex(IncrementalBackupPreferences.location2) { stateModel.backupLocationName2 }
            BackLocationForIndex(IncrementalBackupPreferences.location3) { stateModel.backupLocationName3 }
            BackLocationForIndex(IncrementalBackupPreferences.location4) { stateModel.backupLocationName4 }
        }
     }

    @Composable
    private fun BackLocationForIndex(
        location: IncrementalBackupPreferences.BackupLocation,
        flowWithPreferencesValue: () -> MutableStateFlow<String?>
    ) {
        SpaceBetweenSettings()
        Text(text = location.labelForUi, style = MaterialTheme.typography.labelLarge)
        SpaceLabelAndValue()

        val backupLocation = flowWithPreferencesValue().collectAsState().value
        if (backupLocation != null) {
            Text(text = backupLocation, style = MaterialTheme.typography.bodySmall)
            SpaceLabelAndValue()
        }
        Row {
            Button(onClick = { IncrementalBackupManager.openBackupDir(activity, location.requestCode) }) {
                Text(text = "Update backup location", style = MaterialTheme.typography.labelMedium)
            }
            NTapButton(requiredTaps = 3, onNTap = {
                val prefFile = context.getSharedPreferences(
                    IncrementalBackupPreferences.BACKUP_LOCATION_FILE,
                    Context.MODE_PRIVATE
                )
                prefFile.edit().remove(IncrementalBackupPreferences.requestCodeToKey[location.requestCode]).apply()
                updateStateModel()
            }
            ) {
                Text(text = "Triple Click to Clear", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}