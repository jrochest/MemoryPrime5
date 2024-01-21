package com.md.modesetters;

import com.md.DbNoteEditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;

public final class DeleterOnClickListener extends MultiClickListener {
	private final DbNoteEditor noteEditor;
	private final Activity context;
	private ItemDeletedHandler itemDeleteHandler;

	public DeleterOnClickListener(DbNoteEditor noteEditor, Activity context,
			ItemDeletedHandler itemDeleteHandler) {
		this.noteEditor = noteEditor;
		this.context = context;
		this.itemDeleteHandler = itemDeleteHandler;
	}

	@Override
	public void onMultiClick(View v) {
		maybeDelete(context);
	}

	private void maybeDelete(final Activity context) {

		AlertDialog.Builder builder = new AlertDialog.Builder(context);

		builder.setMessage("Are you sure you want to delete this note?")
				.setCancelable(false).setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								itemDeleteHandler.deleteNote();
							}
						}).setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}
}
