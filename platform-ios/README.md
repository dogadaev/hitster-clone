# platform-ios

Native iOS integration surface for the shared Hitster MVP.

This folder is intentionally thin in the initial implementation:

- `NativePlaybackBridge.swift` defines the Spotify-facing playback contract.
- `ScreenWakeController.swift` isolates screen-awake behavior.
- `LocalSessionCoordinator.swift` outlines the host/join lifecycle for local sessions.

The shared Kotlin rules, playlist parsing, and protocol DTOs live in the Gradle modules at the repository root. An eventual Xcode target should consume those shared contracts and keep all Apple SDK dependencies inside this folder.

