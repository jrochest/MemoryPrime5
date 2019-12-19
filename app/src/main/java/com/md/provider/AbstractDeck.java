package com.md.provider;

import android.net.Uri;

import com.md.NotesProvider;

public class AbstractDeck {

	/**
	 * The content:// style URL for this table
	 */
	public static final Uri CONTENT_URI = Uri.parse("content://"
				+ NotesProvider.AUTHORITY + "/decks");
	
    /**
	 * The default sort order for this table
	 */
	public static final String DEFAULT_SORT_ORDER = Deck._ID + " ASC";
	
	public static final String NAME = "name";
	protected String name;
		
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	protected int id;
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}
	
	public AbstractDeck() {
		super();
	}

}