package com.md;

import java.util.Stack;

import android.app.Activity;

import com.md.modesetters.DeckChooseModeSetter;
import com.md.modesetters.ModeSetter;

public class ModeHandler {

	Stack<ModeSetter> modeStack = new Stack<ModeSetter>();
	private final Activity context;

	public ModeHandler(Activity context) {
		this.context = context;

	}

	public ModeSetter whoseOnTop()
	{
		// Must have two.
		if (!modeStack.empty()) {
			return modeStack.peek();
		} else {
			return null;
		}
	}
	
	public boolean goBack() {
		// Must have two.
		if (modeStack.size() > 1) {
			ModeSetter pop = modeStack.pop();
			modeStack.peek().setupMode(context);
			return true;
		}
		return false;
	}

	public void add(ModeSetter modeSetter) {
		// Don't put your self on!
		if(modeStack.empty() || modeStack.peek() != modeSetter)
		{
		   modeStack.push(modeSetter);
		}
	}

}
