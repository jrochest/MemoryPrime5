package com.md.modesetters.deckchoose;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;

import com.md.DbNoteEditor;
import com.md.modesetters.DeckChooseModeSetter;
import com.md.provider.Deck;

public final class InsertNewHandler implements View.OnClickListener {

	private final DeckChooseModeSetter modeSetter;
	public InsertNewHandler(Activity memoryDroid,
			DeckChooseModeSetter modeSetter) {
		this.memoryDroid = memoryDroid;
		this.modeSetter = modeSetter;
	}

	public void onClick(View v) {
		AlertDialog.Builder alert = new AlertDialog.Builder(memoryDroid);

		alert.setTitle("Please Enter a Deck Name");

		// Set an EditText view to get user input
		final EditText input = new EditText(memoryDroid);
		alert.setView(input);
		alert.setPositiveButton("Ok",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,
							int whichButton) {
						Editable value = input.getText();
						String val = value.toString();
					    Deck deck = new Deck(val);
						DbNoteEditor.getInstance().insertDeck(deck);
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
	public Activity memoryDroid;

}
