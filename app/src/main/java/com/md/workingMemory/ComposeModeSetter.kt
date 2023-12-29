package com.md.workingMemory

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.md.CategorySingleton
import com.md.ModeHandler
import com.md.SpacedRepeaterActivity
import com.md.modesetters.DeckLoadManager
import com.md.modesetters.ItemDeletedHandler
import com.md.modesetters.ModeSetter
import com.md.uiTheme.AppTheme
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

enum class Mode {
    Learning,
    NewNote,
    DeckChooser,
    Settings
}

@ActivityScoped
class ModeViewModel @Inject constructor() {
    val modeModel = MutableStateFlow(Mode.Learning)
}

@ActivityScoped
class AddNoteComposeManager @Inject constructor(
    val activity: SpacedRepeaterActivity,
    val recordButtonController: RecordButtonController
) {

    @Composable
    fun ComposeMode() {
        val buttonModifier = Modifier.fillMaxHeight()
        val firstButtonModifier = buttonModifier.fillMaxWidth(.5f)
        val secondButtonModifier = buttonModifier.fillMaxWidth(1f)
        @Composable
        fun ButtonText(text: String,) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
        Column {
            Row(Modifier.fillMaxHeight(.33f)) {
                Button(modifier = firstButtonModifier,
                    onClick = { /*TODO*/ }) {
                    ButtonText(text = "Record question")
                }
                Button(modifier = secondButtonModifier,
                    onClick = { /*TODO*/ }) {
                    ButtonText(text = "Save question")
                }
            }
            Row(Modifier.fillMaxHeight(.5f)) {
                Button(modifier = firstButtonModifier,
                    onClick = { /*TODO*/ }) {
                    ButtonText(text = "Record answer")
                }
                Button(modifier = secondButtonModifier,
                    onClick = { /*TODO*/ }) {
                    ButtonText(text = "Save answer")
                }
            }
            Row(Modifier.fillMaxHeight(1f)) {
                Button(modifier = firstButtonModifier,
                    onClick = { /*TODO*/ }) {
                    ButtonText(text = "Reset")
                }
                Button(modifier = secondButtonModifier,
                    onClick = { /*TODO*/ }) {
                    ButtonText(text = "Save note")
                }
            }
        }
    }
}

@ActivityScoped
class ComposeModeSetter @Inject constructor(
    val activity: SpacedRepeaterActivity,
    private val modeHandler: ModeHandler,
    private val modeViewModel: ModeViewModel,
    private val deckLoadManager: DeckLoadManager,
    private val recordButtonController: RecordButtonController,
    private val addNoteComposeManager: AddNoteComposeManager,
) : ModeSetter(), ItemDeletedHandler {
    init {
        parentSetup(activity, modeHandler)
    }

    var hasAddedContentView = false

    override fun switchModeImpl(context: Activity) {
        modeHandler.add(this)
        if (!hasAddedContentView) {
            hasAddedContentView = true
            context.setContentView(ComposeView(context).apply {
                setContent  @Composable {
                    AppTheme {
                        Surface {
                            Column {
                                val mode = modeViewModel.modeModel.collectAsState()
                                TopLevelMenu(onLearningMode = {
                                    modeViewModel.modeModel.value = Mode.Learning
                                    this@ComposeModeSetter.switchMode(context = activity)
                                }, onDeckChooseMode = {
                                    modeViewModel.modeModel.value = Mode.DeckChooser
                                    this@ComposeModeSetter.switchMode(context = activity)
                                }, modeViewModel)
                                when (mode.value) {
                                    Mode.Learning -> {
                                        WorkingMemoryScreenComposable(
                                            onAudioRecorderTripleTap = { recordButtonController.onTripleTap() })
                                    }
                                    Mode.DeckChooser -> {
                                        DeckModeComposable(context)
                                    }
                                    Mode.NewNote -> {
                                        addNoteComposeManager.ComposeMode()
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            })
        }
    }

    @Composable
    fun DeckModeComposable(context: Activity) {
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
                                CategorySingleton.getInstance().setDeckInfo(it)
                                modeViewModel.modeModel.value = Mode.Learning
                            }) {
                                Text(text = "Practice", style = MaterialTheme.typography.labelLarge)
                            }
                            VerticalDivider()
                            TextButton(onClick = {
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

    @Composable
    private fun VerticalDivider() {
        Divider(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .height(48.dp)
                .padding(4.dp)
                .width(1.dp)
        )
    }
}
