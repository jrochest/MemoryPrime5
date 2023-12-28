package com.md.workingMemory

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.md.AudioRecorder
import com.md.SpacedRepeaterActivity
import com.md.provider.Note
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject


@ActivityScoped
class RecordButtonController @Inject constructor(
    val activity: SpacedRepeaterActivity,
) {
    private var audioRecorder: AudioRecorder? = null

    fun onTripleTap() {
        val recorder = audioRecorder
        if (recorder != null) {
            recorder.stop()
            if (recorder.isRecorded) {
                val file = recorder.originalFile
            }

        } else {
            val recorder = AudioRecorder()
            this.audioRecorder = recorder
            recorder.start()
        }

    }
}

@ActivityScoped
class CurrentNotePartManager @Inject constructor(
    val activity: SpacedRepeaterActivity,
) {
    private var currentNote: Note? = null

    fun updateAudioFilename() {

    }
}

@Composable
fun RecordAgainButton(modifier: Modifier, onTripleTap: () -> Unit) {
    TripleTapButton(
        modifier = modifier,
        onTripleTap = onTripleTap
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Record again",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Triple Tap and then tap hold to record",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}