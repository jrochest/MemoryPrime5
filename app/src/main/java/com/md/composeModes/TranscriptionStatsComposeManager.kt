package com.md.composeModes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.md.DbNoteEditor
import com.md.TranscriptionStats
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ActivityScoped
class TranscriptionStatsComposeManager @Inject constructor() {

    @Composable
    fun compose() {
        var stats by remember { mutableStateOf<TranscriptionStats?>(null) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                stats = DbNoteEditor.instance?.getTranscriptionStats()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.Assessment,
                contentDescription = "Stats",
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Transcription Statistics",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            val currentStats = stats
            if (currentStats == null) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Crunching database statistics...", style = MaterialTheme.typography.bodyMedium)
            } else {
                val totalAudioItems = currentStats.totalAudioQuestions + currentStats.totalAudioAnswers
                val totalTranscribedItems = currentStats.transcribedQuestions + currentStats.transcribedAnswers
                val percentComplete = if (totalAudioItems > 0) {
                    (totalTranscribedItems.toFloat() / totalAudioItems.toFloat()) * 100f
                } else {
                    100f
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Overall Progress",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total Notes: ${currentStats.totalNotes}")
                        Text("Audio Items Found: $totalAudioItems")
                        Text("Items Transcribed: $totalTranscribedItems")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format("%.1f%% Complete", percentComplete),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Model Confidence",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val qConfStr = if (currentStats.averageQuestionConfidence > 0) String.format("%.1f%%", currentStats.averageQuestionConfidence * 100) else "N/A"
                        val aConfStr = if (currentStats.averageAnswerConfidence > 0) String.format("%.1f%%", currentStats.averageAnswerConfidence * 100) else "N/A"
                        
                        Text("Question Average Confidence: $qConfStr")
                        Text("Answer Average Confidence: $aConfStr")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Higher confidence represents higher extraction accuracy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
