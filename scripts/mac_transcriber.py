#!/usr/bin/env python3
import os
import sqlite3
import subprocess
import json
import time
from importlib.util import find_spec

# Ensure requirements are installed
if find_spec("whisper") is None or find_spec("openai") is None:
    print("Please install requirements: pip install openai-whisper openai")
    exit(1)

import whisper
from openai import OpenAI

APP_PACKAGE = "com.jrochest.mp.debug"
DB_NAME = "memory_droid.db"
LOCAL_DIR = "Mac_MemoryPrimeData"
AUDIO_DIR = f"{LOCAL_DIR}/files/com.md.MemoryPrime/AudioMemo"
LOCAL_DB = f"{LOCAL_DIR}/files/com.md.MemoryPrime/{DB_NAME}"
OUTPUT_JSON = "update_transcripts.json"

def run_cmd(cmd: str):
    print(f"Running: {cmd}")
    subprocess.run(cmd, shell=True, check=True)

def pull_data_from_device():
    print("Pulling DB and audio files from device...")
    os.makedirs(LOCAL_DIR, exist_ok=True)
    # CD into local dir and extract tar from adb
    pull_cmd = f"cd {LOCAL_DIR} && adb shell run-as {APP_PACKAGE} tar cf - files/com.md.MemoryPrime | tar xf -"
    run_cmd(pull_cmd)
    if not os.path.exists(LOCAL_DB):
        raise FileNotFoundError(f"Failed to extract DB: {LOCAL_DB}")

def sanitize_audio_path(file_name: str) -> str:
    # Matches the Kotlin logic in AudioPlayer.kt
    # Removes .m4a
    basename = file_name.replace(".m4a", "")
    # Removes leading minus sign if any
    if basename.startswith("-"):
        basename = basename[1:]
    
    file_number = int(basename)
    which_dir = file_number % 100
    dir_str = f"{which_dir:02d}/"
    return os.path.join(AUDIO_DIR, dir_str, file_name)

def analyze_with_gemma(client: OpenAI, transcript: str) -> str:
    print("Sending to Gemma 4 (26B MoE) for analysis...")
    response = client.chat.completions.create(
        model="gemma-4-26b-moe",
        messages=[
            {"role": "system", "content": "You are a helpful assistant analyzing a recorded flashcard Q&A session. Return a polished, grammatically correct version of the transcript, fixing any obvious verbal stumbles or hallucinated artifact words. Only reply with the polished transcript."},
            {"role": "user", "content": f"The following is a raw audio transcript. Please refine it:\n\n{transcript}"}
        ],
        temperature=0.3,
    )
    return response.choices[0].message.content.strip()

def process_transcriptions():
    print("Loading Whisper Model...")
    audio_model = whisper.load_model("base")
    client = OpenAI(base_url="http://localhost:1234/v1", api_key="lm-studio")
    
    conn = sqlite3.connect(LOCAL_DB)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    # Grab notes where transcript is null
    cursor.execute('''
        SELECT _id, question, answer, question_transcript, answer_transcript 
        FROM notes 
        WHERE (question IS NOT NULL AND question != '' AND question_transcript IS NULL)
           OR (answer IS NOT NULL AND answer != '' AND answer_transcript IS NULL)
    ''')
    
    notes = cursor.fetchall()
    print(f"Found {len(notes)} notes needing transcription.")
    
    updates = []
    
    for note in notes:
        n_id = note['_id']
        question = note['question']
        answer = note['answer']
        
        update_obj = {"id": n_id}
        has_update = False
        now_ms = int(time.time() * 1000)
        
        try:
            if question and not note['question_transcript']:
                audio_path = sanitize_audio_path(question)
                if not audio_path.endswith(".m4a"):
                    audio_path += ".m4a"
                    
                if os.path.exists(audio_path):
                    print(f"Transcribing Question for Note ID {n_id}: {audio_path}")
                    result = audio_model.transcribe(audio_path)
                    raw_text = result['text']
                    if raw_text.strip():
                        print(f"  [Whisper Q]: {raw_text}")
                        refined_text = analyze_with_gemma(client, raw_text)
                        update_obj["questionTranscript"] = refined_text
                        update_obj["questionTranscriptConfidence"] = 1.0 # Pseudo confidence
                        update_obj["questionTranscriptModel"] = "gemma-4-26b-moe-mac"
                        update_obj["questionTranscriptGeneratedAt"] = now_ms
                        has_update = True
                        print(f"  [Gemma Q]  : {refined_text}")
                else:
                    print(f"Warning: Audio file not found: {audio_path}")
                    
            if answer and not note['answer_transcript']:
                audio_path = sanitize_audio_path(answer)
                if not audio_path.endswith(".m4a"):
                    audio_path += ".m4a"
                    
                if os.path.exists(audio_path):
                    print(f"Transcribing Answer for Note ID {n_id}: {audio_path}")
                    result = audio_model.transcribe(audio_path)
                    raw_text = result['text']
                    if raw_text.strip():
                        print(f"  [Whisper A]: {raw_text}")
                        refined_text = analyze_with_gemma(client, raw_text)
                        update_obj["answerTranscript"] = refined_text
                        update_obj["answerTranscriptConfidence"] = 1.0
                        update_obj["answerTranscriptModel"] = "gemma-4-26b-moe-mac"
                        update_obj["answerTranscriptGeneratedAt"] = now_ms
                        has_update = True
                        print(f"  [Gemma A]  : {refined_text}")
                else:
                    print(f"Warning: Audio file not found: {audio_path}")
        except KeyboardInterrupt:
            print("\nReceived stop signal! Saving progress so far...")
            if has_update:
                updates.append(update_obj)
            break
                
        if has_update:
            updates.append(update_obj)
            
    conn.close()
    
    if updates:
        with open(OUTPUT_JSON, "w") as f:
            json.dump(updates, f, indent=2)
        print(f"Saved {len(updates)} updates to {OUTPUT_JSON}.")
        return True
    else:
        print("No transcripts generated.")
        return False

def push_and_trigger():
    print("Pushing JSON to device...")
    device_path = f"/sdcard/Download/{OUTPUT_JSON}"
    run_cmd(f"adb push {OUTPUT_JSON} {device_path}")
    
    print("Triggering Android intent to ingest recordings...")
    intent_cmd = f'adb shell am broadcast -a com.md.IMPORT_TRANSCRIPTS --es filePath "{device_path}"'
    run_cmd(intent_cmd)
    print("Done!")

if __name__ == "__main__":
    import sys
    try:
        if len(sys.argv) > 1:
            cmd = sys.argv[1].lower()
            if cmd == "pull":
                pull_data_from_device()
            elif cmd == "transcribe":
                process_transcriptions()
            elif cmd == "push":
                push_and_trigger()
            elif cmd == "all":
                pull_data_from_device()
                if process_transcriptions():
                    push_and_trigger()
            else:
                print("Usage: python3 scripts/mac_transcriber.py [pull | transcribe | push | all]")
        else:
            # Default behavior: pull and transcribe, but do NOT push.
            pull_data_from_device()
            process_transcriptions()
            print("\nTranscription complete! Reconnect your device and run `python3 scripts/mac_transcriber.py push` to sync.")
    except Exception as e:
        print(f"Pipeline failed: {e}")
