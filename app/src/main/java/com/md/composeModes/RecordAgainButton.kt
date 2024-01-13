package com.md.composeModes

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.md.AudioRecorder
import com.md.SpacedRepeaterActivity
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject


@ActivityScoped
class RecordButtonController @Inject constructor(
    @ActivityContext val context: Context,
    private val currentNotePartManager: CurrentNotePartManager
) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }
    private var audioRecorder: AudioRecorder? = null

    fun onTripleTap() {
        val recorder = audioRecorder
        if (recorder != null) {
            recorder.stop()
            if (recorder.isRecorded) {
                val filename = recorder.originalFile
                currentNotePartManager.updateAudioFilename(filename)
            }

        } else {
            val recorder = AudioRecorder()
            this.audioRecorder = recorder
            recorder.start()
        }

    }
}

@Composable
fun UnlockRecordButton(
    modifier: Modifier,
    unlock: () -> Unit,
) {
        NTapButton(
            modifier = modifier,
            onNTap = unlock

        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Record",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "(Locked) - Triple Tap and then tap hold to record",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
}