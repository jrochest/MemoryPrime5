package com.md.composeModes

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.md.CategorySingleton
import com.md.RevisionQueueStateModel
import com.md.modesetters.DeckLoadManager
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class DeckModeComposableManager @Inject constructor(
    @ActivityContext val context: Context,
    private val deckLoadManager: DeckLoadManager,
    private val revisionQueueStateModel: RevisionQueueStateModel,
    private val modeViewModel: ModeViewModel,
) {
    @Composable
    fun compose() {
        DeckModeComposable()
    }

    @Composable
    fun DeckModeComposable() {
        val decks = deckLoadManager.decks.collectAsState().value
        if (decks != null) {
            Row {
                Button(onClick = { /*TODO*/ }) {
                    Text(text = "Add deck")
                }
            }
            Column {
                decks.forEach {
                    Divider()
                    Column {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = it.name,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Row {
                            Text(
                                text = "Queue: " + it.revisionQueue.getSize() + "\nTotal: " + it.deckCount,
                                style = MaterialTheme.typography.labelLarge
                            )
                            VerticalDivider()
                            TextButton(onClick = {
                                revisionQueueStateModel.queue.value = it.revisionQueue
                                CategorySingleton.getInstance().setDeckInfo(it)
                                modeViewModel.modeModel.value = Mode.Practice
                            }) {
                                Text(text = "Practice", style = MaterialTheme.typography.labelLarge)
                            }
                            VerticalDivider()
                            TextButton(onClick = {
                                revisionQueueStateModel.queue.value = it.revisionQueue
                                CategorySingleton.getInstance().setDeckInfo(it)
                                modeViewModel.modeModel.value = Mode.NewNote
                            }) {
                                Text(
                                    text = "Add to deck",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            var expanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More"
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        Toast.makeText(
                                            context,
                                            "Rename",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        Toast.makeText(
                                            context,
                                            "Delete",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}