package com.md

import android.app.Activity
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.md.provider.AbstractDeck
import com.md.provider.AbstractNote
import com.md.provider.Deck
import com.md.provider.Note
import com.md.provider.Note.DEFAULT_PRIORITY
import com.md.utils.ToastSingleton
import java.util.*

data class TranscriptionStats(
    val totalNotes: Int,
    val totalAudioQuestions: Int,
    val totalAudioAnswers: Int,
    val transcribedQuestions: Int,
    val transcribedAnswers: Int,
    val averageQuestionConfidence: Float,
    val averageAnswerConfidence: Float
)

/**
 * A generic activity for editing a note in a database. This can be used either
 * to simply view a note [Intent.ACTION_VIEW], view and edit a note
 * [Intent.ACTION_EDIT], or create a new note [Intent.ACTION_INSERT]
 * .
 */
class DbNoteEditor {
    // TODO: This global out of context note ID should be refactored away eventually.
    private var currentNoteId: String? = null

    /**
     * Using a passed at the time of need activity instead of this one that is provided at
     * activity creation time causes a failure like this.
     * android.content.Context.getContentResolver()' on a null object reference
     */
    private lateinit var contextForDbAccess: Activity

    fun setContext(context: Activity?) {
        this.contextForDbAccess = checkNotNull(context)

        // TODO Do this to init the database.
        getOverdue(0)
    }

    private val listeners = Vector<NoteEditorListener>()
    fun addListener(listener: NoteEditorListener) {
        listeners.add(listener)
    }

    fun update(note: AbstractNote) {
        val values = ContentValues()

        // Bump the modification time to now.
        noteToContentValues(note, values)

        try {
            contextForDbAccess.contentResolver.update(AbstractNote.CONTENT_URI,
                    values, Note._ID + "=" + note.id, null)
        } catch (e: Exception) {
            val message = e.message
            println(message)
        }
    }

    fun insert(note: AbstractNote): AbstractNote {
        val values = ContentValues()

        // Bump the modification time to now.
        noteToContentValues(note, values)
        try {
            val uri = contextForDbAccess.contentResolver.insert(
                    AbstractNote.CONTENT_URI, values)
            val noteId = uri!!.pathSegments[1]
            note.id = noteId.toInt()
        } catch (e: Exception) {
            val message = e.message
            println(message)
            throw e
        }
        return note
    }

    private fun noteToContentValues(note: AbstractNote, values: ContentValues) {
        values.put(AbstractNote.GRADE, note.grade)
        values.put(AbstractNote.QUESTION, note.question)
        values.put(AbstractNote.ANSWER, note.answer)
        values.put(AbstractNote.QUESTION_TRANSCRIPT, note.questionTranscript)
        values.put(AbstractNote.ANSWER_TRANSCRIPT, note.answerTranscript)
        values.put(AbstractNote.QUESTION_TRANSCRIPT_CONFIDENCE, note.questionTranscriptConfidence)
        values.put(AbstractNote.ANSWER_TRANSCRIPT_CONFIDENCE, note.answerTranscriptConfidence)
        values.put(AbstractNote.CATEGORY, note.categoryAkaDeckId)
        values.put(AbstractNote.EASINESS, note.easiness)
        values.put(AbstractNote.ACQ_REPS, note.acq_reps)
        values.put(AbstractNote.RET_REPS, note.ret_reps)
        values.put(AbstractNote.ACQ_REPS_SINCE_LAPSE, note
                .acq_reps_since_lapse)
        values.put(AbstractNote.RET_REPS_SINCE_LAPSE, note
                .ret_reps_since_lapse)
        values.put(AbstractNote.LAPSES, note.lapses)
        values.put(AbstractNote.LAST_REP, note.last_rep)
        values.put(AbstractNote.NEXT_REP, note.next_rep)
        values.put(AbstractNote.UNSEEN, note.isUnseen)
        values.put(AbstractNote.MARKED, note.isMarked)
        values.put(AbstractNote.PRIORITY, note.priority)
        values.put(AbstractNote.FSRS_STABILITY, note.fsrsStability)
        values.put(AbstractNote.FSRS_DIFFICULTY, note.fsrsDifficulty)
        values.put(AbstractNote.FSRS_STATE, note.fsrsState)
    }

