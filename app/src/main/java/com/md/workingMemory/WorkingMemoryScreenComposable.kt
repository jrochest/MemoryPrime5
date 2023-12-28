package com.md.workingMemory

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.md.modesetters.CreateModeSetter
import com.md.modesetters.DeckChooseModeSetter
import com.md.modesetters.LearningModeSetter
import com.md.modesetters.SettingModeSetter
import java.time.Instant
import com.md.workers.IncrementalBackupManager

object WorkingMemoryScreen {
    const val MAX_FONT_SIZE = 36
    const val MAX_TAP_GAP_DURATION_TO_DELETE_MILLIS = 300
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkingMemoryScreenComposable(
    notes: SnapshotStateList<ShortTermNote>,
    onNotePress: (note: ShortTermNote) -> Unit = { },
) {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.onBackground) {
            Column(
                Modifier.fillMaxHeight(fraction = .05f),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                TopLevelMenu()
                Button(
                    modifier = Modifier
                        .fillMaxHeight(fraction = .85f)
                        .heightIn(min = 48.dp)
                        .padding(4.dp),
                    onClick = { }
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = "1 tap to proceed\n2 for forgot\n3 for go back ",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                val bottomButtonHeight = 140.dp
                val bottomButtonModifier = Modifier
                    .heightIn(min = bottomButtonHeight)
                    .padding(4.dp)

                Row (Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RecordAgainButton(modifier = bottomButtonModifier.fillMaxWidth(fraction = .5f))
                    Button(
                        modifier = bottomButtonModifier.fillMaxWidth(fraction = 1f),
                        onClick = { }
                    ) {
                        Column {
                            Text(
                                text = "Delete",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Triple tap quickly",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                }
            }

        }
    }


    @Preview
    @Composable
    fun WorkMemoryUiPreview() {
        val notes = SnapshotStateList<ShortTermNote>().apply {
            add(
                ShortTermNote(Instant.parse("2023-12-03T10:15:29.00Z").toEpochMilli())
            )
        }
        WorkingMemoryScreenComposable(notes, onNotePress = {

        })
    }
}


    @Composable
    fun TopLevelMenu() {

        var showMenu by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val activity = LocalContext.current as Activity
            Button(onClick = {
                CreateModeSetter.switchMode(activity)
            }) {
                Text(text = "New note", style = MaterialTheme.typography.labelLarge)
            }
            Button(onClick = {
                LearningModeSetter.instance.switchMode(activity)
            }) {
                Text(text = "Practice", style = MaterialTheme.typography.labelLarge)
            }
            Button(onClick = {
                DeckChooseModeSetter.getInstance().switchMode(activity)

            }) {
                Text(text = "Decks", style = MaterialTheme.typography.labelLarge)
            }
            Button(onClick = {
                IncrementalBackupManager.createAndWriteZipBackToPreviousLocation(
                    activity,
                    activity.contentResolver,
                    shouldSpeak = true,
                    runExtraValidation = false
                )

            }) {
                Text(text = "Backup", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = { showMenu = !showMenu }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem({ Text("Settings") }, onClick = {
                        SettingModeSetter.switchMode(activity)
                    })
                }

            }
        }
    }


    @Composable
    private fun RecordAgainButton(modifier: Modifier) {
        Button(
            modifier = modifier,
            onClick = { }
        ) {
            Column {
                Text(
                    text = "Record again",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Tap and hold to record",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }



