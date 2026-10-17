<div align="center">

<img src="docs/banner.png" width="600" alt="Zani">

Screen recorder for Android. The button floats above your game.

**[Download APK](../../releases/latest)**

Android 8+ - 214 KB - no ads - no internet

<img src="docs/melayang.jpg" width="240" alt="Floating button above game">
<img src="docs/beranda.jpg" width="240" alt="Home">
<img src="docs/setelan.jpg" width="240" alt="Settings">

</div>

## Features

| | |
|---|---|
| **Record** | Start and stop recording from the floating button, without leaving your game. |
| **Pause** | Hold the recording for a moment, continue in the same file. |
| **Screenshot** | Capture the screen anytime from the same button. |

## Install

1. Download the latest APK on the [Releases](../../releases) page.
2. Open the APK file and allow installation from this source.
3. Open Zani, the floating button is ready to use.

## Permissions

| Permission | Why Zani needs it |
|---|---|
| `SYSTEM_ALERT_WINDOW` | The floating record button. It stays above your game, so Zani never has to switch apps. This permission is granted by you through a system screen, not silently. |
| `RECORD_AUDIO` | Recording sound. Requested only when you turn on a microphone or internal audio. Audio is optional: recording works fully without it. |
| `POST_NOTIFICATIONS` | Android 13+ requires it so the ongoing recording notification stays visible. Android 12 and lower do not need it. |
| `FOREGROUND_SERVICE` with type `mediaProjection` | Recording continues while you use other apps, so the service must be foreground with the screen-projection type. |
| `FOREGROUND_SERVICE` with type `microphone` | Added at runtime only when the microphone permission is granted and an audio mode is active. |
| `FOREGROUND_SERVICE` with type `specialUse` | Used by the floating button overlay. |

## Limitations

- Zani cannot record while the screen is off. Screen recording uses MediaProjection, which the system can end at any time.
- Internal audio (capturing game sound without the microphone) works on Android 10 and newer only, and still requires the microphone permission.
- Not tested on every phone. Some devices restrict screen recording, overlay drawing, or audio capture. Please report issues with your Android version and model.
- High frame rates such as 120 fps depend on the device GPU and encoder; Zani exposes them but cannot guarantee they are available everywhere.

## Privacy

- No internet permission. Zani never sends anything anywhere.
- No ads and no analytics.
- Recordings stay in `Movies/Zani`, screenshots in `Pictures/Zani`, both on your device only.
- The crash log (`crash.txt` in the app files folder) is written on your device only, is never uploaded, and can be deleted any time.