    fun queryDeck(): List<Deck> {
        val activateList = ArrayList<Deck>()
        val inactivateList = ArrayList<Deck>()
        val result = rawQuery("SELECT * FROM "
                + NotesProvider.DECKS_TABLE_NAME) ?: return activateList
        val query = result.cursor
        var keyword = ""
        while (query.moveToNext()) {
            keyword += "\n"
            val id = query.getInt(query.getColumnIndex("_id"))
            val name = query.getString(query
                    .getColumnIndex(AbstractDeck.NAME))
            println("name: $name")

            val deck = Deck(id, name)

            if (name.contains("inactive")) {
                inactivateList.add(deck)
            } else {
                activateList.add(deck)
            }
        }
        result.cursor.close()
        result.database.close()
        activateList.addAll(inactivateList)
        return activateList
    }

    class DatabaseResult(val cursor: Cursor, val database: SQLiteDatabase)

    fun rawQuery(queryString: String?): DatabaseResult? {
        var openDatabase: SQLiteDatabase? = null
        var query: Cursor? = null
        try {
            openDatabase = SQLiteDatabase
                    .openDatabase(DbContants.getDatabasePath(), null,
                            SQLiteDatabase.OPEN_READWRITE)
            query = openDatabase.rawQuery(queryString, null)
        } catch (e: Exception) {
            ToastSingleton.getInstance().error(e.message)
            return null
        }

        return DatabaseResult(query, openDatabase)
    }

    fun getOverdue(category: Int): Vector<Note> {

        // TODO make this look for ones that actually need to be reviewed.
        var query: Cursor? = null
        val catSingleton = CategorySingleton.getInstance()

        // If it's not a note that we just failed on.
        var selection = (Note.GRADE + " >= " + 2 // The right category name
                + " AND " + categoryCriteria(category) // Normal overdue
                + " AND ((" + catSingleton.daysSinceStart + " > " + Note.NEXT_REP + ") ")
        // Note this needs an end paren.
        selection += if (catSingleton.lookAheadDays == 0) {
            // End paren
            ")"
        } else {
            val oldNoteDays = 20
            val overDueDate = catSingleton.daysSinceStart + catSingleton.lookAheadDays
            // Over due with look ahead.
            (" OR (" + overDueDate + " > " + Note.NEXT_REP // Is mature.
                    + " AND " + Note.NEXT_REP + " - " + Note.LAST_REP + " > " + oldNoteDays + ")" // End paran
                    + ")")
        }
        try {
            query = contextForDbAccess.contentResolver.query(
                    AbstractNote.CONTENT_URI, null, selection, null,
                    AbstractNote.DEFAULT_SORT_ORDER)
        } catch (e: Exception) {
            val getMsg = e.message
            println(getMsg)
        }
        val notes = Vector<Note>()
        while (query != null && query.moveToNext()) {
            notes.add(queryGetOneNote(query))
        }
        query?.close()
        return notes
    }

    var note: Note? = null
        private set
    var isMarkedMode = false

    private fun loadDataCurrentId(): Note? {
        if (currentNoteId != null) {
            note = loadNote(currentNoteId!!.toInt())
        } else {
            // The listener need to be able to handle null notes.
            note = null
        }
        for (listener in listeners) {
            listener.onNoteUpdate(note)
        }
        return note
    }

    private fun loadNote(currentId: Int): Note? {
        val query: Cursor?
        query = contextForDbAccess.contentResolver.query(AbstractNote.CONTENT_URI,
                null, Note._ID + " = " + currentId, null,
                AbstractNote.DEFAULT_SORT_ORDER)
        if (query!!.moveToNext()) {
            note = queryGetOneNote(query)
        } else {
            note = null
        }
        if (query != null) {
            query.close()
        }
        return note
    }

