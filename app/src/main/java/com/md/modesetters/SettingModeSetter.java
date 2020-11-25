package com.md.modesetters;

import android.app.Activity;
import android.view.View;
import android.widget.ToggleButton;

import com.md.CategorySingleton;
import com.md.ModeHandler;
import com.md.R;

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
		final ToggleButton markButton = activity.findViewById(R.id.look_ahead);
		final CategorySingleton instance = CategorySingleton.getInstance();

		markButton.setChecked(instance.getLookAheadDays() != 0);
		markButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				final boolean checked = markButton.isChecked();
				instance.setLookAheadDays(checked ? 1 : 0);
			}
		});

		final ToggleButton repeatButton = activity.findViewById(R.id.repeat);
		repeatButton.setChecked(instance.shouldRepeat());
		repeatButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				instance.setRepeat(!repeatButton.isChecked());
			}
		});



	}
}