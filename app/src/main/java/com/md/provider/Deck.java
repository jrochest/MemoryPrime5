package com.md.provider;

import android.provider.BaseColumns;

/**
 * Notes table
 */
public final class Deck extends AbstractDeck implements BaseColumns, Cloneable {

	private static final int UNSET_VAL = -1;
	
	private int mSize = 0;

	private int mTodaysReview;

	public Deck(int id, String name) {
		super();
		this.name = name;  
		this.id = id;
	}

	public Deck(String name) {
		super();
		this.name = name;  
		this.id = UNSET_VAL;
	}
	
	public void setSize(int size) {
		mSize = size;
	}
	
	public void setTodayReview(int todaysReview) {
		mTodaysReview = todaysReview;
	}
	
	public String toString() {
		return name + "\nToday's review: "
				+ mTodaysReview + " of "
				+ mSize;
	}
	
}