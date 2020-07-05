package com.md;

import android.content.ContentValues;

import com.md.provider.AbstractDeck;
import com.md.provider.Deck;

public class DeckDb extends DbContants {

	public static final String DECKS_TABLE_NAME = "decks";

	public static void deckToContentValues(Deck deck, ContentValues values) {
		values.put(AbstractDeck.NAME, deck.getName());
	}

}
