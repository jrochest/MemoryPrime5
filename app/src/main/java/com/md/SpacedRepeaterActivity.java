package com.md;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;

import com.md.modesetters.BrowsingModeSetter;
import com.md.modesetters.CleanUpAudioFilesModeSetter;
import com.md.modesetters.CreateModeSetter;
import com.md.modesetters.DeckChooseModeSetter;
import com.md.modesetters.LearningModeSetter;
import com.md.modesetters.ModeSetter;
import com.md.modesetters.SettingModeSetter;

import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.ToneGenerator.TONE_CDMA_DIAL_TONE_LITE;

public class SpacedRepeaterActivity extends Activity {
    private static final String LOG_TAG = "SpacedRepeater";

    private ToneGenerator toneGenerator = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        DbContants.setup(this);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        ActivityHelper activityHelper = new ActivityHelper();
        activityHelper.commonActivitySetup(this);

        // Normal mode.
        CreateModeSetter.getInstance().setUp(this, modeHand);
        BrowsingModeSetter.getInstance().setup(this, modeHand);
        DeckChooseModeSetter.getInstance().setUp(this, modeHand);
        LearningModeSetter.getInstance().setUp(this, modeHand);
        DeckChooseModeSetter.getInstance().setupMode(this);
        SettingModeSetter.getInstance().setup(this, modeHand);
        CleanUpAudioFilesModeSetter.getInstance().setup(this, modeHand);
    }

    ModeHandler modeHand = new ModeHandler(this);

    @Override
    public void onBackPressed() {

        modeHand.goBack();

        return;
    }

    @Override
    protected void onStart() {
        super.onStart();
        toneGenerator = new ToneGenerator(STREAM_MUSIC, 1);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        ActivityHelper activityHelper = new ActivityHelper();
        activityHelper.createCommonMenu(menu, this);

        return true;
    }

    public void makeDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(message);

        builder.create();
        builder.show();
    }

    private boolean isFromMemprimeDevice(int keyCode, KeyEvent event) {
        if (event == null) {
            return false;
        }

        InputDevice device = event.getDevice();
        if (device == null) {
            return false;
        }

        if (isFromMultiButtonMemprimeDevice(keyCode, event)) {
            return true;
        }

        final String name = device.getName();
        return name.contains("AB Shutter3") ||
                name.contains("AK LIFE BT") ||
                name.contains("BLE") ||
                name.contains("BR301") ||
                name.contains("memprime") ||
                name.contains("STRIM-BTN10") ||  // MARREX.
                name.contains("Button Jack") ||
                name.contains("PhotoShot"); // Wide flat one
    }


    private boolean isFromMultiButtonMemprimeDevice(int keyCode, KeyEvent event) {
        if (event == null) {
            return false;
        }

        InputDevice device = event.getDevice();
        if (device == null) {
            return false;
        }

        final String name = device.getName();
        return name.contains("Shutter Camera");
    }

    private long mPressGroupLastPressMs = 0;
    private long mPressGroupLastPressEventMs = 0;
    private static final long PRESS_GROUP_MAX_GAP_MS_BLUETOOTH = 400L;
    // Jacob can consistently press every 180ms. With training we can probably drop this down.
    public static final long PRESS_GROUP_MAX_GAP_MS_SCREEN = 250L;

    private int mPressGroupCount = 0;
    private int mPressSequenceNumber = 0;

    private boolean hasAudioFocus = false;

    private AudioManager.OnAudioFocusChangeListener afListener = new AudioManager
            .OnAudioFocusChangeListener
            () {

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    hasAudioFocus = true;
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    hasAudioFocus = false;
                    break;
            }
        }
    };

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        final ModeSetter modeSetter = modeHand.whoseOnTop();
        // BR301 sends an enter command, which we want to ignore.
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true;
        }

        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyUp(keyCode, event);
        }

        System.out.println("TODOJ event" + event);

        if (modeSetter == null || !isFromMemprimeDevice(keyCode, event)) {
            return super.onKeyUp(keyCode, event);
        }
        return true;
    }

    /**
     * We pay more attention to down events because for some reason they are much more likely
     * to be sent. At least that's true on the AK life BT shutters. Perhaps it's acting like a stuck
     * press. There's a repeat count:
     */
    @Override
    public boolean onKeyDown(int keyCode, final KeyEvent event) {
        final ModeSetter modeSetter = modeHand.whoseOnTop();

        System.out.println("TODOJ event" + event);

        // BR301 sends an enter command, which we want to ignore.
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true;
        }

        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyDown(keyCode, event);
        }

        if (modeSetter == null || !isFromMemprimeDevice(keyCode, event)) {
            return super.onKeyDown(keyCode, event);
        }

        if (!(modeSetter instanceof LearningModeSetter)) {
            LearningModeSetter.getInstance().setupMode(this);
            return true;
        }

        final long eventTimeMs = event.getEventTime();
        return handleRhythmUiTaps(modeSetter, eventTimeMs, PRESS_GROUP_MAX_GAP_MS_BLUETOOTH);
    }

    public boolean handleRhythmUiTaps(final ModeSetter modeSetter, long eventTimeMs, long pressGroupMaxGapMs) {
        final long currentTimeMs = SystemClock.uptimeMillis();
        if (mPressGroupLastPressMs == 0) {
            mPressGroupCount = 1;
            System.out.println("New Press group.");
        } else if (mPressGroupLastPressEventMs + pressGroupMaxGapMs < eventTimeMs) {
            // Too much time has ellapsed start a new press group.
            mPressGroupCount = 1;
            System.out.println("New Press group. Expiring old one.");
        } else {
            System.out.println("Time diff: " + (currentTimeMs - mPressGroupLastPressMs));
            System.out.println("Time diff event time: " + (eventTimeMs - mPressGroupLastPressEventMs));
            mPressGroupCount++;
            System.out.println("mPressGroupCount++. " + mPressGroupCount);
        }

        mPressGroupLastPressEventMs = eventTimeMs;
        mPressGroupLastPressMs = currentTimeMs;
        mPressSequenceNumber++;
        final int currentSequenceNumber = mPressSequenceNumber;

        // Don't wait to handle 8 toggle focus.
        if (mPressGroupCount == 8) {
            maybeChangeAudioFocus(!hasAudioFocus);
            return true;
        }
        // Don't let anything beyond eight go through. This avoid continually toggling audiofocus.
        if (mPressGroupCount > 8) {
            return true;
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mPressSequenceNumber != currentSequenceNumber) {
                    return;
                }
                switch (mPressGroupCount) {
                    case 1:
                        modeSetter.handleReplay();
                        break;
                    case 2:
                        modeSetter.proceed();
                        break;
                    case 3:
                        modeSetter.proceedFailure();
                        break;
                    case 4:
                        modeSetter.undo();
                        break;
                    case 5:
                        // Reset!
                        modeSetter.setupMode(SpacedRepeaterActivity.this);
                        break;
                    case 6:
                        modeSetter.toggleDim();
                        break;
                    case 7:
                        modeSetter.mark();
                        break;
                    default:
                        break;
                }
            }
        }, pressGroupMaxGapMs);
        return true;
    }

    public void maybeChangeAudioFocus(boolean shouldHaveFocus) {
        if (hasAudioFocus == shouldHaveFocus) {
            // The audiofocus matches request already.
            return;
        }

        final AudioManager audioManager = (AudioManager) this.getSystemService(
                Context.AUDIO_SERVICE);
        final AudioAttributes mPlaybackAttributes = new AudioAttributes.Builder().setUsage(
                AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        final AudioFocusRequest mFocusRequest = new AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(mPlaybackAttributes)
                .setOnAudioFocusChangeListener(afListener).build();

        if (shouldHaveFocus) {
            int res = audioManager.requestAudioFocus(mFocusRequest);
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                hasAudioFocus = true;
            } else {
                hasAudioFocus = false;
            }
        } else {
            audioManager.abandonAudioFocus(afListener);
            hasAudioFocus = false;
        }
    }

    public boolean hasAudioFocus() {
        return hasAudioFocus;
    }

    public void keepHeadphoneAlive() {
        if (toneGenerator == null) {
            return;
        }
        // keep the headphones turned on by playing an almost silent sound n seconds.
        toneGenerator.startTone(TONE_CDMA_DIAL_TONE_LITE, /* Two minutes */ 1000 * 60 * 2);
    }

    public void maybeDim() {
        final ModeSetter modeSetter = modeHand.whoseOnTop();
        modeSetter.toggleDim();
    }
}
