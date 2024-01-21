package com.md.modesetters;

import com.md.RevisionQueue;
import com.md.provider.Deck;

public class DeckInfo {
	private RevisionQueue revisionQueue;
	private Deck deck;

	public Deck getDeck() {
		return deck;
	}

	public void setDeck(Deck deck) {
		this.deck = deck;
	}

	public DeckInfo(Deck deck, RevisionQueue revisionQueue) {
		this.revisionQueue = revisionQueue;
		this.deck = deck;
	}

	public String getName() {
		return deck.name;
	}

	public int getId() {
		return deck.id;
	}

	public RevisionQueue getRevisionQueue() {
		return revisionQueue;
	}

	public boolean isActive()  {
		return !deck.name.contains("inactive");
	}
}
