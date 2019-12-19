package com.md;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;

import com.md.modesetters.CreateModeSetter;

public class MyOnCompletionListener implements OnCompletionListener {

	private final CreateModeSetter createModeSetter;
	private final int currentIndex;

	public MyOnCompletionListener(CreateModeSetter createModeSetter, int currentIndex) {

		this.createModeSetter = createModeSetter;
		// TODO Auto-generated constructor stub
		this.currentIndex = currentIndex;
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		// TODO Auto-generated method stub
		createModeSetter.moveToDonePlayingOrPlaying(currentIndex, CreateModeData.getInstance(), true); 

	}

}
