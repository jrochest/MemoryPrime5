package com.md.composeModes

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.md.RestoreFromIncrementalDirectoryManager
import com.md.composeStyles.ButtonStyles.ImportantButtonColor
import com.md.composeStyles.ButtonStyles.MediumImportanceButtonColor
import com.md.modesetters.SettingModeSetter

@Composable
fun TopMenu(
    onPracticeMode: () -> Unit,
    onDeckChooseMode: () -> Unit,
    modeViewModel: ModeViewModel
) {
    val mode = modeViewModel.modeModel.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    @Composable
    fun MenuButton(
        myMode: Mode?,
        onClick: () -> Unit,
        label: String? = null,
        content: @Composable() (RowScope.() -> Unit) = {},
    ) {
        OutlinedButton(
            modifier = Modifier.heightIn(min = 120.dp).widthIn(max = 100.dp),
            colors = if (myMode != null && myMode == mode.value) {
                ImportantButtonColor()
            } else {
                MediumImportanceButtonColor()
                    },
            onClick = onClick,
            content = {
                if (label != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Show the first letter with large text size.
                        Text(
                            text = label.substring(startIndex = 0, endIndex = 1),
                            style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = label,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge)
                    }
                    content()
                }

            })

    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val activity = LocalContext.current as Activity
        MenuButton(
            label = "Create\nnote",
            myMode = Mode.NewNote,
            onClick = {
            modeViewModel.modeModel.value = Mode.NewNote
        })
        MenuButton(
            myMode = Mode.Practice,
            label = "Review\nnotes",
            onClick = {
            onPracticeMode()
        })
        MenuButton(
            myMode = Mode.DeckChooser,
            label = "Decks",
            onClick = {
            onDeckChooseMode()
        })
        MenuButton(
            myMode = Mode.Backup,
            label = "Backup",
            onClick = {
                modeViewModel.modeModel.value = Mode.Backup
        })
        MenuButton(
            label = "More\noptions",
            myMode = null,
            onClick = {
                showMenu = !showMenu
            }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem({ Text("Settings") }, onClick = {
                    SettingModeSetter.switchMode(activity)
                })
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem({ Text("Settings") }, onClick = {
                    SettingModeSetter.switchMode(activity)
                })
                DropdownMenuItem({ Text("Restore from incremental directory") }, onClick = {
                    RestoreFromIncrementalDirectoryManager.openZipFileDocument(activity)
                })
            }
        }
    }
}