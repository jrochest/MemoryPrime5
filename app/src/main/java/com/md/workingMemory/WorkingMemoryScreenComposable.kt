package com.md.workingMemory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import com.md.uiTheme.AppTheme

object WorkingMemoryScreen {
    const val MAX_FONT_SIZE = 36
    const val MAX_TAP_GAP_DURATION_TO_DELETE_MILLIS = 300
}

@Composable
fun WorkingMemoryScreenComposable(
    notes: SnapshotStateList<ShortTermNote>,
    onNotePress: (note: ShortTermNote) -> Unit = { },
) {
    AppTheme {
        val shortTermNotes = remember { notes }
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {

                AddMemoryButton(shortTermNotes)
                LazyColumn {
                    itemsIndexed(shortTermNotes) { index: Int, note: ShortTermNote ->
                        ExistingMemoryButton(index = index, note = note, onNotePress)
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


@Composable
private fun AddMemoryButton(notes: SnapshotStateList<ShortTermNote>) {
    WorkingMemoryButton(label = "Tap tap hold to record", WorkingMemoryScreen.MAX_FONT_SIZE
    ) {
        notes.add(0, ShortTermNote())
    }
}

@Composable
private fun ExistingMemoryButton(
    index: Int,
    note: ShortTermNote,
    onNotePress: (note: ShortTermNote) -> Unit = { }
) {
    val fontSize = (WorkingMemoryScreen.MAX_FONT_SIZE - (index * 2)).coerceAtLeast(10)
    Spacer(modifier = Modifier.width(4.dp))

    WorkingMemoryButton(note.name, fontSize) {
        onNotePress(note)
    }
}

@Composable
private fun WorkingMemoryButton(
    label: String,
    fontSize: Int,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(4.dp),
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = fontSize.sp, textAlign = TextAlign.Center)
        )
    }
}

