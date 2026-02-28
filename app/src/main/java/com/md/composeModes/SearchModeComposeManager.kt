package com.md.composeModes

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.md.provider.AbstractNote
import com.md.provider.Note
import com.md.viewmodel.TopModeFlowProvider
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear

@ActivityScoped
class SearchModeComposeManager @Inject constructor(
    @ActivityContext val context: Context,
    private val topModeFlowProvider: TopModeFlowProvider
) {

    @Composable
    fun compose() {
        var query by remember { mutableStateOf("") }
        var searchedQuery by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<Note>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }

        LaunchedEffect(query) {
            if (query.isBlank()) {
                results = emptyList()
                searchedQuery = ""
                isSearching = false
                return@LaunchedEffect
            }
            isSearching = true
            // Debounce to prevent searching on every single keystroke immediately
            delay(500)
            
            val currentQuery = query
            results = performSearch(currentQuery)
            searchedQuery = currentQuery
            isSearching = false
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Transcripts") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (!isSearching && searchedQuery.isNotEmpty()) {
                Text(
                    text = if (results.isEmpty()) "No results found for \"$searchedQuery\"" else "Found ${results.size} result(s) for \"$searchedQuery\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results) { note ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        // Future: Navigate to Note Editor or Practice Mode
                    }) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Note ID: ${note.id}", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(text = "Q: ${note.questionTranscript ?: "(no transcript)"}", style = MaterialTheme.typography.bodyMedium)
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(text = "A: ${note.answerTranscript ?: "(no transcript)"}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    private suspend fun performSearch(query: String): List<Note> = withContext(Dispatchers.IO) {
        val notes = mutableListOf<Note>()
        val selection = "${AbstractNote.QUESTION_TRANSCRIPT} LIKE ? OR ${AbstractNote.ANSWER_TRANSCRIPT} LIKE ?"
        val args = arrayOf("%$query%", "%$query%")
        val cursor = context.contentResolver.query(
            AbstractNote.CONTENT_URI,
            null,
            selection,
            args,
            "${Note._ID} DESC LIMIT 50"
        )
        
        cursor?.use {
            while (it.moveToNext()) {
                val qTransIdx = it.getColumnIndex(Note.QUESTION_TRANSCRIPT)
                val aTransIdx = it.getColumnIndex(Note.ANSWER_TRANSCRIPT)
                val idIdx = it.getColumnIndex(Note._ID)
                val catIdx = it.getColumnIndex(Note.CATEGORY)
                
                val qT = if (qTransIdx != -1) it.getString(qTransIdx) else null
                val aT = if (aTransIdx != -1) it.getString(aTransIdx) else null
                val id = if (idIdx != -1) it.getInt(idIdx) else 0
                val cat = if (catIdx != -1) it.getString(catIdx) else "0"
                
                val note = Note(null, null, cat.toIntOrNull() ?: 0)
                note.id = id
                note.questionTranscript = qT
                note.answerTranscript = aT
                notes.add(note)
            }
        }
        return@withContext notes
    }
}
