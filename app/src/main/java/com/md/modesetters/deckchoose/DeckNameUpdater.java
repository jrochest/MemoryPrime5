package com.md.modesetters.deckchoose;

import net.londatiga.android.QuickAction;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;

import com.md.DbNoteEditor;
import com.md.modesetters.DeckChooseModeSetter;
import com.md.modesetters.DeckInfo;
import com.md.provider.Deck;


public final class DeckNameUpdater implements
		View.OnClickListener {
	private final Activity memoryDroid;
	private final DeckInfo deckInfo;
	private final QuickAction qa;
	private final DeckChooseModeSetter modeSetter;

	public DeckNameUpdater(Activity memoryDroid2,
			DeckInfo deckInfo, QuickAction qa, DeckChooseModeSetter modeSetter) {
		this.memoryDroid = memoryDroid2;
		this.deckInfo = deckInfo;
		this.qa = qa;
		this.modeSetter = modeSetter;
	
	}

	@Override
	public void onClick(View v) {
		qa.dismiss();
		
		AlertDialog.Builder alert = new AlertDialog.Builder(memoryDroid);

		alert.setTitle("Please Enter a new Deck Name for: "
				+ this.deckInfo.getName());

		// Set an EditText view to get user input
		final EditText input = new EditText(memoryDroid);
		input.setText(this.deckInfo.getName());
		
		alert.setView(input);

		alert.setPositiveButton("Ok",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,
							int whichButton) {
						Editable value = input.getText();
						
						String val = value.toString();
					
					    Deck deck = new Deck(deckInfo.getCategory(), val);

					    deckInfo.setDeck(deck);
					    
						DbNoteEditor.getInstance().updateDeck(deck);
						modeSetter.setupCreateMode();
						
					}
				});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,
							int whichButton) {
						// Canceled.
					}
				});

		alert.show();

	}
}
