package com.md.composeModes

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.lifecycleScope
import com.md.CategorySingleton
import com.md.DbNoteEditor.Companion.instance
import com.md.FocusedQueueStateModel
import com.md.SpacedRepeaterActivity
import com.md.modesetters.DeckInfo
import com.md.modesetters.DeckLoadManager
import com.md.provider.Deck
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


enum class DeckMode {
    Default,
    AddingDeck
}

@ActivityScoped
class DeckModeStateModel @Inject constructor() {
    val modeModel = MutableStateFlow(DeckMode.Default)
}

@ActivityScoped
class DeckModeComposableManager @Inject constructor(
    @ActivityContext val context: Context,
    private val deckLoadManager: DeckLoadManager,
    private val focusedQueueStateModel: FocusedQueueStateModel,
    private val topModeViewModel: TopModeViewModel,
    private val deckModeStateModel: DeckModeStateModel,
) {

    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    @Composable
    fun compose() {
        DeckModeComposable()
    }

    @Composable
    fun DeckModeComposable() {
        val decks = deckLoadManager.decks.collectAsState().value
        val deckMode = deckModeStateModel.modeModel.collectAsState().value

        Row {
            OutlinedButton(onClick = {
                deckModeStateModel.modeModel.value = DeckMode.AddingDeck
            }) {
                Text(text = "Add deck")
            }
        }
        when (deckMode) {
            DeckMode.Default -> {
                if (decks != null) {
                    DeckList(decks)
                }
            }

            DeckMode.AddingDeck -> {
                var textValue by remember { mutableStateOf(TextFieldValue("")) }
                TextField(
                    modifier = Modifier.semantics {
                        this.contentDescription = "Deck name"
                    },
                    value = textValue,
                    onValueChange = { newText ->
                        textValue = newText
                    },
                    label = { Text(text = "Deck name") },
                    placeholder = { Text(text = "My deck (active)") },
                )
                OutlinedButton(
                    modifier = Modifier.semantics {
                        this.contentDescription = "Save deck"
                    },
                    onClick = {
                    val deck = Deck(/* name= */ textValue.text)
                    instance!!.insertDeck(deck)
                    activity.lifecycleScope.launch {
                        deckModeStateModel.modeModel.value = DeckMode.Default
                        deckLoadManager.refreshDeckListAndFocusFirstActiveNonemptyQueue()
                    }
                }) {
                    Text(text = "Save deck")
                }
            }
        }
    }

    @Composable
    private fun DeckList(decks: List<DeckInfo>) {
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
                            focusedQueueStateModel.deck.value = it
                            CategorySingleton.getInstance().setDeckInfo(it)
                            topModeViewModel.modeModel.value = Mode.Practice
                        }) {
                            Text(text = "Practice", style = MaterialTheme.typography.labelLarge)
                        }
                        VerticalDivider()
                        TextButton(onClick = {

                            focusedQueueStateModel.deck.value = it

                            CategorySingleton.getInstance().setDeckInfo(it)
                            topModeViewModel.modeModel.value = Mode.NewNote
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