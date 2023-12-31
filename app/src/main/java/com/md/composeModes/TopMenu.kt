package com.md.composeModes

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.md.composeStyles.ButtonStyles.ImportantButtonColor
import com.md.composeStyles.ButtonStyles.MediumImportanceButtonColor
import com.md.modesetters.SettingModeSetter
import com.md.workers.IncrementalBackupManager

@Composable
fun TopMenu(
    onPracticeMode: () -> Unit,
    onDeckChooseMode: () -> Unit,
    modeViewModel: ModeViewModel
) {
    val mode = modeViewModel.modeModel.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    @Composable
    fun MenuButton(myMode: Mode?,
        onClick: () -> Unit,
                   content: @Composable RowScope.() -> Unit) {
        OutlinedButton(
            modifier = Modifier.height(64.dp),
            colors = if (myMode != null && myMode == mode.value) {
                ImportantButtonColor()
            } else {
                MediumImportanceButtonColor()
                    },
            onClick = onClick,
            content = content)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val activity = LocalContext.current as Activity
        MenuButton(
            myMode = Mode.NewNote,
            onClick = {
            modeViewModel.modeModel.value = Mode.NewNote
        }) {
            Text(text = "Add note",
                style = MaterialTheme.typography.labelLarge)
        }
        MenuButton(
            myMode = Mode.Practice,
            onClick = {
            onPracticeMode()
        }) {
            Text(text = "Practice", style = MaterialTheme.typography.labelLarge)
        }
        MenuButton(
            myMode = Mode.DeckChooser,
            onClick = {
            onDeckChooseMode()
        }) {
            Text(text = "Decks", style = MaterialTheme.typography.labelLarge)
        }
        MenuButton(
            myMode = Mode.Backup,
            onClick = {
                modeViewModel.modeModel.value = Mode.Backup
        }) {
            Text(text = "Backup", style = MaterialTheme.typography.labelLarge)
        }
        MenuButton(
            myMode = null,
            onClick = {
                showMenu = !showMenu
            }) {
            Text(text = "More", style = MaterialTheme.typography.labelLarge)
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
        }
    }
}