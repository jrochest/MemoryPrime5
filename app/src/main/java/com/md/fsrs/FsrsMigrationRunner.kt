package com.md.fsrs

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.md.DbContants
import com.md.provider.AbstractNote
import com.md.provider.AbstractRep
import com.md.provider.Note

/**
 * One-time batch migration runner that converts all SM-2 notes to FSRS state.
 *
 * Features:
 * - Pre-migration validation (schema presence, note counts)
 * - Post-migration validation (value range checks per note)
 * - Detailed MigrationReport with per-scenario counts
 * - Full rollback via [rollbackAll]
 * - Results persisted to SharedPreferences for later inspection
 */
object FsrsMigrationRunner {

    private const val TAG = "FSRS"
    private const val PREFS_NAME = "fsrs_migration"
    private const val KEY_MIGRATION_COMPLETED = "migration_completed"
    private const val KEY_MIGRATION_TIMESTAMP = "migration_timestamp_ms"
    private const val KEY_REPORT_JSON = "migration_report"

    /**
     * Migration result for a single note.
     */
    data class NoteResult(
        val noteId: Int,
        val scenario: String,        // "A", "B", "C"
        val stability: Double,
        val difficulty: Double,
        val state: Int,
        val valid: Boolean,
        val errorMessage: String? = null,
    )

    /**
     * Summary of the entire migration batch.
     */
    data class MigrationReport(
        val totalNotesFound: Int = 0,
        val totalMigrated: Int = 0,
        val totalFailed: Int = 0,
        val totalSkippedAlreadyMigrated: Int = 0,
        val scenarioACounts: Int = 0,  // Full history replay
        val scenarioBCounts: Int = 0,  // Partial history
        val scenarioCCounts: Int = 0,  // No history (heuristic)
        val validationErrors: MutableList<String> = mutableListOf(),
        val failedNoteIds: MutableList<Int> = mutableListOf(),
        val durationMs: Long = 0,
    ) {
        fun toLogString(): String = buildString {
            appendLine("═══ FSRS Migration Report ═══")
            appendLine("Total notes found:      $totalNotesFound")
            appendLine("Successfully migrated:  $totalMigrated")
            appendLine("Failed:                 $totalFailed")
            appendLine("Already migrated:       $totalSkippedAlreadyMigrated")
            appendLine("───────────────────────────")
            appendLine("Scenario A (full replay):  $scenarioACounts")
            appendLine("Scenario B (partial):      $scenarioBCounts")
            appendLine("Scenario C (heuristic):    $scenarioCCounts")
            appendLine("───────────────────────────")
            appendLine("Duration:               ${durationMs}ms")
            if (validationErrors.isNotEmpty()) {
                appendLine("───────────────────────────")
                appendLine("Validation warnings (${validationErrors.size}):")
                validationErrors.take(20).forEach { appendLine("  ⚠ $it") }
                if (validationErrors.size > 20) {
                    appendLine("  ... and ${validationErrors.size - 20} more")
                }
            }
            if (failedNoteIds.isNotEmpty()) {
                appendLine("Failed note IDs: ${failedNoteIds.take(20)}")
            }
            appendLine("═════════════════════════════")
        }

        fun toPrefsJson(): String = buildString {
            append("{")
            append("\"totalNotesFound\":$totalNotesFound,")
            append("\"totalMigrated\":$totalMigrated,")
            append("\"totalFailed\":$totalFailed,")
            append("\"scenarioA\":$scenarioACounts,")
            append("\"scenarioB\":$scenarioBCounts,")
            append("\"scenarioC\":$scenarioCCounts,")
            append("\"validationErrors\":${validationErrors.size},")
            append("\"durationMs\":$durationMs")
            append("}")
        }
    }

    // ─── Public API ─────────────────────────────────────────────────────

