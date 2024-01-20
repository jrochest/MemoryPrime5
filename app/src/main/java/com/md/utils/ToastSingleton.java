package com.md.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.md.modesetters.TtsSpeaker;

public class ToastSingleton {
    private static ToastSingleton instance = null;
    private static String TAG = "TOASTER";

    private Context context;

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    protected ToastSingleton() {}

    public static ToastSingleton getInstance() {
        if (instance == null) {
            instance = new ToastSingleton();
        }
        return instance;
    }

    public void error(String msg) {
        TtsSpeaker.speak("Error " + msg);
        msgCommon("Error: " + msg);
    }

    public void speakAndShow(String msg) {
        TtsSpeaker.speak(msg);
        msgCommon(msg);
    }

    public void msgCommon(String msg) {
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.show();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Toast: " + msg);
        }
    }

    public void msg(String msg) {
        msgCommon(msg);
    }
}
