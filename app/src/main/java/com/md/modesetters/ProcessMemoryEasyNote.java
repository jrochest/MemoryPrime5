package com.md.modesetters;

import java.util.Iterator;
import java.util.Vector;

import android.app.Activity;

import com.md.CategorySingleton;
import com.md.DbNoteEditor;
import com.md.modesetters.ImportModeSetter.TestInserter;
import com.md.provider.MemoreasyType;
import com.md.provider.Note;

public class ProcessMemoryEasyNote {

	private double internalValAvg = 0;
	private double internalValNum = 0;
	private final Activity memoryDroid;

	public ProcessMemoryEasyNote(Activity memoryDroid) {
		this.memoryDroid = memoryDroid;
	}

	public void processNew(MemoreasyType memoreasyType,
			TestInserter testInserter) {
		// TODO Auto-generated method stub
	
		Vector<Integer> grades = new Vector<Integer>();

		for (int count = 0; count < memoreasyType.getPasses()
				- memoreasyType.getPoints(); count++) {
			grades.add(getPassingGrade());
		}

		for (int count = 0; count < memoreasyType.getFails(); count++) {
			grades.add(getFailingGrade());
		}

		java.util.Collections.shuffle(grades);

		for (int count = 0; count < memoreasyType.getPoints(); count++) {
			grades.add(getPassingGrade());
		}

		// testInserter.publishProgessVisible("grades: " + grades.toString());

		CategorySingleton.getInstance().setDaysSinceStart(0);

		Note note = new Note(memoreasyType.getQfile(), memoreasyType.getAfile());

		for (Iterator<Integer> gradeItr = grades.iterator(); gradeItr.hasNext();) {

			int lastLastRep = note.getLast_rep();

			Integer grade = (Integer) gradeItr.next();

			note.process_answer(grade);

			if (grade == getFailingGrade()) {
				lastLastRep = note.getLast_rep();
				note.process_answer(2);
			}

			int difference = note.getLast_rep() - lastLastRep + 1;

			double numToAvg = 7;
			// Average it and 1.1.
			double messedUpEasiness = ((note.getEasiness() + numToAvg * 1.005) / (numToAvg + 1.0));

			int internalIncrease = Math.max(2,
					(int) (difference * messedUpEasiness));

			int nextEarlyRep = CategorySingleton.getInstance()
					.getDaysSinceStart() + internalIncrease;

			// testInserter.publishProgessVisible("grades: " + grades.toString()
			// + " note: " + note);

			CategorySingleton.getInstance().setDaysSinceStart(nextEarlyRep);

			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		int currentInterval = note.getNext_rep() - note.getLast_rep();
		internalValAvg += currentInterval;
				
		if(currentInterval > 100)
		{
			currentInterval = 100;
		}

		internalValNum++;

		testInserter.publishProgessVisible("number : " + internalValNum
				+ " average: " + internalValAvg / internalValNum);

		int todayInDaysSinceStart = CategorySingleton
				.getTodayInDaysSinceStart();
		
		int NUM_DAYS_BEHIND = 5;
		
		// Review the first 10 days today.
		if (currentInterval > NUM_DAYS_BEHIND) {
			double lastTesting = memoreasyType.getLastTesting();
			
			int lastTestingInDaysSinceStart = CategorySingleton.turnMilliSecondsIntoDaysSinceStart((long)(lastTesting*1000));
			
			// Last review was exactly the last interval
			note.setLast_rep(lastTestingInDaysSinceStart -1);
			// So we review today. 
			note.setNext_rep((int) (currentInterval + lastTestingInDaysSinceStart-1));
			
		} else 
		{
			// Last review was exactly the last interval
			note.setLast_rep(todayInDaysSinceStart - currentInterval-1);
			// So we review today. 
			note.setNext_rep((int) (todayInDaysSinceStart-1));
		}
		
		int MEMORY_EASY_CATEGORY = 1000;
		note.setCategory(MEMORY_EASY_CATEGORY);
		
		DbNoteEditor noteEditor = DbNoteEditor.getInstance();;

		noteEditor.insert(memoryDroid, note);
		
	}

	private Integer getFailingGrade() {
		return 1;
	}

	private Integer getPassingGrade() {
		return 4;
	}

}
