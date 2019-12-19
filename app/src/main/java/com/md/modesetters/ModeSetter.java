package com.md.modesetters;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import com.md.CategorySingleton;
import com.md.ModeHandler;
import com.md.SpacedRepeaterActivity;
import com.md.utils.ToastSingleton;

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
    }

    public abstract void setupModeImpl(final Activity context);

    public void parentSetup(final Activity context, ModeHandler modeHand) {
        this.mActivity = (SpacedRepeaterActivity) context;
        this.modeHand = modeHand;
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

    protected void hideSystemUi() {// Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        mActivity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    protected void showSystemUi() {
        mActivity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    public void proceed() {
    }

    public void undo() {
    }

    public void handleReplay() {
    }

    public void proceedFailure() {

    }

    public void toggleDim() {

    }

    public void mark() {}
}