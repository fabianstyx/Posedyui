# Chiaki with PoseTracker Integration

## Project Overview

This is **Chiaki v2.2.0** - a PlayStation Remote Play client for Android, modified with **PoseTracker AI** integration for pose detection and aim assistance.

**Important**: This is an Android APK project that builds on **GitHub Actions**, not on Replit. Replit is used here for code editing only.

## Build System

- **Platform**: Android (APK)
- **Build Tool**: Gradle with CMake for native code
- **Build Environment**: GitHub Actions CI/CD
- **Language**: Kotlin + Java + C/C++ (NDK)

## How to Build

The APK is built via GitHub Actions. Push changes to trigger the CI pipeline:

```bash
git add .
git commit -m "Your changes"
git push
```

The GitHub Actions workflow will:
1. Set up Android SDK and NDK
2. Initialize git submodules
3. Build the APK with CMake and Gradle

## Project Structure

```
chiaki-v2.2.0/
├── android/                    # Android app source
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/metallic/chiaki/
│   │   │   │   ├── posetracker/    # NEW: PoseTracker integration
│   │   │   │   ├── stream/         # Streaming activity
│   │   │   │   ├── session/        # Session management
│   │   │   │   └── ...
│   │   │   ├── cpp/                # Native C/C++ code
│   │   │   └── res/                # Android resources
│   │   └── build.gradle
│   └── build.gradle
├── lib/                        # Core Chiaki library (C)
├── gui/                        # Qt GUI (not used for Android)
└── CMakeLists.txt
```

## PoseTracker Integration

### New Files Added

- `posetracker/PoseTrackerConfig.kt` - Configuration data class
- `posetracker/PoseTrackerOverlayView.kt` - Visual overlay for detection display
- `posetracker/PoseDetectorHelper.kt` - TensorFlow Lite pose detection
- `posetracker/PoseTrackerManager.kt` - Main manager class

### Modified Files

- `app/build.gradle` - Added TensorFlow Lite dependencies
- `stream/StreamActivity.kt` - Integrated PoseTracker initialization
- `session/StreamInput.kt` - Added pose tracker movement injection
- `res/layout/activity_stream.xml` - Added overlay and toggle button
- `res/values/strings.xml` - Added PoseTracker strings
- `res/xml/preferences.xml` - Added PoseTracker settings
- `common/Preferences.kt` - Added PoseTracker preferences
- `proguard-rules.pro` - Added TensorFlow Lite rules

### Dependencies Added

```gradle
implementation 'org.tensorflow:tensorflow-lite:2.14.0'
implementation 'org.tensorflow:tensorflow-lite-gpu:2.14.0'
implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
implementation 'org.tensorflow:tensorflow-lite-task-vision:0.4.4'
```

### Usage

1. Enable PoseTracker in Settings → PoseTracker AI
2. During streaming, tap the robot icon to toggle tracking
3. The AI will detect human poses and assist with aiming

## Configuration Options

- **Detection Confidence**: 0.28 (default)
- **FOV Radius**: 300px (focus area)
- **Visual Assist**: Show/hide detection boxes
- **Mask Area**: Ignore HUD regions

## Technical Notes

- Uses TensorFlow Lite MoveNet for pose detection
- Overlay renders on top of the video stream
- Movement is injected via right stick emulation
- Feature is gated behind settings preference

## Recent Changes

- 2024: Added PoseTracker AI integration using TensorFlow Lite
- Original Chiaki v2.2.0 codebase preserved

## User Preferences

- Build system: GitHub Actions
- Target: Android APK
- No Replit-based execution required