    private fun queryGetOneNote(query: Cursor?): Note {
        val question = query!!.getString(query.getColumnIndex(Note.QUESTION))
        val answer = query.getString(query.getColumnIndex(Note.ANSWER))
        val category = query.getString(query.getColumnIndex(Note.CATEGORY))
        note = Note(question, answer, category.toInt())
        val id = query.getInt(query.getColumnIndex(Note._ID))
        note!!.id = id
        val grade = query.getInt(query.getColumnIndex(Note.GRADE))
        note!!.grade = grade
        
        val questionTranscriptIndex = query.getColumnIndex(Note.QUESTION_TRANSCRIPT)
        if (questionTranscriptIndex != -1) {
            note!!.questionTranscript = query.getString(questionTranscriptIndex)
        }
        val answerTranscriptIndex = query.getColumnIndex(Note.ANSWER_TRANSCRIPT)
        if (answerTranscriptIndex != -1) {
            note!!.answerTranscript = query.getString(answerTranscriptIndex)
        }
        
        val questionTranscriptConfIndex = query.getColumnIndex(Note.QUESTION_TRANSCRIPT_CONFIDENCE)
        if (questionTranscriptConfIndex != -1) {
            note!!.questionTranscriptConfidence = query.getFloat(questionTranscriptConfIndex)
        }
        val answerTranscriptConfIndex = query.getColumnIndex(Note.ANSWER_TRANSCRIPT_CONFIDENCE)
        if (answerTranscriptConfIndex != -1) {
            note!!.answerTranscriptConfidence = query.getFloat(answerTranscriptConfIndex)
        }
        
        val unseenString = query
                .getString(query.getColumnIndex(Note.UNSEEN))
        var unseen = false
        if (unseenString == "1") {
            unseen = true
        }
        note!!.isUnseen = unseen
        val markedString = query
                .getString(query.getColumnIndex(Note.MARKED))
        var marked = false
        if (markedString != null && markedString == "1") {
            marked = true
        }
        note!!.isMarked = marked
        val easiness = query.getFloat(query.getColumnIndex(Note.EASINESS))
        note!!.easiness = easiness
        val acq_reps = query.getInt(query.getColumnIndex(Note.ACQ_REPS))
        note!!.acq_reps = acq_reps
        val ret_reps = query.getInt(query.getColumnIndex(Note.RET_REPS))
        note!!.ret_reps = ret_reps
        val lapses = query.getInt(query.getColumnIndex(Note.LAPSES))
        note!!.lapses = lapses
        val acq_reps_since_lapse = query.getInt(query
                .getColumnIndex(Note.ACQ_REPS_SINCE_LAPSE))
        note!!.acq_reps_since_lapse = acq_reps_since_lapse
        val ret_reps_since_lapse = query.getInt(query
                .getColumnIndex(Note.RET_REPS_SINCE_LAPSE))
        note!!.ret_reps_since_lapse = ret_reps_since_lapse
        val next_rep = query.getInt(query.getColumnIndex(Note.NEXT_REP))
        note!!.next_rep = next_rep
        val last_rep = query.getInt(query.getColumnIndex(Note.LAST_REP))
        note!!.last_rep = last_rep
        val priorityColumnIndex = query.getColumnIndex(Note.PRIORITY)
        if (priorityColumnIndex == -1) {
            note!!.priority = DEFAULT_PRIORITY
        } else {
            note!!.priority = query.getInt(priorityColumnIndex)
        }
        val fsrsStabilityIndex = query.getColumnIndex(Note.FSRS_STABILITY)
        if (fsrsStabilityIndex != -1) {
            note!!.fsrsStability = query.getFloat(fsrsStabilityIndex)
        }
        val fsrsDifficultyIndex = query.getColumnIndex(Note.FSRS_DIFFICULTY)
        if (fsrsDifficultyIndex != -1) {
            note!!.fsrsDifficulty = query.getFloat(fsrsDifficultyIndex)
        }
        val fsrsStateIndex = query.getColumnIndex(Note.FSRS_STATE)
        if (fsrsStateIndex != -1) {
            note!!.fsrsState = query.getInt(fsrsStateIndex)
        }
        return note!!
    }

    val last: Note?
        get() = getLast(0)

