package com.md;

import com.md.modesetters.DeckInfo;
import com.md.provider.Deck;

public class CategorySingleton {
	private static CategorySingleton instance = null;

	private Deck deck;
	private int mCurrentDeck = INITIAL_CATEGORY;

	// Just a time when I started programming
	// Sat, 04 Sep 2010 11:00:00 GMT
	private static long startInMillis = 1283598000000L;
	private int daysSinceStart;

	private int mLookAheadDays = 0;
	private boolean shouldRepeat = true;

	protected CategorySingleton() {
		daysSinceStart = getTodayInDaysSinceStart();
	}

	public static CategorySingleton getInstance() {
		if (instance == null) {
			instance = new CategorySingleton();
		}
		return instance;
	}

	public static int getTodayInDaysSinceStart() {
		// Exists only to defeat instantiation.
		long currentTimeMillis = System.currentTimeMillis();

		return turnMilliSecondsIntoDaysSinceStart(currentTimeMillis);
	}

	public static int turnMilliSecondsIntoDaysSinceStart(long currentTimeMillis) {
		// m seconds since start
		long secondsSinceStart = (currentTimeMillis - startInMillis) / 1000;

		return (int) ((secondsSinceStart / 3600) / 24);
	}

	public int getLookAheadDays() {
		return mLookAheadDays;
	}

	public void setLookAheadDays(int lookAheadDays) {
		mLookAheadDays = lookAheadDays;
	}


	public void setDeckInfo(DeckInfo deckInfo) {
		this.mCurrentDeck = deckInfo.getId();
		this.deck = deckInfo.getDeck();
		RevisionQueue.Companion.setCurrentDeckReviewQueueDeleteThisTODOJNOW(deckInfo.getRevisionQueue());
	}

	public boolean shouldRepeat() {
		return shouldRepeat;
	}

	public void setRepeat(boolean value) {
		shouldRepeat = value;
	}

	private static final int INITIAL_CATEGORY = -1;

	public boolean hasCategory() {
		return mCurrentDeck != INITIAL_CATEGORY;
	}

	public int getCurrentDeck() {
		return mCurrentDeck;
	}

	public int getDaysSinceStart() {
		return daysSinceStart;
	}

	public int debugIncrementDay() {
		return daysSinceStart++;
	}

	public void setDaysSinceStart(int daysSinceStart) {
		this.daysSinceStart = daysSinceStart;
	}

	public Deck getDeck() {
		return deck;
	}

}
