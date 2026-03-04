package com.md.workers

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.md.AudioPlayer
import com.md.audio.AudioDecoder
import com.md.provider.AbstractNote
import com.md.provider.Note
import com.md.stt.SttEngine
import com.md.stt.VoskSttEngine
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

class TranscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "TranscriptionWorker"
        const val KEY_USE_SMALL_MODEL = "use_small_model"
        const val MAX_FAIL_COUNT = 3
        const val MIN_TRANSCRIPTION_TIMEOUT_MS = 90_000L // minimum 90 seconds
        const val TIMEOUT_MULTIPLIER = 3L // allow up to 3x realtime for large models
    }

    private val audioDecoder = AudioDecoder()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val useSmallModel = inputData.getBoolean(KEY_USE_SMALL_MODEL, false)
        Log.d(TAG, "Starting transcription batch (useSmallModel=$useSmallModel)")
        
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground info", e)
        }
        
        setProgress(androidx.work.workDataOf(
            "statusMessage" to "Initializing Speech-to-Text Engine..."
        ))

        val sttEngine = VoskSttEngine(applicationContext)
        val initialized = sttEngine.initialize(useSmallModel)
        if (!initialized) {
            Log.e(TAG, "Failed to initialize STT Engine (useSmallModel=$useSmallModel)")
            return@withContext Result.failure()
        }
        
        val modelName = sttEngine.modelName
        Log.i(TAG, "STT Engine initialized with model: $modelName")

        try {
            val result = processNotes(sttEngine, modelName, useSmallModel) { isStopped }
            sttEngine.release()
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Worker exception", e)
            e.printStackTrace()
            sttEngine.release()
            return@withContext Result.retry()
        }
    }

    private suspend fun processNotes(
        sttEngine: VoskSttEngine,
        modelName: String,
        useSmallModel: Boolean,
        isStoppedCheck: () -> Boolean
    ): Result {
        // Build selection:
        // - Notes that have audio but no transcript
        // - Haven't been attempted yet OR were attempted but within retry threshold
        // - Fail count below max for the current model type
        val maxFails = if (useSmallModel) MAX_FAIL_COUNT else MAX_FAIL_COUNT
        val selection = """
            (
                (${Note.QUESTION} IS NOT NULL AND ${Note.QUESTION} != '' AND ${AbstractNote.QUESTION_TRANSCRIPT} IS NULL AND ${AbstractNote.QUESTION_TRANSCRIPT_FAIL_COUNT} < $maxFails)
                OR
                (${Note.ANSWER} IS NOT NULL AND ${Note.ANSWER} != '' AND ${AbstractNote.ANSWER_TRANSCRIPT} IS NULL AND ${AbstractNote.ANSWER_TRANSCRIPT_FAIL_COUNT} < $maxFails)
            )
        """.trimIndent()

        val cursor = applicationContext.contentResolver.query(
            AbstractNote.CONTENT_URI,
            null,
            selection,
            null,
            "${Note._ID} DESC" // Process newest first
        )

        if (cursor == null) {
            return Result.success()
        }

        val totalCount = cursor.count
        var processedCount = 0
        var skippedCount = 0
        var failedCount = 0
        val maxBatchSize = 1000

        Log.i(TAG, "Found $totalCount notes needing transcription (model=$modelName)")

        setProgress(androidx.work.workDataOf(
            "statusMessage" to "Found $totalCount notes needing transcription (model=$modelName).",
            "totalCount" to totalCount,
            "processedCount" to 0,
            "skippedCount" to 0
        ))

        var sessionConfSum = 0f
        var sessionConfCount = 0

        while (coroutineContext.isActive && !isStoppedCheck() && cursor.moveToNext() && processedCount < maxBatchSize) {
            yield()
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(Note._ID))
            val question = cursor.getString(cursor.getColumnIndexOrThrow(Note.QUESTION))
            val answer = cursor.getString(cursor.getColumnIndexOrThrow(Note.ANSWER))
            var questionTranscript = cursor.getString(cursor.getColumnIndexOrThrow(Note.QUESTION_TRANSCRIPT))
            var answerTranscript = cursor.getString(cursor.getColumnIndexOrThrow(Note.ANSWER_TRANSCRIPT))
            val questionFailCount = cursor.getInt(cursor.getColumnIndexOrThrow(Note.QUESTION_TRANSCRIPT_FAIL_COUNT))
            val answerFailCount = cursor.getInt(cursor.getColumnIndexOrThrow(Note.ANSWER_TRANSCRIPT_FAIL_COUNT))

            var questionTranscriptConf = 0f
            var answerTranscriptConf = 0f
            var updated = false
            val now = System.currentTimeMillis()

            // Process Question
            var questionAttemptedTime = 0L
            var questionFailed = false
            if (!question.isNullOrBlank() && questionTranscript.isNullOrBlank() && questionFailCount < MAX_FAIL_COUNT) {
                val path = AudioPlayer.sanitizePath(question)
                Log.d(TAG, "Note ID $id: Attempting to decode Question audio from '$path' (failCount=$questionFailCount)")
                val decodeStartMs = System.currentTimeMillis()
                val pcm = audioDecoder.decodeToPcm(path)
                val decodeElapsedMs = System.currentTimeMillis() - decodeStartMs

                if (pcm != null) {
                    Log.d(TAG, "Note ID $id: Question decoded in ${decodeElapsedMs}ms (${pcm.size} samples, ~${pcm.size / 16000}s). Transcribing with $modelName...")
                    
                    val audioDurationMs = (pcm.size.toLong() * 1000L) / 16000L
                    val timeoutMs = maxOf(MIN_TRANSCRIPTION_TIMEOUT_MS, audioDurationMs * TIMEOUT_MULTIPLIER)
                    val transcriptResult = withTimeoutOrNull(timeoutMs) {
                        sttEngine.transcribe(pcm)
                    }
                    
                    if (transcriptResult == null) {
                        // Real timeout — Vosk hung on this audio
                        Log.w(TAG, "Note ID $id: Question transcription TIMED OUT after ${timeoutMs}ms (samples=${pcm.size}, audioDuration=${audioDurationMs}ms)")
                        questionFailed = true
                        failedCount++
                    } else if (transcriptResult.text.isBlank()) {
                        // Model completed but detected no speech — don't count as failure
                        Log.i(TAG, "Note ID $id: Question — no speech recognized by $modelName (${pcm.size} samples). Skipping, not counting as failure.")
                        skippedCount++
                    } else {
                        Log.d(TAG, "Note ID $id: Question transcribed -> '${transcriptResult.text}' (conf=${String.format("%.2f", transcriptResult.confidence)})")
                        questionTranscript = transcriptResult.text
                        questionTranscriptConf = transcriptResult.confidence
                        updated = true
                    }
                } else {
                    Log.w(TAG, "Note ID $id: Question audio decoding failed for '$path' (${decodeElapsedMs}ms).")
                    questionFailed = true
                    failedCount++
                }
                questionAttemptedTime = now
            }

            // Process Answer
            var answerAttemptedTime = 0L
            var answerFailed = false
            if (!answer.isNullOrBlank() && answerTranscript.isNullOrBlank() && answerFailCount < MAX_FAIL_COUNT) {
                val path = AudioPlayer.sanitizePath(answer)
                Log.d(TAG, "Note ID $id: Attempting to decode Answer audio from '$path' (failCount=$answerFailCount)")
                val decodeStartMs = System.currentTimeMillis()
                val pcm = audioDecoder.decodeToPcm(path)
                val decodeElapsedMs = System.currentTimeMillis() - decodeStartMs

                if (pcm != null) {
                    Log.d(TAG, "Note ID $id: Answer decoded in ${decodeElapsedMs}ms (${pcm.size} samples, ~${pcm.size / 16000}s). Transcribing with $modelName...")
                    
                    val audioDurationMs = (pcm.size.toLong() * 1000L) / 16000L
                    val timeoutMs = maxOf(MIN_TRANSCRIPTION_TIMEOUT_MS, audioDurationMs * TIMEOUT_MULTIPLIER)
                    val transcriptResult = withTimeoutOrNull(timeoutMs) {
                        sttEngine.transcribe(pcm)
                    }
                    
                    if (transcriptResult == null) {
                        // Real timeout — Vosk hung on this audio
                        Log.w(TAG, "Note ID $id: Answer transcription TIMED OUT after ${timeoutMs}ms (samples=${pcm.size}, audioDuration=${audioDurationMs}ms)")
                        answerFailed = true
                        failedCount++
                    } else if (transcriptResult.text.isBlank()) {
                        // Model completed but detected no speech — don't count as failure
                        Log.i(TAG, "Note ID $id: Answer — no speech recognized by $modelName (${pcm.size} samples). Skipping, not counting as failure.")
                        skippedCount++
                    } else {
                        Log.d(TAG, "Note ID $id: Answer transcribed -> '${transcriptResult.text}' (conf=${String.format("%.2f", transcriptResult.confidence)})")
                        answerTranscript = transcriptResult.text
                        answerTranscriptConf = transcriptResult.confidence
                        updated = true
                    }
                } else {
                    Log.w(TAG, "Note ID $id: Answer audio decoding failed for '$path' (${decodeElapsedMs}ms).")
                    answerFailed = true
                    failedCount++
                }
                answerAttemptedTime = now
            }

            // Build update values
            if (updated || questionAttemptedTime > 0L || answerAttemptedTime > 0L || questionFailed || answerFailed) {
                val values = ContentValues().apply {
                    if (updated) {
                        put(AbstractNote.QUESTION_TRANSCRIPT, questionTranscript)
                        put(AbstractNote.ANSWER_TRANSCRIPT, answerTranscript)
                        if (questionTranscriptConf > 0) {
                            put(AbstractNote.QUESTION_TRANSCRIPT_CONFIDENCE, questionTranscriptConf)
                            put(AbstractNote.QUESTION_TRANSCRIPT_GENERATED_AT, now)
                            put(AbstractNote.QUESTION_TRANSCRIPT_MODEL, modelName)
                        }
                        if (answerTranscriptConf > 0) {
                            put(AbstractNote.ANSWER_TRANSCRIPT_CONFIDENCE, answerTranscriptConf)
                            put(AbstractNote.ANSWER_TRANSCRIPT_GENERATED_AT, now)
                            put(AbstractNote.ANSWER_TRANSCRIPT_MODEL, modelName)
                        }
                    }
                    if (questionAttemptedTime > 0L) {
                        put(AbstractNote.QUESTION_TRANSCRIPT_ATTEMPTED, questionAttemptedTime)
                    }
                    if (answerAttemptedTime > 0L) {
                        put(AbstractNote.ANSWER_TRANSCRIPT_ATTEMPTED, answerAttemptedTime)
                    }
                    if (questionFailed) {
                        put(AbstractNote.QUESTION_TRANSCRIPT_FAIL_COUNT, questionFailCount + 1)
                        Log.w(TAG, "Note ID $id: Question fail count incremented to ${questionFailCount + 1}")
                    }
                    if (answerFailed) {
                        put(AbstractNote.ANSWER_TRANSCRIPT_FAIL_COUNT, answerFailCount + 1)
                        Log.w(TAG, "Note ID $id: Answer fail count incremented to ${answerFailCount + 1}")
                    }
                }
                applicationContext.contentResolver.update(
                    AbstractNote.CONTENT_URI,
                    values,
                    "${Note._ID} = ?",
                    arrayOf(id.toString())
                )
                processedCount++

                if (questionTranscriptConf > 0) { sessionConfSum += questionTranscriptConf; sessionConfCount++ }
                if (answerTranscriptConf > 0) { sessionConfSum += answerTranscriptConf; sessionConfCount++ }

                val avg = if (sessionConfCount > 0) sessionConfSum / sessionConfCount else 0f
                Log.i(TAG, "Progress: Processed $processedCount/$totalCount notes ($skippedCount skipped, $failedCount failed). Avg Confidence: ${"%.2f".format(avg * 100)}%")

                setProgress(androidx.work.workDataOf(
                    "statusMessage" to "Transcribing ($modelName)... ($processedCount / $totalCount)\nSkipped: $skippedCount, Failed: $failedCount",
                    "totalCount" to totalCount,
                    "processedCount" to processedCount,
                    "skippedCount" to skippedCount,
                    "failedCount" to failedCount,
                    "averageConfidence" to avg
                ))
            }
        }

        cursor.close()

        if (isStoppedCheck() || !coroutineContext.isActive) {
            Log.d(TAG, "Transcription batch cancelled. Processed: $processedCount, Skipped: $skippedCount, Failed: $failedCount")
            return Result.success()
        }

        setProgress(androidx.work.workDataOf(
            "statusMessage" to "Finished ($modelName). Processed: $processedCount, Skipped: $skippedCount, Failed: $failedCount.",
            "totalCount" to totalCount,
            "processedCount" to processedCount,
            "skippedCount" to skippedCount,
            "failedCount" to failedCount
        ))

        Log.d(TAG, "Finished transcription batch ($modelName). Processed: $processedCount, Skipped: $skippedCount, Failed: $failedCount")
        return Result.success()
    }

    private fun createForegroundInfo(): androidx.work.ForegroundInfo {
        val title = "Transcribing Audio"
        val cancel = "Cancel"
        
        val intent = androidx.work.WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "transcription_channel",
                "Background Transcription",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "transcription_channel")
            .setContentTitle(title)
            .setTicker(title)
            .setContentText("Processing audio notes...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(1992, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            androidx.work.ForegroundInfo(1992, notification)
        }
    }
}
