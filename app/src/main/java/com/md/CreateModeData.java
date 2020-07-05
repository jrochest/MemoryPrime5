package com.md;

import com.md.modesetters.CreateModeSetter;

public class CreateModeData {
	private static CreateModeData instance = null;

	protected CreateModeData() {
		// Exists only to defeat instantiation.
		clearState();
	}
	
	public static CreateModeData getInstance() {
		if (instance == null) {
			instance = new CreateModeData();
		}
		return instance;
	}

	
	public enum  State  {
	    BLANK, RECORDING, RECORDED, PLAYING
	}


	public State getQuestionState(int qUESTION_INDEX) {
		
		return questionState[qUESTION_INDEX];
	}

	public void setQuestionState(State questionState, int qUESTION_INDEX) {
		this.questionState[qUESTION_INDEX] = questionState;
	}

	private State[] questionState = new State[CreateModeSetter.ANSWER_INDEX+1];


    private String question[] = new String[CreateModeSetter.ANSWER_INDEX+1];

	public String getQuestion(int qUESTION_INDEX) {
		return question[qUESTION_INDEX] ;
	}

	public void setAudioFile(String question, int qUESTION_INDEX) {
		this.question[qUESTION_INDEX] = question;
	}

	public void clearState() {
		questionState[CreateModeSetter.QUESTION_INDEX] = State.BLANK;
		questionState[CreateModeSetter.ANSWER_INDEX] = State.BLANK;
		question[CreateModeSetter.QUESTION_INDEX] = null;
		question[CreateModeSetter.ANSWER_INDEX] = null;
	}
}