    fun getLast(howFarToGo: Int): Note? {
        var returnVal: Note? = null
        if (currentNoteId == null) {
            return null
        }
        var queryString = ("SELECT MAX(" + Note._ID + ") FROM "
                + NotesProvider.NOTES_TABLE_NAME + " WHERE " + Note._ID + " < "
                + (currentNoteId!!.toInt() - howFarToGo) + " AND "
                + categoryCriteria())
        if (isMarkedMode) {
            queryString += " AND " + Note.MARKED + " = 1"
        }
        val query = rawQuery(queryString) ?: return null
        val cursory = query.cursor
        // If we found nothing just go to the max.
        if (!cursory.moveToFirst() || cursory.getInt(0) == 0) {
            queryString = ("SELECT MIN(" + Note._ID + ") FROM "
                    + NotesProvider.NOTES_TABLE_NAME + " WHERE "
                    + categoryCriteria())
            if (isMarkedMode) {
                queryString += " AND " + Note.MARKED + " = 1"
            }
            val query2 = rawQuery(queryString) ?: return null
            val cursor2 = query2.cursor
            if (cursor2.moveToFirst()) {
                if (cursor2.getInt(0) != 0) {
                    currentNoteId = "" + cursor2.getInt(0)
                    returnVal = loadDataCurrentId()
                }
            }
            cursor2.close()
            query2.database.close()
        }

        cursory.close()
        query.database.close()

        return returnVal
    }

    private fun categoryCriteria(category: Int = CategorySingleton.getInstance()
            .currentDeck): String {
        return " " + Note.CATEGORY + " = '" + category + "' "
    }

    fun deleteNote(note: Note) {
        contextForDbAccess.contentResolver.delete(AbstractNote.CONTENT_URI,
                Note._ID + " = " + note.id, null)

        if (!AudioRecorder.deleteFile(note.answer)) {
            Log.e(this.javaClass.toString(), "Couldn't answer question "
                    + note.answer)
        }
        if (!AudioRecorder.deleteFile(note.question)) {
            Log.e(this.javaClass.toString(), "Couldn't delete question "
                    + note.question)
        }
    }

    fun getAquisitionReps(category: Int): Vector<Note> {

        // TODO make this look for ones that actually need to be reviewed.
        val query: Cursor?
        val selection = (Note.GRADE + " < " + 2 + " AND "
                + categoryCriteria(category))
        query = contextForDbAccess.contentResolver.query(AbstractNote.CONTENT_URI,
                null, selection, null, AbstractNote.DEFAULT_SORT_ORDER)
        val notes = Vector<Note>()
        while (query != null && query.moveToNext()) {
            notes.add(queryGetOneNote(query))
        }
        query?.close()
        return notes
    }

    fun setNullNote() {
        currentNoteId = null
        loadDataCurrentId()
    }

    fun getTranscriptionStats(): TranscriptionStats {
        var totalNotes = 0
        var totalAudioQuestions = 0
        var totalAudioAnswers = 0
        var transcribedQuestions = 0
        var transcribedAnswers = 0
        var qConfSum = 0f
        var qConfCount = 0
        var aConfSum = 0f
        var aConfCount = 0

        val queryString = "SELECT ${Note.QUESTION}, ${Note.ANSWER}, ${Note.QUESTION_TRANSCRIPT}, ${Note.ANSWER_TRANSCRIPT}, ${Note.QUESTION_TRANSCRIPT_CONFIDENCE}, ${Note.ANSWER_TRANSCRIPT_CONFIDENCE} FROM ${NotesProvider.NOTES_TABLE_NAME}"
        val result = rawQuery(queryString) ?: return TranscriptionStats(0, 0, 0, 0, 0, 0f, 0f)
        val query = result.cursor

        val qIdx = query.getColumnIndex(Note.QUESTION)
        val aIdx = query.getColumnIndex(Note.ANSWER)
        val qtIdx = query.getColumnIndex(Note.QUESTION_TRANSCRIPT)
        val atIdx = query.getColumnIndex(Note.ANSWER_TRANSCRIPT)
        val qtcIdx = query.getColumnIndex(Note.QUESTION_TRANSCRIPT_CONFIDENCE)
        val atcIdx = query.getColumnIndex(Note.ANSWER_TRANSCRIPT_CONFIDENCE)

        while (query.moveToNext()) {
            totalNotes++
            val q = if (qIdx != -1) query.getString(qIdx) else null
            val a = if (aIdx != -1) query.getString(aIdx) else null
            
            val hasQAudio = !q.isNullOrBlank()
            val hasAAudio = !a.isNullOrBlank()

            if (hasQAudio) totalAudioQuestions++
            if (hasAAudio) totalAudioAnswers++

            val qTrans = if (qtIdx != -1) query.getString(qtIdx) else null
            val aTrans = if (atIdx != -1) query.getString(atIdx) else null

            if (hasQAudio && !qTrans.isNullOrBlank()) transcribedQuestions++
            if (hasAAudio && !aTrans.isNullOrBlank()) transcribedAnswers++

            if (qtcIdx != -1) {
                val qConf = query.getFloat(qtcIdx)
                if (qConf > 0f) {
                    qConfSum += qConf
                    qConfCount++
                }
            }

            if (atcIdx != -1) {
                val aConf = query.getFloat(atcIdx)
                if (aConf > 0f) {
                    aConfSum += aConf
                    aConfCount++
                }
            }
        }

        query.close()
        result.database.close()

        val avgQConf = if (qConfCount > 0) qConfSum / qConfCount else 0f
        val avgAConf = if (aConfCount > 0) aConfSum / aConfCount else 0f

        return TranscriptionStats(
            totalNotes = totalNotes,
            totalAudioQuestions = totalAudioQuestions,
            totalAudioAnswers = totalAudioAnswers,
            transcribedQuestions = transcribedQuestions,
            transcribedAnswers = transcribedAnswers,
            averageQuestionConfidence = avgQConf,
            averageAnswerConfidence = avgAConf
        )
    }

