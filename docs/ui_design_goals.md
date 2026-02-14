# UI Design Goals

## Eyes-Free Interaction

MemoryPrime is designed to be usable **without looking at the screen**. The primary use case is practicing spaced repetition while running, walking, or doing other physical activities.

### Large Touch Areas

- The center practice button occupies **~70% of the screen height** and full width, providing a massive tap target that can be hit reliably without visual aim.
- All buttons use generous padding and minimum height constraints (`heightIn(min = 48.dp)` or larger) to exceed accessibility touch target guidelines.
- The bottom row buttons each occupy half the screen width with tall heights (~270dp), ensuring they can be found by touch alone.

### Audio-First Feedback

- All actions provide **TTS (text-to-speech) feedback** so the user knows what happened without looking.
- Click counts are announced as they accumulate (e.g., "3", "4", "5") so the user can count along.
- State transitions are announced (e.g., "secondary mode", "back to default", "cancelled").

## Unified Input Model

The center screen tap and Bluetooth controller button share **identical click count logic and state machine**. Both input paths feed into `ExternalClickCounter.handleRhythmUiTaps()`, ensuring:

- The same click-count scheme works whether tapping the screen or pressing a Bluetooth remote.
- Users can switch between input methods mid-session without relearning controls.
- The rhythm-based interaction (rapid successive clicks counted as a group) works naturally with both physical buttons and screen taps.

This unified model is implemented via:
- **Screen taps**: `PracticeModeComposable` → `ExternalClickCounter.handleRhythmUiTaps()`
- **Bluetooth remote**: `RemoteInputDeviceManager` → `ExternalClickCounter.handleRhythmUiTaps()`
