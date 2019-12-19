package com.md.provider;

public class AbstractRep {
    public AbstractRep(int noteId, int interval, int score, long timeStampMs) {
        this.noteId = noteId;
        this.interval = interval;
        this.score = score;
        this.timeStampMs = timeStampMs;
    }

    @Override
    public String toString() {
        return "AbstractRep{" + "id=" + id + ", noteId=" + noteId + ", interval=" + interval +
                ", score=" + score + ", timeStampMs=" + timeStampMs + '}';
    }

    /**

     * The unique ID for a row.
     * <P>Type: INTEGER (long)</P>
     */
    public static final String _ID = "_id";

    public static final String NOTE_ID = "note_id";

    public static final String INTERVAL = "interval";

    public static final String SCORE = "score";

    public static final String TIME_STAMP_MS = "time_stamp";

    public int getId() {
        return id;
    }

    public int getNoteId() {
        return noteId;
    }

    public int getInterval() {
        return interval;
    }

    public int getScore() {
        return score;
    }

    public long getTimeStampMs() {
        return timeStampMs;
    }

    protected int id;
    protected int noteId;
    protected int interval;
    protected int score;
    protected long timeStampMs;

    public void setId(long id) {
        this.id = (int) id;
    }
}
