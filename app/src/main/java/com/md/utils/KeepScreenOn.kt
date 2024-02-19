package com.md.utils;

import android.os.Handler;
import android.view.WindowManager;

import com.md.SpacedRepeaterActivity;

public class KeepScreenOn {
    private static KeepScreenOn instance = null;

    private int sequenceNumber = 0;

    private static final int TIMEOUT_MILLIS = 15 * 60 * 1000;

    private KeepScreenOn() {
    }

    public static KeepScreenOn getInstance() {
        if (instance == null) {
            instance = new KeepScreenOn();
        }
        return instance;
    }

    public void keepScreenOn(final SpacedRepeaterActivity activity) {
        sequenceNumber++;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final int requestId = sequenceNumber;
        new Handler().postDelayed(() -> {
            if (activity.isDestroyed() || activity.isFinishing()) {
                return;
            }

            if (requestId == sequenceNumber) {
                // TIMEOUT_MILLIS without a new command so remove the screen on flag.
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }, TIMEOUT_MILLIS);
    }
}
