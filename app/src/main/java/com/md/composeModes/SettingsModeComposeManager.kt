package com.md.composeModes

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioDeviceInfo.TYPE_USB_HEADSET
import android.media.AudioManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.md.DefaultDeckToAddNewNotesToSharedPreference
import com.md.SpacedRepeaterActivity
import com.md.modesetters.TtsSpeaker
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
    val backupLocationNickname1 = MutableStateFlow<String?>(null)

    val backupLocationName2 = MutableStateFlow<String?>(null)
    val backupLocationNickname2 = MutableStateFlow<String?>(null)

    val backupLocationName3 = MutableStateFlow<String?>(null)
    val backupLocationNickname3 = MutableStateFlow<String?>(null)

    val backupLocationName4 = MutableStateFlow<String?>(null)
    val backupLocationNickname4 = MutableStateFlow<String?>(null)

    val preferredMic = MutableStateFlow<AudioDeviceInfo?>(null)

    val speedUpPlayback = MutableStateFlow(false)

    val increaseLoudness = MutableStateFlow(false)
}

@ActivityScoped
class SettingsModeComposeManager @Inject constructor(
    @ActivityContext val context: Context,
    private val topModeFlowProvider: TopModeFlowProvider,
    private val stateModel: SettingsModeStateModel,
    private val workingModeSetter: dagger.Lazy<ComposeModeSetter> ,
    private val deckModeStateModel: DeckModeStateModel
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
        stateModel.backupLocationNickname1.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_NICKNAME_KEY_1, null)

        stateModel.backupLocationName2.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_KEY_2, null)
        stateModel.backupLocationNickname2.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_NICKNAME_KEY_2, null)

        stateModel.backupLocationName3.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_KEY_3, null)
        stateModel.backupLocationNickname3.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_NICKNAME_KEY_3, null)

        stateModel.backupLocationName4.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_KEY_4, null)
        stateModel.backupLocationNickname4.value =
            prefFile.getString(IncrementalBackupPreferences.BACKUP_LOCATION_NICKNAME_KEY_4, null)
    }

    @Composable
    fun compose() {
        ShowUiForState()
    }

    @Composable
    fun SpaceBetweenSettings() {
        Spacer(modifier = Modifier.height(30.dp))
    }

    @Composable
    fun SpaceLabelAndValue() {
        Spacer(modifier = Modifier.height(2.dp))
    }

    @Composable
    private fun DefaultDeckPicker() {
        val currentDeckName = DefaultDeckToAddNewNotesToSharedPreference.getDeck(activity)?.name ?: "(no deck selected yet)"
        val currentDeckLabel = "Deck to add notes to: \'$currentDeckName\'"
        Text(
            text = currentDeckLabel,
        )
        SpaceLabelAndValue()
        Button(onClick = {
            deckModeStateModel.modeModel.value = DeckMode.ChooseDeckToAddNewItemsTo
            topModeFlowProvider.modeModel.value = Mode.DeckChooser
            workingModeSetter.get().switchMode(context = activity)
        }) {
            Text(
                text = "Choose a default deck to add notes to",
            )
        }
    }


    @Composable
    fun ShowUiForState() {
        Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
            Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
            SpaceBetweenSettings()

            SectionHeading("Incremental backup locations")
            SpaceBetweenSettings()

            BackLocationForIndex(IncrementalBackupPreferences.location1, { stateModel.backupLocationName1 }, { stateModel.backupLocationNickname1 })
            BackLocationForIndex(IncrementalBackupPreferences.location2, { stateModel.backupLocationName2 }, { stateModel.backupLocationNickname2 })
            BackLocationForIndex(IncrementalBackupPreferences.location3, { stateModel.backupLocationName3 }, { stateModel.backupLocationNickname3 })
            BackLocationForIndex(IncrementalBackupPreferences.location4, { stateModel.backupLocationName4 }, { stateModel.backupLocationNickname4 })

            SpaceBetweenSettings()
            SectionHeading("Default deck")

            DefaultDeckPicker()


            SpaceBetweenSettings()

            SectionHeading("Activity lifetime settings")

            SpaceBetweenSettings()
            PlaybackSpeedSetting()

            SpaceBetweenSettings()
            PlaybackAudioIncreaseSetting()

            SpaceBetweenSettings()
            LookAheadSetting()

            SpaceBetweenSettings()
            MicrophoneSetting()
        }
    }

    @Composable
    private fun SectionHeading(sectionText: String) {
        Text(
            text = sectionText,
            style = MaterialTheme.typography.titleLarge
        )
    }

    @Composable
    private fun LookAheadSetting() {
        val lookAhead = stateModel.enableLookAhead.collectAsState()
        Text(
            text = "Look ahead (not integrated yet)",
            style = MaterialTheme.typography.labelLarge
        )
        SpaceLabelAndValue()
        Switch(
            checked = lookAhead.value,
            onCheckedChange = {
                stateModel.enableLookAhead.value = it
            }
        )
    }

    @Composable
    private fun MicrophoneSetting() {
        val preferredMic = stateModel.preferredMic.collectAsState().value
        if (preferredMic == null) {
            Text(
                text = "Using DEFAULT mic",
                style = MaterialTheme.typography.labelLarge
            )
        } else if (preferredMic.type == TYPE_USB_HEADSET) {
            Text(
                text = "Using TYPE_USB_HEADSET mic",
                style = MaterialTheme.typography.labelLarge
            )
        }
        TextButton(onClick = {
            stateModel.preferredMic.value = null
        }) {
            Text(
                text = "Switch to DEFAULT mic",
                style = MaterialTheme.typography.labelLarge
            )
        }
        TextButton(onClick = {
            val audioManager =
                context.getSystemService(AppCompatActivity.AUDIO_SERVICE) as AudioManager
            val headset: AudioDeviceInfo? =
                audioManager.getDevices(
                    AudioManager.GET_DEVICES_INPUTS
                ).find { it.type == TYPE_USB_HEADSET }
            if (headset != null) {
                stateModel.preferredMic.value = headset
                TtsSpeaker.speak("Found TYPE_USB_HEADSET")
            } else {
                TtsSpeaker.speak("Fail")
            }
        }) {
            Text(
                text = "Switch to Mic TYPE_USB_HEADSET",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }


    @Composable
    private fun PlaybackSpeedSetting() {
        Text(
            text = "Speed up playback",
            style = MaterialTheme.typography.labelLarge
        )
        SpaceLabelAndValue()
        val speedUpPlayback = stateModel.speedUpPlayback.collectAsState().value
        Switch(
            checked = speedUpPlayback,
            onCheckedChange = {
                stateModel.speedUpPlayback.value = it
            }
        )
    }

    @Composable
    private fun PlaybackAudioIncreaseSetting() {
        Text(
            text = "Increase audio loudness",
            style = MaterialTheme.typography.labelLarge
        )
        SpaceLabelAndValue()
        val speedUpPlayback = stateModel.increaseLoudness.collectAsState().value
        Switch(
            checked = speedUpPlayback,
            onCheckedChange = {
                stateModel.increaseLoudness.value = it
            }
        )
    }

    @Composable
    private fun BackLocationForIndex(
        location: IncrementalBackupPreferences.BackupLocation,
        flowWithPreferencesValue: () -> MutableStateFlow<String?>,
        flowWithNicknameValue: () -> MutableStateFlow<String?>
    ) {
        val backupLocation = flowWithPreferencesValue().collectAsState().value
        val nickname = flowWithNicknameValue().collectAsState().value ?: ""
        var isEditingNickname by remember(nickname) { mutableStateOf(nickname.isEmpty()) }
        var nicknameInputValue by remember(nickname) { mutableStateOf(nickname) }

        androidx.compose.material3.ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = location.labelForUi, style = MaterialTheme.typography.titleMedium)
                
                if (backupLocation != null) {
                    androidx.compose.material3.OutlinedTextField(
                        value = nicknameInputValue,
                        onValueChange = { nicknameInputValue = it },
                        readOnly = !isEditingNickname,
                        label = { Text("Backup Nickname") },
                        placeholder = { Text("e.g. SanDisk Extreme SSD") },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (isEditingNickname) {
                                    // Save
                                    val prefFile = context.getSharedPreferences(
                                        IncrementalBackupPreferences.BACKUP_LOCATION_FILE,
                                        Context.MODE_PRIVATE
                                    )
                                    prefFile.edit()
                                        .putString(location.nicknameKey, nicknameInputValue)
                                        .apply()
                                    updateStateModel()
                                    isEditingNickname = false
                                } else {
                                    // Edit
                                    isEditingNickname = true
                                }
                            }) {
                                Icon(
                                    imageVector = if (isEditingNickname) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = if (isEditingNickname) "Save Nickname" else "Edit Nickname"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        singleLine = true
                    )
                    SpaceLabelAndValue()
                    Text(
                        text = "Path: $backupLocation", 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SpaceLabelAndValue()
                } else {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No backup location configured yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    androidx.compose.material3.FilledTonalButton(onClick = {
                        IncrementalBackupManager.openBackupDir(
                            activity,
                            location.requestCode
                        )
                    }) {
                        Text(text = "Update backup location", style = MaterialTheme.typography.labelMedium)
                    }
                    if (backupLocation != null) {
                        NTapButton(requiredTaps = 3, onNTap = {
                            val prefFile = context.getSharedPreferences(
                                IncrementalBackupPreferences.BACKUP_LOCATION_FILE,
                                Context.MODE_PRIVATE
                            )
                            prefFile.edit()
                                .remove(IncrementalBackupPreferences.requestCodeToKey[location.requestCode])
                                .remove(location.nicknameKey)
                                .apply()
                            updateStateModel()
                        }) {
                            Text(text = "Triple Click to Clear", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}