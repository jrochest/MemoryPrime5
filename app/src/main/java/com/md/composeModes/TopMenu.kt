package com.md.composeModes

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.md.FocusedQueueStateModel
import com.md.RestoreFromIncrementalDirectoryManager
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

    data class NavItem(
        val mode: Mode?,
        val label: String,
        val icon: ImageVector,
        val onClick: () -> Unit,
    )

    val navItems = listOf(
        NavItem(Mode.NewNote, "Create", Icons.Default.Add) {
            topModeFlowProvider.modeModel.value = Mode.NewNote
        },
        NavItem(Mode.Practice, "Practice", Icons.Outlined.School) {
            onPracticeMode()
        },
        NavItem(Mode.DeckChooser, "Decks", Icons.Outlined.LibraryBooks) {
            onDeckChooseMode()
        },
        NavItem(Mode.Backup, "Backup", Icons.Outlined.Backup) {
            topModeFlowProvider.modeModel.value = Mode.Backup
        },
    )

    val activity = LocalContext.current as Activity

    Column {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            for (item in navItems) {
                NavigationBarItem(
                    selected = item.mode == mode.value,
                    onClick = item.onClick,
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                        )
                    },
                    label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }
            // More / overflow item
            NavigationBarItem(
                selected = false,
                onClick = { showMenu = !showMenu },
                icon = {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More",
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
                },
                label = { Text("More") },
                colors = NavigationBarItemDefaults.colors(
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }

        // Deck name below nav
        val deck = focusedQueueStateModel.deck.collectAsState().value
        if (deck != null) {
            Text(
                text = "Deck: " + deck.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}