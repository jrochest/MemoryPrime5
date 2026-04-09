package com.md.receivers

import android.content.BroadcastReceiver
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log
import com.md.provider.AbstractNote
import com.md.provider.Note
import android.widget.Toast
import org.json.JSONArray
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranscriptImportReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "TranscriptImport"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.md.IMPORT_TRANSCRIPTS") return
        
        val filePath = intent.getStringExtra("filePath")
        if (filePath.isNullOrEmpty()) {
            Log.e(TAG, "No filePath provided in intent")
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: $filePath")
            return
        }

        Log.i(TAG, "Importing transcripts from $filePath in background...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonString = file.readText()
                val array = JSONArray(jsonString)
                var updatedCount = 0

                val operations = java.util.ArrayList<ContentProviderOperation>()
                val authority = AbstractNote.CONTENT_URI.authority ?: "com.md.provider"

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getInt("id")
                    val values = ContentValues()

                    if (obj.has("questionTranscript")) {
                        values.put(AbstractNote.QUESTION_TRANSCRIPT, obj.getString("questionTranscript"))
                        values.put(AbstractNote.QUESTION_TRANSCRIPT_CONFIDENCE, obj.getDouble("questionTranscriptConfidence").toFloat())
                        values.put(AbstractNote.QUESTION_TRANSCRIPT_MODEL, obj.getString("questionTranscriptModel"))
                        values.put(AbstractNote.QUESTION_TRANSCRIPT_GENERATED_AT, obj.getLong("questionTranscriptGeneratedAt"))
                    }

                    if (obj.has("answerTranscript")) {
                        values.put(AbstractNote.ANSWER_TRANSCRIPT, obj.getString("answerTranscript"))
                        values.put(AbstractNote.ANSWER_TRANSCRIPT_CONFIDENCE, obj.getDouble("answerTranscriptConfidence").toFloat())
                        values.put(AbstractNote.ANSWER_TRANSCRIPT_MODEL, obj.getString("answerTranscriptModel"))
                        values.put(AbstractNote.ANSWER_TRANSCRIPT_GENERATED_AT, obj.getLong("answerTranscriptGeneratedAt"))
                    }

                    if (values.size() > 0) {
                        operations.add(
                            ContentProviderOperation.newUpdate(AbstractNote.CONTENT_URI)
                                .withSelection("${Note._ID} = ?", arrayOf(id.toString()))
                                .withValues(values)
                                .build()
                        )
                        updatedCount++
                        
                        if (operations.size >= 500) {
                            context.contentResolver.applyBatch(authority, operations)
                            operations.clear()
                        }
                    }
                }
                
                if (operations.isNotEmpty()) {
                    context.contentResolver.applyBatch(authority, operations)
                }
                Log.i(TAG, "Successfully updated $updatedCount notes with new transcripts.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Successfully imported $updatedCount transcribed notes!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing or importing transcripts", e)
            }
        }
    }
}
