package com.md.composeModes

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.widget.EditText
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
import com.md.utils.ToastSingleton
import com.md.viewmodel.TopModeFlowProvider
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
    private val topModeFlowProvider: TopModeFlowProvider,
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
        
        if (decks == null) {
            Text(text = "Waiting for decks...")
            return
        }
        val deckMode  = if (decks.isEmpty()) {
            // Force adding mode to guide the user to a place where decks can be added.
            DeckMode.AddingDeck
        } else {
            deckModeStateModel.modeModel.collectAsState().value
        }

        Row {
            OutlinedButton(onClick = {
                deckModeStateModel.modeModel.value = DeckMode.AddingDeck
            }) {
                Text(text = "Add deck")
            }
        }
        when (deckMode) {
            DeckMode.Default -> {
                DeckList(decks)
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
                        deckUpdateActivityReload()
                    }) {
                    Text(text = "Save deck")
                }
            }
        }
    }

    @Composable
    private fun DeckList(decks: List<DeckInfo>) {
        Column {
            decks.forEach { deckInfo: DeckInfo ->
                Divider()
                Column {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = deckInfo.name,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Row {

                        val deckIdToDeckSize = deckLoadManager.deckIdToDeckSize.collectAsState().value
                        var description = "Queue: " + deckInfo.revisionQueue.getSize()
                        if (deckIdToDeckSize != null) {
                            description += "\nTotal: " + deckIdToDeckSize[deckInfo.id]
                        }
                        Text(
                            text = description,
                            style = MaterialTheme.typography.labelLarge
                        )

                        VerticalDivider()
                        TextButton(onClick = {
                            focusedQueueStateModel.deck.value = deckInfo
                            CategorySingleton.getInstance().setDeckInfo(deckInfo)
                            topModeFlowProvider.modeModel.value = Mode.Practice
                        }) {
                            Text(text = "Practice", style = MaterialTheme.typography.labelLarge)
                        }
                        VerticalDivider()
                        TextButton(onClick = {
                            focusedQueueStateModel.deck.value = deckInfo
                            CategorySingleton.getInstance().setDeckInfo(deckInfo)
                            topModeFlowProvider.modeModel.value = Mode.NewNote
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
                        val name = deckInfo.name
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                        handleDeckRename(deckInfo)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete deck: $name") },
                                onClick = {
                                    handleDeckDelete(deckInfo)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleDeckRename(deckInfo: DeckInfo) {
        val name = deckInfo.name
        val alert = AlertDialog.Builder(activity)
        alert.setTitle(
            "Please Enter a new Deck Name for: $name"
        )
        // Set an EditText view to get user input

        // Set an EditText view to get user input
        val input = EditText(activity)
        input.setText(name)

        alert.setView(input)

        alert.setPositiveButton(
            "Ok"
        ) { _: DialogInterface?, _: Int ->
            val value = input.text
            val newName = value.toString()
            if (newName.isBlank()) {
                ToastSingleton.getInstance().speakAndShow("The input is blank")
                return@setPositiveButton
            }

            val deck = Deck(deckInfo.id, newName)
            deckInfo.deck = deck
            instance!!.updateDeck(deck)


        }


        alert.setNegativeButton(
            "Cancel"
        ) { _, _ ->
            // Do nothing.
        }

        alert.create().show()

    }


    private fun handleDeckDelete(deckInfo: DeckInfo) {
        val name = deckInfo.name
        if (name.contains("saved") || name.contains("protected")) {
            val message = "Cannot delete saved or protected deck."
            ToastSingleton.getInstance().speakAndShow(message)
            return
        }

        val alert = AlertDialog.Builder(activity)
        alert.setTitle(
            "Are you sure you want the delete: '${name}'?"
        )

        alert.setPositiveButton("Yes, Delete") { _, _ ->
            instance!!.deleteDeck(deckInfo.deck)
            deckUpdateActivityReload()
        }

        alert.setNegativeButton(
            "Cancel"
        ) { _, _ ->
            // Do nothing.
        }
        alert.create().show()
    }

    private fun deckUpdateActivityReload() {
        activity.lifecycleScope.launch {
            activity.recreate()
        }
    }
}