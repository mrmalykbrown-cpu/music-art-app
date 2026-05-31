# MusicLockArt

An Apple Music–inspired lock screen experience for Android (Samsung-focused, minSdk 31).

The album art of whatever you're playing (Spotify, Apple Music, YT Music, etc.) is
pushed to your lock screen wallpaper as a blurred backdrop, and a custom overlay card
shows the crisp art, title, artist, a Ken Burns animation, and the connected Bluetooth
device.

## How it works
- **MediaNotificationListener** — a `NotificationListenerService` that reads the active
  `MediaSession` to get title/artist/album-art, and pushes a blurred copy to
  `WallpaperManager.FLAG_LOCK`.
- **LockOverlayActivity** — drawn over the lock screen (`showOnLockScreen=true`) with a
  live `RenderEffect` blur and Ken Burns animation.
- **BluetoothWatcher** — reports the connected A2DP audio device name.
- **NowPlaying** — a `StateFlow` singleton shared between the service and the UI.

## Building
No Android Studio needed. Push to GitHub and the included Actions workflow
(`.github/workflows/build.yml`) builds a debug APK. Download it from the workflow run's
**Artifacts** section, then install on your phone (enable "install unknown apps").

## Setup on device
1. Open the app, tap **Grant notification access** and enable MusicLockArt.
2. Tap **Grant Bluetooth & notification permissions**.
3. Play a song, then lock your phone.
