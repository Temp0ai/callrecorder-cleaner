# Call Recorder Cleaner

An Android app that analyzes call recordings and deletes ones containing specific keywords like "Paytm" or "Sound Box".

## Features

- **Date Range Selection**: Filter recordings by date (e.g., Aug 1 - Sep 15, 2026)
- **Keyword Detection**: Automatically detects "Paytm", "Sound Box", "Soundbox" in audio
- **Offline Processing**: Uses Vosk for on-device speech recognition
- **Batch Delete**: Delete all matched recordings at once
- **Preview**: See which recordings matched before deleting

## How It Works

1. **Select Date Range**: Pick start and end dates
2. **Enter Keywords**: Default keywords are pre-filled (Paytm, Sound Box)
3. **Tap Scan**: App scans recordings in the date range
4. **Review**: See which recordings contain the keywords
5. **Delete**: Confirm and delete matched recordings

## Installation

### Build from Source

1. Open Android Studio
2. Import the project from `callrecorder-cleaner/` folder
3. Sync Gradle
4. Build APK: `Build > Build Bundle(s)/APK(s) > Build APK(s)`
5. Install on your phone

### Quick Build (Command Line)

```bash
cd callrecorder-cleaner
./gradlew assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

## Setup

1. **Install Vosk Model** (first time only):
   - Download the small English model from https://alphacephei.com/vosk/models
   - Extract to: `Internal Storage/vosk-model-small-en-us-0.15/`

2. **Grant Permissions**:
   - Storage access (to read/delete recordings)
   - Audio recording permission

3. **Recordings Location**:
   The app looks for recordings in:
   - `/Recordings/Call/`
   - `/DCIM/Recordings/Call/`
   - `/Sounds/Call recordings/`

## Supported Audio Formats

- M4A (most Android call recorders)
- MP3
- WAV
- AMR
- 3GP
- OGG

## Filename Format Support

The app parses dates from filenames like:
- `Call Name_260815_143022.m4a` → Aug 15, 2026
- `Recording_20260815_1430.wav` → Aug 15, 2026

If filename doesn't match, it uses file modification date.

## Technical Details

- **Language**: Kotlin
- **Min SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)
- **Speech Recognition**: Vosk (offline)
- **Audio Processing**: Android MediaCodec

## Privacy

- All processing happens on-device
- No internet required after model download
- No data is sent to any server

## Troubleshooting

**"No recordings found"**
- Check if recordings exist in the expected folder
- Try different date range
- Verify storage permissions are granted

**"Scan takes long time"**
- Large recordings take longer to process
- Only scans recordings in selected date range
- Progress is shown during scanning

**"Keywords not detected"**
- Ensure Vosk model is installed correctly
- Check audio quality of recording
- Try variations of keywords (e.g., "paytm", "Paytm", "PAYTM")

## License

MIT License
