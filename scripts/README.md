# Mac Audio Transcriber Pipeline

This script offloads the heavy audio processing from Memory Prime to your Mac, utilizing OpenAI's `whisper` for offline speech-to-text and Google's `gemma-4-26b-moe` via LM Studio to refine the transcriptions.

## Prerequisites

1. Have your Android device connected to your Mac via USB or Wi-Fi debugging (`adb devices` should show your device).
2. Open **LM Studio**, load the `gemma-4-26b-moe` model, and click **"Start Server"** in the Developer tab to expose it on `http://localhost:1234/v1`.

## How to Run It

To run the pipeline and sync transcripts back to Memory Prime, follow these steps from the root `MemoryPrime5` folder:

1. **Activate the Virtual Environment**:  
   *(You must do this every time you open a new terminal window to run the script!)*
   ```bash
   source .venv/bin/activate
   ```

2. **Run the Script Commands**:
   The script is broken down into modular steps so you don't have to keep your phone plugged in for hours!

   * **Pull & Transcribe** (Default): Pulls data from your phone and starts processing. You can unplug your phone once transcribing begins.
     ```bash
     python3 scripts/mac_transcriber.py
     ```
   * **Push**: Pushes the generated `update_transcripts.json` back to your phone.
     ```bash
     python3 scripts/mac_transcriber.py push
     ```

   *Optional Commands*:
   * `python3 scripts/mac_transcriber.py pull` (Only extract data)
   * `python3 scripts/mac_transcriber.py transcribe` (Only process local data)
   * `python3 scripts/mac_transcriber.py all` (Pull, Transcribe, and Push continuously)

### What happens under the hood?
1. **Extraction**: Uses ADB to pull the `memory_droid.db` and audio `.m4a` files directly from the app's internal storage.
2. **Transcription**: Scans the database for flashcards missing transcripts and runs the audio through Whisper.
3. **Refinement**: Sends those crude transcripts to your local Gemma 4 LM Studio server to clean up verbal stumbles.
4. **Syncing**: Packages the refined data into a JSON file, pushes it to your device's `Downloads` folder, and broadcasts an Intent. Memory Prime catches that intent in the background, ingesting the bulk transcripts flawlessly.
