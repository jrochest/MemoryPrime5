package com.md.composeModes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class ManualTranscriptionComposeManager @Inject constructor() {

    @Composable
    fun compose() {
        val context = LocalContext.current
        val workManager = remember { WorkManager.getInstance(context) }
        
        val workInfos by workManager.getWorkInfosForUniqueWorkLiveData("ManualTranscription")
            .observeAsState(initial = emptyList())

        val workInfo = workInfos.firstOrNull()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = "Manual Transcription",
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Manual Transcription",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (workInfo == null || workInfo.state == WorkInfo.State.SUCCEEDED || workInfo.state == WorkInfo.State.FAILED || workInfo.state == WorkInfo.State.CANCELLED) {
                Text(
                    text = "No active transcription session.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (workInfo != null) {
                    val progress = workInfo.progress
                    val statusMessage = progress.getString("statusMessage")
                    if (statusMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Last Session Result: ${workInfo.state.name}", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                
                Button(onClick = {
                    val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.md.workers.TranscriptionWorker>().build()
                    workManager.enqueueUniqueWork(
                        "ManualTranscription", 
                        androidx.work.ExistingWorkPolicy.REPLACE, 
                        workRequest
                    )
                }) {
                    Text("Start Transcription")
                }
            } else {
                val progress = workInfo.progress
                val statusMessage = progress.getString("statusMessage") ?: "Starting..."
                val totalCount = progress.getInt("totalCount", 0)
                val processedCount = progress.getInt("processedCount", 0)
                val averageConfidence = progress.getFloat("averageConfidence", 0f)
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Status: ${workInfo.state.name}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = statusMessage)
                        
                        if (averageConfidence > 0f) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Current Confidence: ${"%.1f".format(averageConfidence * 100)}%")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        if (totalCount > 0) {
                            LinearProgressIndicator(
                                progress = processedCount.toFloat() / totalCount.toFloat(),
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { workManager.cancelUniqueWork("ManualTranscription") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop Transcription")
                }
            }
        }
    }
}
