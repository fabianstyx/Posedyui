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
│   │   │   │   │   ├── PoseTrackerConfig.kt       # All config options
│   │   │   │   │   ├── PoseTrackerSettings.kt     # Persistence layer
│   │   │   │   │   ├── PoseTrackerOverlayView.kt  # Visual overlay
│   │   │   │   │   ├── PoseDetectorHelper.kt      # ML Kit wrapper
│   │   │   │   │   └── PoseTrackerManager.kt      # Main controller
│   │   │   │   ├── stream/         # Streaming activity
│   │   │   │   ├── session/        # Session management
│   │   │   │   └── settings/       # Settings UI
│   │   │   ├── cpp/                # Native C/C++ code
│   │   │   └── res/                # Android resources
│   │   └── build.gradle            # Dependencies
│   └── build.gradle
├── lib/                        # Core Chiaki library (C)
├── gui/                        # Qt GUI (not used for Android)
└── CMakeLists.txt
```

## PoseTracker Integration

### Core Files (5 files)

| File | Description |
|------|-------------|
| `PoseTrackerConfig.kt` | Configuration data class with all settings |
| `PoseTrackerSettings.kt` | SharedPreferences persistence layer |
| `PoseTrackerOverlayView.kt` | Custom View for drawing overlays |
| `PoseDetectorHelper.kt` | Google ML Kit pose detection wrapper |
| `PoseTrackerManager.kt` | Main manager with triggerbot and aim logic |

### Dependencies

```gradle
implementation 'com.google.mlkit:pose-detection:18.0.0-beta3'
implementation 'com.google.mlkit:pose-detection-accurate:18.0.0-beta3'
```

## Configuration Options (All in Settings)

### Core Settings
| Option | Default | Range | Description |
|--------|---------|-------|-------------|
| Enable PoseTracker | Off | On/Off | Master enable switch |
| Confidence | 28% | 10-100% | Detection confidence threshold |
| FOV Radius | 300px | 50-600px | Only track targets within this radius |

### Visual Settings
| Option | Default | Description |
|--------|---------|-------------|
| Visual Assist | On | Show all overlays |
| Show FOV Circle | On | Display focus area radius |
| Show Bounding Boxes | On | Display detection boxes around targets |
| Show Focus Labels | On | Display "focus" label on target |

### TriggerBot Settings
| Option | Default | Range | Description |
|--------|---------|-------|-------------|
| Enable TriggerBot | Off | On/Off | Auto-fire when target in crosshair |
| TriggerBot Delay | 50ms | 0-500ms | Delay before firing |
| Fire Hold Time | 100ms | 10-500ms | How long to hold fire |
| Auto-Fire Mode | Off | On/Off | Continuously fire while locked |
| Auto-Fire Rate | 100ms | 50-500ms | Time between auto-fire shots |

### Aim Settings
| Option | Default | Range | Description |
|--------|---------|-------|-------------|
| Aim Smoothing | 50% | 0-100% | Smooth aim movement (higher = slower) |
| Aim Speed | 100% | 10-200% | Speed multiplier (100 = normal) |
| Aim Assist Strength | 80% | 0-100% | How strongly to pull toward target |
| Snap to Target | Off | On/Off | Instant snap when close |
| Snap Threshold | 50px | 10-200px | Distance for snap activation |

### Activation Settings
| Option | Default | Options | Description |
|--------|---------|---------|-------------|
| Activation Mode | Toggle | Toggle/Hold/Always On | How tracking is activated |
| Controller Button | LT | LT/RT/LB/RB/L3/R3/A/B/X/Y | Button to activate |

### Target Settings
| Option | Default | Description |
|--------|---------|-------------|
| Target Priority | Closest to Center | How to select when multiple targets |
| Headshot Mode | On | Aim for head instead of center mass |
| Head Offset | 7% | Vertical offset for headshot aim |

### HUD Mask (Ignore Game UI)
| Option | Default | Description |
|--------|---------|-------------|
| Enable Mask | On | Ignore UI elements during detection |
| Show Mask | Off | Visualize the masked area |
| Mask X | 0% | Horizontal position |
| Mask Y | 27% | Vertical position |
| Mask Width | 43% | Width of mask area |
| Mask Height | 74% | Height of mask area |

### Advanced Settings
| Option | Default | Range | Description |
|--------|---------|-------|-------------|
| Processing Interval | 33ms | 16-100ms | Time between frame processing |
| Max Targets | 5 | 1-10 | Maximum targets to track |
| Predictive Aiming | Off | On/Off | Predict target movement |
| Prediction Strength | 30% | 0-100% | How far ahead to predict |

## Technical Implementation

### Pose Detection Flow
1. ML Kit processes video frames at configured interval
2. Detected poses are filtered by confidence threshold
3. HUD mask is applied to ignore UI elements
4. **FOV Filtering**: Only poses within fovRadius are considered
5. Best target is selected based on priority setting
6. Focus point (nose or head estimate) determines aim direction
7. Smoothing and aim speed are applied
8. Movement delta is injected to controller

### TriggerBot Flow
1. When crosshair enters target bounding box, timer starts
2. After configured delay, fire button is pressed
3. Fire is held for configured hold time
4. In auto-fire mode, continues firing at configured rate
5. When crosshair leaves target, firing stops

### Settings Persistence
- Uses Android `PreferenceManager.getDefaultSharedPreferences()`
- Settings sync automatically via `OnSharedPreferenceChangeListener`
- SeekBar values stored as integers (0-100), converted to floats in code

## Usage

1. **Enable in Settings**: Settings → PoseTracker AI → Enable
2. **Configure options**: Adjust settings to your preference
3. **Toggle during gameplay**: Use configured button or tap robot icon
4. **Visual feedback**: Red boxes show detected poses, white circle shows FOV

## Recent Changes

- 2024-12: Added comprehensive PoseTracker settings section
- 2024-12: Implemented TriggerBot with delay, hold time, auto-fire
- 2024-12: Added aim smoothing, speed, and assist strength controls
- 2024-12: Added FOV-limited tracking (only within fovRadius)
- 2024-12: Added predictive aiming for moving targets
- 2024-12: Added snap-to-target functionality
- 2024-12: Added HUD masking to ignore game UI
- 2024-12: Added settings persistence with auto-reload
- 2024-12: Original PoseTracker AI integration using Google ML Kit

## User Preferences

- Build system: GitHub Actions
- Target: Android APK
- No Replit-based execution required
