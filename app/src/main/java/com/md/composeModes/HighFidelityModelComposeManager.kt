package com.md.composeModes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.md.stt.DownloadState
import com.md.stt.ModelDownloadManager
import com.md.viewmodel.TopModeFlowProvider
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class HighFidelityModelComposeManager @Inject constructor(
    private val topModeFlowProvider: TopModeFlowProvider,
    private val modelDownloadManager: ModelDownloadManager
) {
    @Composable
    fun compose() {
        val downloadState = modelDownloadManager.downloadState.collectAsState().value

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "High-Fidelity STT Model",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vosk Acoustic Model",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The bundled model is optimized for space efficiency but may produce lower quality transcriptions. You can optionally download a massive 2.3 GB 'gigaspeech' model to achieve significantly better and more accurate transcriptions offline. This model will stay securely on your device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            when (downloadState) {
                is DownloadState.NotDownloaded -> {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = "Download Model",
                        modifier = Modifier.padding(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Model is not downloaded.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { modelDownloadManager.startDownload() }) {
                        Text("Start 2.3 GB Download")
                    }
                }
                is DownloadState.Downloading -> {
                    val progressPercent = (downloadState.progress * 100).toInt()
                    Text(
                        text = "Downloading Model ($progressPercent%)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = downloadState.progress,
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "The download will continue in the background using Android's native download manager.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
                is DownloadState.Extracting -> {
                    Text(
                        text = "Extracting Archive...",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This process requires unzipping over 2.3 GB of model chunks. This might take several minutes depending on your device's IO speed. Please be patient.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                is DownloadState.Ready -> {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Ready",
                        modifier = Modifier.padding(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "High-Fidelity Model is installed and active!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "MemoryPrime will now exclusively use this model for all offline transcriptions to ensure the highest accuracy possible.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                is DownloadState.Error -> {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Error",
                        modifier = Modifier.padding(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Error Handled",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { modelDownloadManager.startDownload() }) {
                        Text("Retry Download")
                    }
                }
            }
        }
    }
}
