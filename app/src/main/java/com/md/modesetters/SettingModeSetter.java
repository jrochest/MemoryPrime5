package com.md.modesetters;

import com.md.AudioPlayer;
import com.md.CategorySingleton;
import com.md.CreateModeData;
import com.md.DbNoteEditor;
import com.md.ModeHandler;
import com.md.NoteEditorListener;
import com.md.R;
import com.md.provider.Note;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

public class SettingModeSetter extends ModeSetter implements
		ItemDeletedHandler {

	private static SettingModeSetter instance = null;

	protected SettingModeSetter() {

	}

	public static SettingModeSetter getInstance() {
		if (instance == null) {
			instance = new SettingModeSetter();
		}
		return instance;
	}

	public void setup(Activity memoryDroid, ModeHandler modeHand) {
		parentSetup(memoryDroid, modeHand);
	}

	public void setupModeImpl(final Activity context) {
		commonSetup(context, R.layout.settings);
		setupSettings(context);
	}

	private void setupSettings(final Activity activity) {
		final ToggleButton markButton = (ToggleButton) activity
				.findViewById(R.id.look_ahead);

		final CategorySingleton instance = CategorySingleton.getInstance();
		markButton.setChecked(instance.getLookAheadDays() != 0);
		markButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				final boolean checked = markButton.isChecked();
				instance.setLookAheadDays(checked ? 1 : 0);
			}
		});
	}

	@Override
	public void deleteNote() {
		// TODO(jrochest) Remove this!
	}
}