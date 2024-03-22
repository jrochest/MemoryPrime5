package com.md.composeModes

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.md.FocusedQueueStateModel
import com.md.RestoreFromIncrementalDirectoryManager
import com.md.composeStyles.ButtonStyles.ImportantButtonColor
import com.md.composeStyles.ButtonStyles.MediumImportanceButtonColor
import com.md.modesetters.SettingModeSetter
import com.md.viewmodel.TopModeFlowProvider

@Composable
fun TopMenu(
    onPracticeMode: () -> Unit,
    onDeckChooseMode: () -> Unit,
    topModeFlowProvider: TopModeFlowProvider,
    focusedQueueStateModel: FocusedQueueStateModel,
    ) {
    val mode = topModeFlowProvider.modeModel.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    @Composable
    fun MenuButton(
        myMode: Mode?,
        onClick: () -> Unit,
        label: String? = null,
        sentContent: @Composable (() -> Unit) = {},
    ) {
        OutlinedButton(
            modifier = Modifier.widthIn(max = (LocalConfiguration.current.screenWidthDp / 5).dp)
                .heightIn(min = 120.dp)
                .padding(start=1.dp, end = 1.dp).semantics {
                    this.contentDescription = label ?: "overflow"
                }.defaultMinSize(minHeight = 2.dp, minWidth = 2.dp),
            colors = if (myMode != null && myMode == mode.value) {
                ImportantButtonColor()
            } else {
                MediumImportanceButtonColor()
                    },
            contentPadding = PaddingValues(0.dp),
            onClick = onClick,
            content = {
                if (label != null) {
                    Column(modifier = Modifier.align(alignment = Alignment.Top).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
                        // Show the first letter with large text size.
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = label.substring(startIndex = 0, endIndex = 1),
                            style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = label,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge)
                        sentContent()
                    }

                }

            })

    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val activity = LocalContext.current as Activity
        MenuButton(
            label = "Create\nnote",
            myMode = Mode.NewNote,
            onClick = {
            topModeFlowProvider.modeModel.value = Mode.NewNote
        })
        MenuButton(
            myMode = Mode.Practice,
            label = "Practice",
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
                topModeFlowProvider.modeModel.value = Mode.Backup
        })
        MenuButton(
            label = "More",
            myMode = null,
            onClick = {
                showMenu = !showMenu
            }) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem({ Text("Settings") }, onClick = {
                    topModeFlowProvider.modeModel.value = Mode.Settings
                    showMenu = false
                })
                DropdownMenuItem({ Text("Restore from incremental directory") }, onClick = {
                    RestoreFromIncrementalDirectoryManager.openZipFileDocument(activity)
                    showMenu = false
                })
            }
        }
    }
    val deck = focusedQueueStateModel.deck.collectAsState().value
    if (deck != null) {
        Text(
            text = "Deck: " + deck.name,
            style = MaterialTheme.typography.headlineSmall
        )
    }

}