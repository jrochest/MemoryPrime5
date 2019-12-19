package com.md.modesetters.deckchoose;

import net.londatiga.android.QuickAction;
import android.app.Activity;
import android.view.View;

import com.md.modesetters.DeckChooseModeSetter;
import com.md.modesetters.DeckInfo;

public final class DeckClickLoader implements
		View.OnClickListener {
	private final DeckInfo deckInfo;
	private final QuickAction qa;
	private final DeckChooseModeSetter modeSetter;

	public DeckClickLoader(Activity memoryDroid, DeckInfo deckInfo,
			QuickAction qa, DeckChooseModeSetter modeSetter) {

		this.deckInfo = deckInfo;
		this.qa = qa;
		this.modeSetter = modeSetter;
	}

	@Override
	public void onClick(View v) {

		qa.dismiss();
		modeSetter.loadDeck(deckInfo);

	}
}
