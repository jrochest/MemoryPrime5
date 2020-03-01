package com.md;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.md.modesetters.LearningModeSetter;
//  Maybe remove this because we switched to media session
public class RemoteControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        System.out.println("TODOJ onReceive raw event!" + intent);
        //  Maybe remove this because we switched to media session
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            LearningModeSetter.getInstance().actionMediaButton(intent);

            abortBroadcast();

        }
    }
}