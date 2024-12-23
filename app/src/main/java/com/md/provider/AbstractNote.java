package com.md.provider;

import android.net.Uri;

import com.md.NotesProvider;

public class AbstractNote {

	/**
	 * The content:// style URL for this table
	 */
	public static final Uri CONTENT_URI = Uri.parse("content://"
				+ NotesProvider.AUTHORITY + "/notes");
	/**
	 * The MIME type of {@link #CONTENT_URI} providing a directory of notes.
	 */
	public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note";
	/**
	 * The default sort order for this table
	 */
	public static final String DEFAULT_SORT_ORDER = Note._ID + " ASC";
	/**
	 * The MIME type of a {@link #CONTENT_URI} sub-directory of a single note.
	 */
	public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note";
	/**
	 * The title of the note
	 * <P>
	 * Type: TEXT
	 * </P>
	 */
	public static final String GRADE = "grade";
	public static final String QUESTION = "question";
	public static final String ANSWER = "answer";
	public static final String CATEGORY = "category";
	public static final String UNSEEN = "unseen";
	public static final String EASINESS = "easiness";
	public static final String ACQ_REPS = "acq_reps";
	public static final String RET_REPS = "ret_reps";
	public static final String LAPSES = "lapses";
	public static final String ACQ_REPS_SINCE_LAPSE = "acq_reps_since_lapse";
	public static final String RET_REPS_SINCE_LAPSE = "ret_reps_since_lapse";
	public static final String LAST_REP = "last_rep";
	public static final String NEXT_REP = "next_rep";
	public static final String PRIORITY = "priority";
	public static final String MARKED = "marked";
	protected int grade;
	

	
	// TODO did you ever do the population of one of these from the database.
	
	protected int id;
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	protected String question;
	protected String answer;

	protected int categoryAkaDeckId;
	protected boolean unseen;
	protected boolean marked;
	
	public boolean isMarked() {
		return marked;
	}

	public void setMarked(boolean marked) {
		this.marked = marked;
	}

	protected float easiness;

	public void setLast_rep(int last_rep) {
		this.last_rep = last_rep;
	}

	public int getGrade() {
		return grade;
	}

	public void setGrade(int grade) {
		this.grade = grade;
	}

	public String getQuestion() {
		return question;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public String getAnswer() {
		return answer;
	}

	public void setAnswer(String answer) {
		this.answer = answer;
	}

	public int getCategoryAkaDeckId() {
		return categoryAkaDeckId;
	}

	public void setCategoryAkaDeckId(int categoryAkaDeckId) {
		this.categoryAkaDeckId = categoryAkaDeckId;
	}

	public boolean isUnseen() {
		return unseen;
	}

	public void setUnseen(boolean unseen) {
		this.unseen = unseen;
	}

	public float getEasiness() {
		return easiness;
	}

	public void setEasiness(float easiness) {
		this.easiness = easiness;
	}

	public int getAcq_reps() {
		return acq_reps;
	}

	public void setAcq_reps(int acq_reps) {
		this.acq_reps = acq_reps;
	}

	public int getRet_reps() {
		return ret_reps;
	}

	public void setRet_reps(int ret_reps) {
		this.ret_reps = ret_reps;
	}

	public int getLapses() {
		return lapses;
	}

	public void setLapses(int lapses) {
		this.lapses = lapses;
	}

	public int getAcq_reps_since_lapse() {
		return acq_reps_since_lapse;
	}

	public void setAcq_reps_since_lapse(int acq_reps_since_lapse) {
		this.acq_reps_since_lapse = acq_reps_since_lapse;
	}

	public int getRet_reps_since_lapse() {
		return ret_reps_since_lapse;
	}

	public void setRet_reps_since_lapse(int ret_reps_since_lapse) {
		this.ret_reps_since_lapse = ret_reps_since_lapse;
	}

	public int getLast_rep() {
		return last_rep;
	}

	public void setLast_reps(int last_rep) {
		this.last_rep = last_rep;
	}

	public int getNext_rep() {
		return next_rep;
	}

	public int interval() {
		return getNext_rep() - getLast_rep();
	}
	
	public void setNext_rep(int next_rep) {
		this.next_rep = next_rep;
	}

	protected int acq_reps;
	protected int ret_reps;
	protected int lapses;
	protected int acq_reps_since_lapse;
	protected int ret_reps_since_lapse;
	protected int last_rep;
	protected int next_rep;
	// This is mostly used for tasks. Starts at 100 and can be decreased via 6 presses.
	public int priority;

	public AbstractNote() {
		super();
	}

}