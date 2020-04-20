package com.md;

import java.io.File;
import java.io.IOException;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.PlaybackParams;
import android.media.audiofx.LoudnessEnhancer;

import com.md.utils.ToastSingleton;

public class AudioPlayer implements OnCompletionListener, MediaPlayer.OnErrorListener {

    private static AudioPlayer instance = null;
    private MediaPlayer mp;
    private OnCompletionListener mFiredOnceCompletionListener;
    private LoudnessEnhancer loudnessEnhancer;
    // Note: noise supressor seem to fail and say not enough memory. NoiseSuppressor.

    public static AudioPlayer getInstance() {
        if (instance == null) {
            instance = new AudioPlayer();
        }
        return instance;
    }

    protected static String transformToM4a(String filename) {
        return filename.replace(".mp3", ".m4a").replace(".wav", ".m4a");
    }

    public static String basename(String filename) {
        return filename.replace(".mp3", "").replace(".wav", "").replace(".m4a", "");
    }

    public static String sanitizePath(String filename) {

        String basename = filename.replace(".mp3", "").replace(".wav", "").replace(".m4a", "");

        // Cut off the negative sign. Just used for dir name.
        basename = basename.substring(1);

        Long fileInNumberForm = Long.parseLong(basename);

        int NUMBER_OF_DIRS = 100;

        int whichDirToPutIn = (int) (fileInNumberForm % NUMBER_OF_DIRS);

        String zeroPadding;
        // make this more general some day.
        if (whichDirToPutIn < 10) {
            zeroPadding = "0";
        } else {
            zeroPadding = "";
        }

        String uniquePathToString = zeroPadding + whichDirToPutIn + "/";

        filename = DbContants.getAudioLocation() + uniquePathToString + filename;

        // TODO
        if (!filename.contains("wav") && !filename.contains("mp3")  && !filename.contains("m4a")) {
            filename += ".mp3";
        }

        return filename;
    }

    /**
     * @param originalFile name of the file, without the save path
     * @param firedOnceCompletionListener
     */
    public synchronized void playFile(String originalFile,
            @Nullable OnCompletionListener firedOnceCompletionListener) {

        if (originalFile == null) {
            ToastSingleton.getInstance().error("Null file path. You should probably delete this note Jacob.");
            return;
        }

        cleanUp();

        mFiredOnceCompletionListener = firedOnceCompletionListener;

        String path = sanitizePath(originalFile);

        File audioFile = new File(path);

        if (!audioFile.exists()) {
            ToastSingleton.getInstance().error(path + " does not exist.");
            return;
        }

        mp = new MediaPlayer();

        try {
            mp.setDataSource(path);
            loudnessEnhancer = new LoudnessEnhancer(mp.getAudioSessionId());
            loudnessEnhancer.setTargetGain(700);
            loudnessEnhancer.setEnabled(true);

            mp.setPlaybackParams(new PlaybackParams().setSpeed(1.7f));
            mp.prepare();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mp.setOnErrorListener(this);
        mp.setOnCompletionListener(this);
        mp.start();
    }

    public synchronized void cleanUp() {
        if (mp != null) {
            mp.stop();
            // Once the MP is released it can't be used again.
            mp.release();
            mp = null;
        }
        if (loudnessEnhancer != null) {
            loudnessEnhancer.release();
            loudnessEnhancer = null;
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mFiredOnceCompletionListener != null) {
            mFiredOnceCompletionListener.onCompletion(mp);
            mFiredOnceCompletionListener = null;
        }
        if (CategorySingleton.getInstance().shouldRepeat()) {
            mp.seekTo(0);
        } else {
            cleanUp();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        System.out.println("TODOJ error during playback what=" + what);
        cleanUp();
        return true;
    }

}