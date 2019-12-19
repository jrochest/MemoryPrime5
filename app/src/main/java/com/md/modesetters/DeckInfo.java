package com.md.modesetters;

import com.md.RevisionQueue;
import com.md.provider.Deck;

public class DeckInfo {
	private RevisionQueue revisionQueue;
	private Deck deck;
	private final int deckCount;

	public Deck getDeck() {
		return deck;
	}

	public void setDeck(Deck deck) {
		this.deck = deck;
	}

	public DeckInfo(Deck deck, RevisionQueue revisionQueue, int deckCount) {
		this.revisionQueue = revisionQueue;
		this.deck = deck;
		this.deckCount = deckCount;
		deck.setSize(deckCount);
		deck.setTodayReview(revisionQueue.getSize());
	}

	public int getDeckCount() {
		return deckCount;
	}

	public String getName() {
		return deck.getName();
	}

	public int getCategory() {
		return deck.getId();
	}

	public RevisionQueue getRevisionQueue() {
		return revisionQueue;
	}
}
