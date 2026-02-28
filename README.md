# MemoryPrime
Audionotes Notes app with spaced rep algorithm 

## Architecture & Technical Decisions

### Offline Speech-to-Text (STT) Pipeline
MemoryPrime features an offline audio transcription pipeline. When deciding on the STT engine, we had to work around a few architectural limitations of the Android ecosystem:

1. **Why not Android's built-in `SpeechRecognizer`?**
   While Android 12 introduced `SpeechRecognizer.createOnDeviceSpeechRecognizer()` for excellent on-device recognition, its API is hardwired to listen directly to the device's microphone via the `ACTION_RECOGNIZE_SPEECH` intent. It inherently requires the `RECORD_AUDIO` permission and cannot be easily fed pre-recorded audio files (`AAC/WAV`).

2. **Why Vosk?**
   Since we needed to transcribe existing audio files stored in the database, we built a custom pipeline that decodes the audio into 16kHz PCM data using `MediaExtractor` and `MediaCodec`.
   We chose **Vosk** (`com.alphacephei:vosk-android`) as our STT engine because:
   - It runs completely offline, ensuring user privacy.
   - It has an official Maven distribution with ready-to-use Android bindings, avoiding the need to write complex custom C++ JNI wrappers (which would be required for alternatives like Whisper.cpp).
   - It provides lightweight, highly accurate acoustic models (typically ~50MB) that are well-suited for mobile devices with constrained storage and memory.

### Known Issues & Android 15 Compatibility

*   **16KB Page Size Alignment**: The current version of `vosk-android` compiles its `.so` binaries (`libvosk.so`) with 4KB memory page alignment. Android 15 introduces strict requirements for native libraries to use 16KB page alignment. Currently, this triggers a "16 KB compatible" warning dialog on Android 15 devices when running a debug build.
    *   **TODO**: Upgrade `com.alphacephei:vosk-android` to a newer version (published after Android NDK r28+) before the Google Play November 1st, 2025 deadline to officially resolve the 16KB `LOAD` segment alignment.

