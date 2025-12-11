# Chiaki with PoseTracker Integration

## Project Overview

This is **Chiaki v2.2.0** - a PlayStation Remote Play client for Android, modified with **PoseTracker AI** integration for pose detection and aim assistance using Google ML Kit.

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
│   │   │   │   ├── posetracker/    # PoseTracker integration
│   │   │   │   │   ├── PoseTrackerConfig.kt
│   │   │   │   │   ├── PoseTrackerOverlayView.kt
│   │   │   │   │   ├── PoseDetectorHelper.kt
│   │   │   │   │   └── PoseTrackerManager.kt
│   │   │   │   ├── stream/         # Streaming activity (modified)
│   │   │   │   ├── session/        # Session management (modified)
│   │   │   │   └── ...
│   │   │   ├── cpp/                # Native C/C++ code
│   │   │   └── res/                # Android resources
│   │   └── build.gradle            # Dependencies added
│   └── build.gradle
├── lib/                        # Core Chiaki library (C)
├── gui/                        # Qt GUI (not used for Android)
└── CMakeLists.txt
```

## PoseTracker Integration

### New Files Added (4 files)

| File | Description |
|------|-------------|
| `PoseTrackerConfig.kt` | Configuration data class with confidence, FOV radius, mask settings |
| `PoseTrackerOverlayView.kt` | Custom View for drawing detection boxes and focus circle |
| `PoseDetectorHelper.kt` | Google ML Kit pose detection wrapper |
| `PoseTrackerManager.kt` | Main manager coordinating detection and input injection |

### Modified Files

- `app/build.gradle` - Added ML Kit Pose Detection dependencies
- `stream/StreamActivity.kt` - Integrated PoseTracker initialization and toggle button
- `session/StreamInput.kt` - Added pose tracker movement injection to right stick
- `res/layout/activity_stream.xml` - Added overlay view and toggle button
- `res/values/strings.xml` - Added PoseTracker strings
- `res/xml/preferences.xml` - Added PoseTracker settings category
- `common/Preferences.kt` - Added PoseTracker preferences
- `proguard-rules.pro` - Added ML Kit ProGuard rules

### Dependencies Added

```gradle
implementation 'com.google.mlkit:pose-detection:18.0.0-beta3'
implementation 'com.google.mlkit:pose-detection-accurate:18.0.0-beta3'
```

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| Confidence | 0.28 | Minimum detection confidence |
| FOV Radius | 300px | Focus area around screen center |
| Visual Assist | true | Show detection boxes |
| Mask Enabled | true | Ignore HUD regions |

### Mask Area (for ignoring HUD)
- X: 0.0, Y: 0.27
- Width: 0.43, Height: 0.74

## Usage

1. **Enable in Settings**: Settings → PoseTracker AI → Enable
2. **Toggle during gameplay**: Tap the robot icon (top-right) to activate/deactivate
3. **Visual feedback**: Red boxes show detected poses, white circle shows focus area

## Technical Implementation

### Pose Detection Flow
1. ML Kit processes video frames in background thread
2. Detected poses are filtered by confidence and FOV radius
3. Best target (closest to center within FOV) is selected
4. Focus point (nose or head estimate) determines aim direction
5. Movement delta is calculated and injected to right stick

### Input Injection
- Movement is applied to `poseTrackerControllerState`
- Right stick values are updated based on distance from center
- Sensitivity factor (0.5) controls movement speed
- Active flag ensures clean state when disabled

## Recent Changes

- 2024-12: Initial PoseTracker AI integration using Google ML Kit
- 2024-12: Fixed frame capture - implemented PixelCopy for video frame extraction
- 2024-12: Fixed video rect coordinates - uses overlay-local coordinates via getLocationInWindow
- 2024-12: Fixed bitmap lifecycle - copy before processing, proper ownership transfer and recycling
- 2024-12: Fixed thread safety - dedicated HandlerThread for pose processing
- 2024-12: Made toggle button draggable with position persistence
- 2024-12: Added preference persistence for PoseTracker toggle state
- 2024-12: Fixed memory leaks - proper cleanup of layout listeners, handlers, and callbacks
- Original Chiaki v2.2.0 codebase preserved

## User Preferences

- Build system: GitHub Actions
- Target: Android APK
- No Replit-based execution required
