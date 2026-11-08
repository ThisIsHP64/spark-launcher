# Spark Launcher

A fast, clean Android home screen in the spirit of the Pixel launcher, with the customization power users actually want. Free and open source under the GPLv3.

## Privacy promise

- **No internet permission.** Spark cannot send anything off your device. A unit test fails the build if the permission ever appears in the merged manifest.
- No ads, analytics, tracking, crash reporters, Google Play Services, paywalls, or AI features.
- Optional permissions (contacts in search, notification access for dots, the gesture accessibility service) are only requested when you enable the feature that needs them.
- Cloud backup is disabled.

## Features

- Material You theming from your wallpaper or a custom seed color, with scheme variants
- Slide-up app drawer **or** an all-apps-on-home layout, switchable at any time
- Custom home, dock and drawer grid sizes, with optional half cell (subgrid) positioning
- Icon shapes, a corner radius slider, themed (monochrome) icons and icon pack support
- Nova-style widget control: free resizing, padding, overlap and corner rounding
- Folders, hidden apps, renaming, app shortcuts, gestures, notification dots, work profile and private space support

## App animations

- **Opening an app** uses the system's default animation. Android 17 ignores launch animations requested by third party launchers, so Spark does not fake one.
- **Swiping home** shrinks the app back into its icon and crossfades into it, using the same gesture handshake the system offers Nova and Niagara on Pixel phones. This only works for icons on the visible home page or dock; other apps use the system's default close animation.

## Building

Requirements: JDK 21 and the Android SDK (platform 37).

```
./gradlew assembleDebug
./gradlew testDebugUnitTest lint
```

If your default JDK is newer than 21, point Gradle at JDK 21 with `org.gradle.java.home` in your user-level `~/.gradle/gradle.properties`.

## Module layout

| Module | Purpose |
| --- | --- |
| `app` | launcher activity, dependency graph, manifest |
| `core:model` | plain kotlin domain types and grid logic |
| `core:data` | room database, settings store, repositories |
| `core:apps` | installed apps, profiles and shortcuts via `LauncherApps` |
| `core:icons` | icon loading, shapes, themed icons, icon packs |
| `core:theme` | material 3 color schemes |
| `core:ui` | shared compose components |
| `feature:*` | workspace, drawer, widgets, search, settings |

## License

Copyright (C) 2026 Spark Launcher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).
