# Hitster Clone

Multiplayer music-timeline party game inspired by Hitster.

The current project shape is:
- Android app for host and guest play
- guest-only web build using the same shared libGDX client
- authoritative local-network host model
- Spotify playback on the Android host

## What The Game Does

Each player uses their own device and normally sees only their own timeline.

Core loop:
1. One Android device hosts the match.
2. Other players join from Android or from the guest-only web build.
3. Every player starts with one revealed seeded card.
4. On your turn, you draw a hidden card and the host starts Spotify playback.
5. You place that card into your personal timeline and end the turn.
6. The host validates the chronological placement and broadcasts the result.
7. Doubt and coin rules are supported, including temporary doubt placement and successful card steals.
8. The first player to reach 10 revealed cards wins.

## Modules

- `core-model`: immutable match, player, timeline, and playlist models
- `core-game`: authoritative reducer, turn rules, validation, and coin/doubt flow
- `playback-api`: shared playback abstraction used by the host flow
- `playlist-data`: JSON playlist parsing and validation
- `networking`: transport DTOs and state mappers
- `transport-jvm`: LAN hosting, guest transport, browser guest session routes
- `animations`: shared animation specs
- `ui`: shared libGDX presentation, interaction, and controllers
- `platform-android`: Android activity, Spotify integration, LAN host, QR join panel, hosted web server
- `platform-web`: TeaVM guest build, local helper server entry point, and browser guest transport

## Architecture

The project is intentionally split so core gameplay does not depend on libGDX rendering or platform SDKs.

- Host authority lives in `core-game`
- Shared synchronized state lives in `core-model`
- Transport schemas live in `networking`
- Platform SDK integration stays in platform modules
- The browser guest is not a separate app; it reuses the same shared `ui` code

The Android host can serve the guest web build itself over the local network, so guests can join directly from a browser on the same Wi-Fi.

## Requirements

- macOS or Linux shell environment
- JDK 21 for Gradle tasks
- Android SDK / adb for Android builds
- Spotify app installed on the Android host device for real playback

## Local Configuration

Spotify credentials are read from ignored local developer config. Add these to `local.properties`:

```properties
spotifyClientId=YOUR_SPOTIFY_CLIENT_ID
spotifyRedirectUri=hitsterclone://spotify-auth-callback
```

The Android app package used for Spotify setup is:
- `com.hitster.platform.android`

## Useful Gradle Tasks

Build and test the shared project:

```bash
./gradlew :core-game:test :networking:test :ui:test :platform-web:test :platform-android:assembleDebug
```

Build the TeaVM guest bundle:

```bash
./gradlew :platform-web:buildWebDist
```

Run the desktop helper server for the guest web build:

```bash
./gradlew :platform-web:run
```

Install the Android debug build:

```bash
./gradlew :platform-android:assembleDebug
adb install -r platform-android/build/outputs/apk/debug/platform-android-debug.apk
```

## Joining A Hosted Match

Current host/join flow:
- choose `HOST` or `GUEST`
- hosts pair Spotify from the lobby before starting
- the host lobby shows a QR code that points to the phone's raw LAN IP
- browser guests can open the phone-hosted guest page directly from that QR code
- guests enter a name before joining the lobby

The Android host also advertises a `melonman.local` mDNS alias, but the most reliable browser entry is still the raw IP URL shown in the host lobby QR code.

## Playlist Data

The bundled deck lives in:
- `ui/src/main/resources/sample-playlist.json`

It contains real Spotify track URIs intended for actual playback.

## Verification

The project currently has coverage around:
- reducer turn flow
- timeline placement validation
- deck behavior
- playlist parsing
- transport serialization
- browser guest transport helpers
- UI layout math and presenter/controller behavior

This README was refreshed against the current codebase and current build flow, not the original MVP scaffold.
