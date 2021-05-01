package com.md.modesetters;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.md.AudioPlayer;
import com.md.CategorySingleton;
import com.md.ModeHandler;
import com.md.SpacedRepeaterActivity;
import com.md.utils.ToastSingleton;

import static android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;

public abstract class ModeSetter {
    protected SpacedRepeaterActivity mActivity;
    protected ModeHandler modeHand;

    public void setupMode(final Activity context) {
        if (!(this instanceof DeckChooseModeSetter) &&
                !CategorySingleton.getInstance().hasCategory()) {
            ToastSingleton.getInstance().msg("No deck selected. \nUsing default");
            final DeckChooseModeSetter deckChooser = DeckChooseModeSetter.getInstance();
            final DeckInfo defaultDeck = deckChooser.getDefaultDeck();
            deckChooser.loadDeck(defaultDeck);
        }
        setupModeImpl(context);
        updateShouldRepeat();
    }

    public abstract void setupModeImpl(final Activity context);

    public void parentSetup(final Activity context, ModeHandler modeHand) {
        this.mActivity = (SpacedRepeaterActivity) context;
        this.modeHand = modeHand;
    }

    protected void updateShouldRepeat() {
        // Hiding stops the repeat playback in all besides learning mode.
        AudioPlayer.getInstance().setShouldRepeat(false);
    }

    protected void commonSetup(final Activity context, int view) {
        context.setContentView(view);
        adjustScreenLock();
        modeHand.add(this);
    }

    protected void adjustScreenLock() {
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showSystemUi();
        /*
        See if these affect brightness when screen off.
        android.provider.Settings.System.putInt(mActivity.getContentResolver(),
                android.provider.Settings.System.SCREEN_BRIGHTNESS, 200);
        android.provider.Settings.System.putInt(mActivity.getContentResolver(),
                android.provider.Settings.System.SCREEN_BRIGHTNESS, 0);
                */
    }

    protected void hideSystemUi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
          return;
        }
        WindowInsetsController controller = mActivity.getWindow().getInsetsController();
        if (controller != null) {
          //controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsAppearance(APPEARANCE_LIGHT_NAVIGATION_BARS, APPEARANCE_LIGHT_NAVIGATION_BARS);
            controller.setSystemBarsAppearance(APPEARANCE_LIGHT_STATUS_BARS, APPEARANCE_LIGHT_STATUS_BARS);
        }
    }

    protected void showSystemUi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        WindowInsetsController controller = mActivity.getWindow().getInsetsController();
        if (controller != null) {
            controller.setSystemBarsAppearance(0, APPEARANCE_LIGHT_NAVIGATION_BARS);
            controller.setSystemBarsAppearance(0, APPEARANCE_LIGHT_STATUS_BARS);
        }
    }

    public void proceed() {
        // If play is pressed setup learning mode.
        LearningModeSetter.getInstance().setupMode(mActivity);
    }

    public void undo() {
    }

    public void resetActivity() {}

    public void handleReplay() {
    }

    public void proceedFailure() {

    }

    public void toggleDim() {

    }

    public void mark() {}

    public String secondaryAction() {
        return "";
    }

    public void postponeNote() {}
}