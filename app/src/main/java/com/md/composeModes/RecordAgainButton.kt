package com.md.composeModes

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier


@Composable
fun UnlockRecordButton(
    modifier: Modifier,
    modeDescription: String,
    onModeChange: () -> Unit,
) {
        NTapButton(
            modifier = modifier,
            onNTap = onModeChange
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Record mode",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = modeDescription,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
}