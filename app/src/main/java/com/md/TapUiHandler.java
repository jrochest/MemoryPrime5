package com.md;

import com.md.modesetters.ModeSetter;

interface TapUiHandler {
    boolean handleRhythmUiTaps(ModeSetter modeSetter, long eventTimeMs, long pressGroupMaxGapMs, int tapCount);
}
