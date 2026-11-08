<p align="center">
  <img src="art/spark-launcher-icon.svg" alt="The Spark Launcher icon: a white ring open toward the upper right with a spark leaving through the opening, on a flame gradient" width="128" height="128">
</p>

<h1 align="center">Spark Launcher</h1>

<p align="center"><strong>A fast, private home screen. Free and open source.</strong></p>

Spark Launcher is my take on the perfect Android launcher: fast and clean in the spirit of the Pixel launcher, with the customization users want, and no internet access, ads, or AI features at all. It is free and open source for life under the GPLv3 or later.

## Privacy promise

- **No internet permission.** Spark cannot send anything off your device. A unit test fails the build if the internet permission, or any of a few other tracking related permissions, ever appear in the merged manifest.
- No ads, analytics, tracking, crash reporters, Google Play Services, paywalls, or AI features.
- Optional access (contacts in search, notification access for dots, and the gesture accessibility service) is only requested when you enable the feature that needs it.
- Notification dots only check which apps have notifications. Spark never reads or keeps what they say. The accessibility service is configured so it cannot read the screen.
- The only other permissions are two normal ones granted at install. One lets a gesture open the notification shade, and the other hands uninstalls to the system's own confirmation screen.

## Features

### Home screen

- Multiple home pages holding apps, shortcuts, folders, and widgets, with a dock of up to seven icons
- A slide-up app drawer **or** an all-apps-on-home layout, switchable at any time. Each layout is kept separately, so switching back restores the other one
- Custom home grids from 3 to 8 columns and 3 to 9 rows, drawer grids from 3 to 7 columns, icon sizes from 70 to 130 percent, and labels that can be hidden or wrapped onto two lines
- Optional half cell (subgrid) positioning, so icons and widgets can sit between grid cells
- Folders with multiple pages, inline renaming, and drag and drop in and out
- Long press menus with app shortcuts, rename, change icon, app info, uninstall, and remove, plus a menu on empty space for widgets, wallpaper, and Spark Settings
- Notification dots that follow each app's own notification settings
- Apps can pin their shortcuts and widgets to your home screen, and you confirm each one first
- A starter dock built from your default phone, browser, and camera apps
- Apps from other profiles on your phone, such as a work profile or private space, appear alongside your other apps with the system's profile badge, and they keep their spots on home while a profile is paused

### Widgets

- A searchable widget picker with previews, including widgets from apps that have no launcher icon
- Free resizing on the grid, plus reconfiguring widgets that support it
- Per widget options: padding, corner rounding, opacity, overlap, and ignoring a widget's own size limits

### App drawer and search

- A drawer that blurs the home screen and wallpaper behind it, when the system allows blur
- Hide apps from the drawer and show them again at any time
- Search across apps, app shortcuts such as "New message", system settings screens such as Wi-Fi or Battery, and contacts
- A built-in calculator in search, with tap to copy
- Every search source runs on your phone. App shortcuts, system settings, and contacts can each be switched off, and contacts are off until you turn them on

### Colors and icons

- Material You theming from your wallpaper or a custom accent color
- Light, dark, or system theme, with five palette styles: Tonal spot, Vibrant, Expressive, Fidelity, and Monochrome
- Icon shapes: circle, squircle, rounded with a corner radius slider, square, and teardrop
- Themed (monochrome) icons, with an option to generate themed icons for apps that do not ship one
- Nova and ADW icon pack support, with per app icon picking from any installed pack
- Renaming for any app or folder

### Gestures

- Six configurable gestures: swipe up, swipe down, two finger swipe up, two finger swipe down, double tap on empty space, and the home button while already on the first page
- Each gesture can open the app drawer, open search, open notifications, open quick settings, lock the screen, show recent apps, open any app you choose, open Spark Settings, or do nothing
- Locking the screen and showing recent apps work through an accessibility service that you turn on yourself

### Accessibility

- Home screen items can be moved, and widgets resized, with screen reader actions instead of drag gestures
- Pages announce their position, such as "Page 1 of 3"

## App animations

- **Opening an app** uses the system's default animation. Android ignores custom launch animations from third party launchers, so Spark does not fake one. On Android 13 and newer, Spark asks for the app's icon on its splash screen.
- **Swiping home** shrinks the app back into its icon and crossfades into it, using the gesture handshake Pixel phones offer to third party launchers. This only works for icons on the visible home page or dock. Other apps use the system's default close animation.

## Building

Requirements: JDK 21 and the Android SDK (platform 37). Spark runs on Android 12 (API 31) and newer.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest lint
./gradlew assembleRelease
```

The debug build installs as `dev.gearturner.sparklauncher.debug`, so it sits beside a release install. Release builds are minified and stay unsigned unless you set the `SPARK_KEYSTORE_FILE`, `SPARK_STORE_PASSWORD`, and `SPARK_KEY_ALIAS` environment variables.

If your default JDK is newer than 21, point Gradle at JDK 21 with `org.gradle.java.home` in your user-level `~/.gradle/gradle.properties`.

## Module layout

Spark is written in Kotlin with Jetpack Compose and Material 3, and uses Hilt, Room, and DataStore.

| Module | Purpose |
| --- | --- |
| `app` | Launcher activity, dependency graph, and manifest |
| `build-logic` | Gradle convention plugins shared by every module |
| `core:model` | Plain Kotlin domain types and grid logic |
| `core:data` | Room database, settings store, and repositories |
| `core:apps` | Installed apps, profiles, and shortcuts via `LauncherApps` |
| `core:icons` | Icon loading, shapes, themed icons, and icon packs |
| `core:theme` | Material 3 color schemes |
| `core:ui` | Shared Compose components |
| `feature:*` | Workspace, drawer, widgets, search, and settings |

## Development

I built Spark Launcher with the assistance of Claude Code. If you do not want to use Spark because of this, I entirely understand. I thoroughly tested every feature by hand before adding it. The app itself contains no AI features and never connects to any AI service.

## License

Copyright (C) 2026 Hunter Pageau and Spark Launcher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).

The libraries Spark is built on are licensed under Apache 2.0 and MIT, and their licenses are listed in the app under Settings > About.
