package com.md.modesetters.deckchoose;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;

import com.md.DbNoteEditor;
import com.md.modesetters.DeckChooseModeSetter;
import com.md.modesetters.DeckInfo;
import com.md.utils.ToastSingleton;

public final class DeckDeleter implements View.OnClickListener {
	private final Activity memoryDroid;
	private final DeckInfo deckInfo;
	private final DeckChooseModeSetter modeSetter;

	public DeckDeleter(Activity memoryDroid, DeckInfo deckInfo, DeckChooseModeSetter modeSetter) {
		this.memoryDroid = memoryDroid;
		this.deckInfo = deckInfo;

		this.modeSetter = modeSetter;
	}

	@Override
	public void onClick(View v) {
		AlertDialog.Builder alert = new AlertDialog.Builder(memoryDroid);

		alert.setTitle("Are you sure you want the delete: '"
				+ this.deckInfo.getName() + "'?");

		// Set an EditText view to get user input

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {

				if(deckInfo.getName().contains("saved"))
				{
					ToastSingleton.getInstance().msg("Cannot delete 'saved' deck.");
				} else
				{
					DbNoteEditor.getInstance().deleteDeck(deckInfo.getDeck());
				}
				modeSetter.setupCreateMode();
			}
		});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// Canceled.
					}
				});

		alert.show();

	}
}