    /**
     * Check if migration has already completed on this device.
     */
    fun isMigrationCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MIGRATION_COMPLETED, false)
    }

    /**
     * Migrate all unmigrated notes to FSRS state.
     * Skips if migration was already completed.
     *
     * @param context Android context.
     * @return MigrationReport with full details.
     */
    fun migrateAll(context: Context): MigrationReport {
        if (isMigrationCompleted(context)) {
            Log.i(TAG, "FSRS migration already completed, skipping.")
            return MigrationReport()
        }

        val startTime = System.currentTimeMillis()
        val report = MigrationReport()
        var db: SQLiteDatabase? = null

        try {
            db = SQLiteDatabase.openDatabase(
                DbContants.getDatabasePath(),
                null,
                SQLiteDatabase.OPEN_READWRITE
            )

            // ── Pre-migration validation ──
            if (!validateSchema(db, report)) {
                Log.e(TAG, "Schema validation failed, aborting migration.")
                return report
            }

            // Count total notes
            val totalCursor = db.rawQuery("SELECT COUNT(*) FROM notes", null)
            val totalNotes = if (totalCursor.moveToFirst()) totalCursor.getInt(0) else 0
            totalCursor.close()

            // Count unmigrated notes
            val unmigratedCursor = db.rawQuery(
                "SELECT COUNT(*) FROM notes WHERE ${AbstractNote.FSRS_STABILITY} < 0", null
            )
            val unmigratedCount = if (unmigratedCursor.moveToFirst()) unmigratedCursor.getInt(0) else 0
            unmigratedCursor.close()

            (report as MigrationReport).let {
                // We'll build the report inline since data class is immutable on fields we
                // need to update. Using a mutable approach instead.
            }

            var totalFound = unmigratedCount
            var totalMigrated = 0
            var totalFailed = 0
            var scenarioA = 0
            var scenarioB = 0
            var scenarioC = 0
            val validationErrors = mutableListOf<String>()
            val failedIds = mutableListOf<Int>()

            Log.i(TAG, "Starting FSRS migration: $unmigratedCount of $totalNotes notes to migrate")

            // ── Begin transaction for atomicity ──
            db.beginTransaction()
            try {
                // Select only the columns we need (not SELECT *) and batch
                // to avoid CursorWindow overflow on large collections.
                val BATCH_SIZE = 500
                val columns = "${Note._ID}, ${Note.EASINESS}, ${Note.GRADE}, " +
                        "${Note.LAST_REP}, ${Note.NEXT_REP}, ${Note.ACQ_REPS}, " +
                        "${Note.RET_REPS}, ${Note.LAPSES}, ${Note.UNSEEN}"
                var offset = 0
                var hasMore = true

                while (hasMore) {
                    val notesCursor = db.rawQuery(
                        "SELECT $columns FROM notes WHERE ${AbstractNote.FSRS_STABILITY} < 0 " +
                                "LIMIT $BATCH_SIZE OFFSET $offset", null
                    )

                    if (notesCursor.count == 0) {
                        notesCursor.close()
                        hasMore = false
                        break
                    }

                    while (notesCursor.moveToNext()) {
                        val noteId = notesCursor.getInt(notesCursor.getColumnIndexOrThrow(Note._ID))
                        try {
                            val result = migrateOneNote(db, notesCursor, noteId)

                            // ── Post-migration validation per note ──
                            val errors = validateResult(result)
                            if (errors.isNotEmpty()) {
                                validationErrors.addAll(errors)
                            }

                            if (result.valid) {
                                db.execSQL(
                                    "UPDATE notes SET " +
                                            "${AbstractNote.FSRS_STABILITY} = ${result.stability}, " +
                                            "${AbstractNote.FSRS_DIFFICULTY} = ${result.difficulty}, " +
                                            "${AbstractNote.FSRS_STATE} = ${result.state} " +
                                            "WHERE ${Note._ID} = $noteId"
                                )
                                totalMigrated++
                                when (result.scenario) {
                                    "A" -> scenarioA++
                                    "B" -> scenarioB++
                                    "C" -> scenarioC++
                                }
                            } else {
                                totalFailed++
                                failedIds.add(noteId)
                                Log.w(TAG, "Note $noteId: invalid result, skipping: ${result.errorMessage}")
                            }
                        } catch (e: Exception) {
                            totalFailed++
                            failedIds.add(noteId)
                            Log.e(TAG, "Failed to migrate note $noteId: ${e.message}", e)
                        }

                        // Progress logging every 100 notes
                        val processed = totalMigrated + totalFailed
                        if (processed % 100 == 0 && processed > 0) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val pct = if (totalFound > 0) (processed * 100 / totalFound) else 0
                            Log.i(TAG, "FSRS progress: $processed/$totalFound ($pct%) — ${elapsed}ms elapsed")
                        }
                    }

                    val count = notesCursor.count
                    notesCursor.close()

                    // Since we UPDATE the notes (setting stability >= 0), they drop
                    // out of the WHERE clause, so we don't need to advance the offset
                    // for successfully migrated notes. Only advance for failures.
                    offset = if (totalFailed > 0) totalFailed else 0

                    if (count < BATCH_SIZE) {
                        hasMore = false
                    }
                }

                // If too many failures (>10%), abort the transaction
                if (totalFailed > 0 && totalFound > 0 &&
                    totalFailed.toDouble() / totalFound > 0.10
                ) {
                    Log.e(TAG, "Too many failures ($totalFailed/$totalFound > 10%), rolling back!")
                    validationErrors.add("ABORTED: Failure rate exceeded 10% ($totalFailed/$totalFound)")
                    // Don't set transaction successful → rollback
                } else {
                    db.setTransactionSuccessful()
                }
            } finally {
                db.endTransaction()
            }

            val durationMs = System.currentTimeMillis() - startTime

            val finalReport = MigrationReport(
                totalNotesFound = totalFound,
                totalMigrated = totalMigrated,
                totalFailed = totalFailed,
                totalSkippedAlreadyMigrated = totalNotes - unmigratedCount,
                scenarioACounts = scenarioA,
                scenarioBCounts = scenarioB,
                scenarioCCounts = scenarioC,
                validationErrors = validationErrors,
                failedNoteIds = failedIds,
                durationMs = durationMs,
            )

            // ── Persist report ──
            if (totalFailed == 0 || (totalFound > 0 && totalFailed.toDouble() / totalFound <= 0.10)) {
                getPrefs(context).edit()
                    .putBoolean(KEY_MIGRATION_COMPLETED, true)
                    .putLong(KEY_MIGRATION_TIMESTAMP, System.currentTimeMillis())
                    .putString(KEY_REPORT_JSON, finalReport.toPrefsJson())
                    .apply()
            }

            Log.i(TAG, finalReport.toLogString())
            return finalReport

        } catch (e: Exception) {
            Log.e(TAG, "Migration failed catastrophically: ${e.message}", e)
            return MigrationReport(
                validationErrors = mutableListOf("FATAL: ${e.message}"),
                durationMs = System.currentTimeMillis() - startTime,
            )
        } finally {
            db?.close()
        }
    }

    /**
     * Rollback ALL notes from FSRS back to SM-2.
     *
     * This resets the three FSRS columns to their "unmigrated" sentinel values.
     * The original SM-2 scheduling fields (easiness, grade, last_rep, next_rep)
     * are never deleted, so SM-2 scheduling resumes automatically.
     *
     * @param context Android context.
     * @return Number of notes rolled back.
     */
    fun rollbackAll(context: Context): Int {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(
                DbContants.getDatabasePath(),
                null,
                SQLiteDatabase.OPEN_READWRITE
            )

            // Count how many are currently migrated
            val countCursor = db.rawQuery(
                "SELECT COUNT(*) FROM notes WHERE ${AbstractNote.FSRS_STABILITY} >= 0", null
            )
            val count = if (countCursor.moveToFirst()) countCursor.getInt(0) else 0
            countCursor.close()

            // Reset all FSRS columns to sentinel values
            db.execSQL(
                "UPDATE notes SET " +
                        "${AbstractNote.FSRS_STABILITY} = -1, " +
                        "${AbstractNote.FSRS_DIFFICULTY} = -1, " +
                        "${AbstractNote.FSRS_STATE} = 0"
            )

            // Clear migration completion flag so it can be re-run
            getPrefs(context).edit()
                .putBoolean(KEY_MIGRATION_COMPLETED, false)
                .apply()

            Log.i(TAG, "FSRS rollback complete: $count notes reverted to SM-2")
            return count
        } catch (e: Exception) {
            Log.e(TAG, "Rollback failed: ${e.message}", e)
            return -1
        } finally {
            db?.close()
        }
    }

    /**
     * Get the last migration report stored in SharedPreferences.
     */
    fun getLastReport(context: Context): String? {
        return getPrefs(context).getString(KEY_REPORT_JSON, null)
    }

    // ─── Internal ───────────────────────────────────────────────────────

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Validate that the FSRS columns exist in the schema.
     */
    private fun validateSchema(db: SQLiteDatabase, report: MigrationReport): Boolean {
        val cursor = db.rawQuery("PRAGMA table_info(notes)", null)
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndex("name")))
        }
        cursor.close()

        val required = listOf(
            AbstractNote.FSRS_STABILITY,
            AbstractNote.FSRS_DIFFICULTY,
            AbstractNote.FSRS_STATE
        )
        val missing = required.filter { it !in columns }
        if (missing.isNotEmpty()) {
            report.validationErrors.add("Missing columns: $missing")
            return false
        }
        return true
    }

    /**
     * Migrate a single note from the cursor.
     */
    private fun migrateOneNote(
        db: SQLiteDatabase,
        notesCursor: android.database.Cursor,
        noteId: Int
    ): NoteResult {
        val easiness = notesCursor.getFloat(notesCursor.getColumnIndexOrThrow(Note.EASINESS))
        val grade = notesCursor.getInt(notesCursor.getColumnIndexOrThrow(Note.GRADE))
        val lastRep = notesCursor.getInt(notesCursor.getColumnIndexOrThrow(Note.LAST_REP))
        val nextRep = notesCursor.getInt(notesCursor.getColumnIndexOrThrow(Note.NEXT_REP))
        val acqReps = notesCursor.getInt(notesCursor.getColumnIndexOrThrow(Note.ACQ_REPS))
        val retReps = notesCursor.getInt(notesCursor.getColumnIndexOrThrow(Note.RET_REPS))
        val lapses = notesCursor.getInt(notesCursor.getColumnIndexOrThrow(Note.LAPSES))
        val unseenStr = notesCursor.getString(notesCursor.getColumnIndexOrThrow(Note.UNSEEN))
        val unseen = unseenStr == "1"

        // Fetch review history (select only needed columns)
        val repsCursor = db.rawQuery(
            "SELECT ${AbstractRep.NOTE_ID}, ${AbstractRep.INTERVAL}, ${AbstractRep.SCORE}, ${AbstractRep.TIME_STAMP_MS} " +
                    "FROM reps WHERE ${AbstractRep.NOTE_ID} = ? ORDER BY ${AbstractRep.TIME_STAMP_MS} ASC",
            arrayOf(noteId.toString())
        )
        val reps = mutableListOf<AbstractRep>()
        while (repsCursor.moveToNext()) {
            reps.add(
                AbstractRep(
                    repsCursor.getInt(repsCursor.getColumnIndexOrThrow(AbstractRep.NOTE_ID)),
                    repsCursor.getInt(repsCursor.getColumnIndexOrThrow(AbstractRep.INTERVAL)),
                    repsCursor.getInt(repsCursor.getColumnIndexOrThrow(AbstractRep.SCORE)),
                    repsCursor.getLong(repsCursor.getColumnIndexOrThrow(AbstractRep.TIME_STAMP_MS))
                )
            )
        }
        repsCursor.close()

        // Build Note object for migrator
        val note = Note("", "", 0)
        note.id = noteId
        note.easiness = easiness
        note.grade = grade
        note.last_rep = lastRep
        note.next_rep = nextRep
        note.acq_reps = acqReps
        note.ret_reps = retReps
        note.lapses = lapses
        note.isUnseen = unseen

        // Determine scenario
        val totalRepsInNote = acqReps + retReps
        val scenario = when {
            reps.isEmpty() -> "C"
            reps.size >= totalRepsInNote && totalRepsInNote > 0 -> "A"
            else -> "B"
        }

        // Migrate
        val fsrsState = FsrsMigrator.migrateNote(note, reps)

        return NoteResult(
            noteId = noteId,
            scenario = scenario,
            stability = fsrsState.stability,
            difficulty = fsrsState.difficulty,
            state = fsrsState.state.value,
            valid = true,
        )
    }

    /**
     * Validate that a migration result has sane values.
     * Returns a list of validation error messages (empty = OK).
     */
    private fun validateResult(result: NoteResult): List<String> {
        val errors = mutableListOf<String>()

        if (result.stability <= 0) {
            errors.add("Note ${result.noteId}: stability=${result.stability} (should be > 0)")
        }
        if (result.stability > 36500) { // > 100 years is suspicious
            errors.add("Note ${result.noteId}: stability=${result.stability} (suspiciously high, >100 years)")
        }
        if (result.difficulty < 1.0 || result.difficulty > 10.0) {
            errors.add("Note ${result.noteId}: difficulty=${result.difficulty} (should be 1-10)")
        }
        if (result.state !in 0..3) {
            errors.add("Note ${result.noteId}: state=${result.state} (should be 0-3)")
        }

        return errors
    }
}
