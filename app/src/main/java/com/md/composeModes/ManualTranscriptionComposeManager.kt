package com.md.composeModes

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.work.workDataOf
import com.md.workers.TranscriptionWorker
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
        
        val isLargeModelReady = remember {
            context.getSharedPreferences("VoskModelPrefs", Context.MODE_PRIVATE)
                .getBoolean("high_fidelity_model_ready", false)
        }

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

                // Small Model Button — always available
                OutlinedButton(
                    onClick = { startTranscription(workManager, useSmallModel = true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start with Small Model")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Large Model Button — only enabled if downloaded
                Button(
                    onClick = { startTranscription(workManager, useSmallModel = false) },
                    enabled = isLargeModelReady,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isLargeModelReady) "Start with Large Model" else "Start with Large Model (Not Downloaded)")
                }
                
                if (!isLargeModelReady) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Download the high-fidelity model from Settings for better accuracy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val progress = workInfo.progress
                val statusMessage = progress.getString("statusMessage") ?: "Starting..."
                val totalCount = progress.getInt("totalCount", 0)
                val processedCount = progress.getInt("processedCount", 0)
                val averageConfidence = progress.getFloat("averageConfidence", 0f)
                val failedCount = progress.getInt("failedCount", 0)
                
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

                        if (failedCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Failed: $failedCount",
                                color = MaterialTheme.colorScheme.error
                            )
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

    private fun startTranscription(workManager: WorkManager, useSmallModel: Boolean) {
        val inputData = workDataOf(
            TranscriptionWorker.KEY_USE_SMALL_MODEL to useSmallModel
        )
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(inputData)
            .build()
        workManager.enqueueUniqueWork(
            "ManualTranscription",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