    fun insertDeck(deck: Deck?) {
        val values = ContentValues()

        // Bump the modification time to now.
        DeckDb.deckToContentValues(deck, values)
        try {
            SQLiteDatabase.openDatabase(
                    DbContants.getDatabasePath(), null,
                    SQLiteDatabase.OPEN_READWRITE).use { checkDB -> checkDB.insertOrThrow(NotesProvider.DECKS_TABLE_NAME, null, values) }
        } catch (e: Exception) {
            ToastSingleton.getInstance().error(e.message)
        }
    }

    fun deleteDeck(deck: Deck) {
        try {
            SQLiteDatabase.openDatabase(
                    DbContants.getDatabasePath(), null,
                    SQLiteDatabase.OPEN_READWRITE).use { checkDB ->
                val cause = Deck._ID + " = " + deck.id
                checkDB.delete(NotesProvider.DECKS_TABLE_NAME, cause, null)
            }
        } catch (e: Exception) {
            println(e.message)
            ToastSingleton.getInstance().error(e.message)
        }
    }

    fun updateDeck(deck: Deck) {
        val values = ContentValues()

        // Bump the modification time to now.
        DeckDb.deckToContentValues(deck, values)
        try {
            SQLiteDatabase.openDatabase(
                    DbContants.getDatabasePath(), null,
                    SQLiteDatabase.OPEN_READWRITE).use { checkDB ->
                val cause = Deck._ID + " = " + deck.id
                checkDB.update(NotesProvider.DECKS_TABLE_NAME, values, cause, null)
            }
        } catch (e: Exception) {
            println(e.message)
            ToastSingleton.getInstance().error(e.message)
        }
    }

    fun getDeckCount(id: Int): Int {
        var deckCount = 0
        val COUNT_COLUMN = "MyCount"
        val queryString = ("SELECT COUNT(" + Note._ID + ") AS "
                + COUNT_COLUMN + " FROM " + NotesProvider.NOTES_TABLE_NAME
                + " WHERE " + Note.CATEGORY + " = '" + id + "'")

        val result = rawQuery(queryString) ?: return 0
        val query = result.cursor

        // If we found nothing just go to the max.
        if (query.moveToFirst()) {
            deckCount = query.getInt(query.getColumnIndex(COUNT_COLUMN))
        }

        query.close()
        result.database.close()
        return deckCount
    }

    companion object {
        // TODO make the DbNoteEditor not use a TextView to update BrowseMode
        // Make it a callback!!!
		@JvmStatic
		var instance: DbNoteEditor? = null
            get() {
                if (field == null) {
                    field = DbNoteEditor()
                }
                return field
            }
            private set
    }
}