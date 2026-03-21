# AGENTS.md
# Author: Vladyslav Dohadaiev

## Project Overview
This project is a multiplayer mobile party game inspired by the physical table-top card music game "Hitster".

Each player uses their own mobile device as a replacement for the physical deck interaction and for placing virtual cards in front of themselves on a personal timeline.

The goal is to reproduce the physical personal-play experience as closely as practical:
- draw a card
- start music playback for that card
- place the card on the player's own timeline
- end the turn
- validate the placement
- synchronize the result to all players

## Product Model
This is not a shared-board game UI.

Each player has their own personal UI on their own device.
A device should normally display only:
- that player's own timeline
- that player's current interactive card
- shared match information relevant to everyone

Do not design the game as:
- a split-screen experience
- a shared table view on one device
- a UI that shows all players' timelines at once during normal gameplay

The mobile app is intended to replace:
- the physical deck of cards
- the row of cards a player places in front of themselves

## Primary Technical Direction
Build the first playable MVP around:
- Android for host and guest play
- a guest-only web build using the same libGDX game code

Use:
- Kotlin as the main language
- libGDX as the primary game framework
- platform-specific native integrations where libGDX alone is not sufficient

The browser build is a guest-only extension of the same libGDX app, not a separate web-specific game client.

## Development Priorities
Prioritize:
- correctness of multiplayer synchronization
- smooth drag-and-drop interactions
- clean personal timeline UX
- polished animations and modern presentation
- modular architecture with clear separation of concerns
- maintainable code with testable game logic

Do not prioritize first:
- desktop support
- dedicated remote servers
- Bluetooth play
- accounts
- public matchmaking
- cloud saves

These are future extensions, not MVP requirements.

## Core Architectural Principles
Keep the project split into:
- shared game logic
- shared domain models
- shared networking protocol definitions
- shared playlist parsing and validation
- shared UI / rendering logic where practical
- platform-specific integration layers

The shared code must contain:
- game rules
- turn logic
- timeline validation
- playlist loading and parsing
- domain models
- networking DTOs / protocol messages
- playback abstractions

Platform-specific modules must contain:
- Spotify integration
- native permissions
- screen wake handling
- lifecycle handling
- deep links / app handoff
- local-network-specific platform code if needed

Do not place platform SDK logic inside core game-rule code.

## Tech Stack
Use:
- Kotlin
- libGDX
- trusted open-source libraries where needed

Allowed additional libraries include categories such as:
- serialization
- networking
- testing
- logging
- dependency injection if there is clear benefit

Prefer:
- small
- proven
- well-maintained dependencies

Do not introduce heavy dependencies without clear justification.

## Target Platforms
Primary targets:
- Android phones for host and guest play
- modern desktop or mobile browsers for guest-only play

The app must be designed for landscape orientation.
Do not treat portrait mode as part of the core experience.

Tablet support is welcome if it does not significantly complicate the implementation.

The first development milestone should focus on Android phones, while keeping the guest-only web build functional for lightweight join flows.

## Orientation Rule
The app must use landscape orientation during gameplay.

All core gameplay screens must be designed for landscape.
On Android, both landscape directions should be supported instead of locking the app to only one horizontal rotation.
Portrait mode is not required for MVP and should not drive layout decisions.

## Multiplayer Model
The game is multiplayer.

For MVP:
- one player creates a session and acts as the host
- the host is the authoritative source of truth for shared game state
- other players join that session over the local network
- all important game state changes are validated by the host
- the lobby player list and advertised player count must track currently connected guests in real time; guests who disconnect in the lobby should be removed from the authoritative lobby state

The architecture must allow future support for:
- dedicated remote server mode
- internet multiplayer
- Bluetooth-based transport if technically viable

However, only local-session hosting is required for the first MVP.
The host flow must run on Android.
Guests may join either from another Android device or from the guest-only web build.
The Android host should serve the guest-only web build itself over the local network so guests do not depend on a separate laptop-hosted helper server.
The Android host lobby should expose a scannable raw-IP browser join path, such as a QR code and visible IP URL, rather than relying only on mDNS aliases.
An Android host must keep its local-session server discoverable and able to accept guest joins while the app is backgrounded, using foreground-safe platform hosting where needed.
Authoritative host networking and command handling must not depend on the libGDX render loop being actively resumed.
Guest reconnects must preserve the same player identity so a temporarily disconnected player can safely reattach to an in-progress match instead of being treated as a new player.
The guest-only web build must remain touch-usable on mobile browsers, including iOS Safari and Android Chrome, rather than assuming desktop mouse-only interaction.
The guest-only web build must size itself to the visible mobile browser viewport, respect safe-area insets, and render crisply on high-DPI screens instead of relying on raw `100vh` / `100vw` assumptions.
When browser capabilities allow it, or when a safe local fallback is available, the guest-only web build should keep the screen awake during active play.

## Shared State vs Local UI State
Shared synchronized state includes:
- player list
- current turn
- deck progression
- current drawn card identity
- validation results
- score / progress / win state
- each player's authoritative timeline state

Local per-device presentation state includes:
- drag state
- temporary animation state
- local visual layout state
- transient UI state
- loading indicators
- playback status presentation

A player's personal timeline is part of the authoritative match state, but it should normally be rendered only on that player's device.

## Privacy / Visibility Rule
A player's personal timeline should not be shown to other players during normal gameplay unless the game rules explicitly require reveal or result display.

Do not build the normal game flow around full visibility of every player's cards on every device.

## Spotify Integration
Spotify integration is a core feature.

Requirements:
- drawing a card should trigger playback for the associated track
- ending the turn should pause playback
- playback integration must be abstracted behind an interface
- core game logic must not depend directly on Spotify SDK classes
- the host must complete playback pairing from the lobby before starting the match
- guests should not be blocked by the host-only playback pairing step

Implementation direction:
- Android uses a native Spotify integration layer
- Android host pairing should use an explicit app-initiated Spotify authorization flow before connecting App Remote, rather than relying on the App Remote auth view alone
- guests, including web guests, must not require Spotify pairing
- shared game code communicates only through an abstract playback interface

The app must handle these cases gracefully:
- Spotify app is not installed
- the user is not authenticated
- playback cannot be started
- the track reference is missing or invalid
- platform or account limitations prevent requested playback behavior

Do not hardcode Spotify-specific logic into core rules.

## Playlist Data
Playlist data is loaded from JSON.

The JSON source may be:
- bundled locally with the app
- imported by the user
- loaded from a remote URL in the future

Each playlist entry should contain at minimum:
- unique id
- track title
- artist
- release year
- Spotify identifier or playback reference
- optional cover image URL
- optional extra metadata

The parser must:
- validate input
- reject malformed entries
- produce user-friendly errors
- remain independent from the UI layer

Bundled or developer-facing sample playlists that are intended to exercise playback must use real playable Spotify track URIs.
Do not leave shipped sample data on placeholder values such as `spotify:track:track-01`.

## Core Gameplay Requirements
The gameplay loop should mirror the physical game as closely as practical.

Turn flow:
- the host-created deck must be shuffled per session before the match starts; do not ship a fixed deterministic draw order outside explicit tests
- each player begins the match with one random revealed card already placed on their own timeline
- that seeded opening card counts toward the player's score / progress, so each player starts at 1
- the host must be able to manually award or remove coins for players, because the song-name / artist-name bonus check is judged socially rather than by the app
- the active player draws a card from the deck on their own device using drag and drop
- drawing the card starts playback for the associated track
- the player must not see song-identifying details such as title, artist, or release year before ending the turn
- once a card is revealed on the timeline, it should show its song title, artist, and release year
- the player drags the card to a position on their own timeline
- the player must be able to place the card:
  - between two existing cards
  - to the far left of all existing cards
  - to the far right of all existing cards
- the first player to complete a timeline of 10 revealed cards wins the match
- when released, the card snaps to the nearest valid timeline slot
- after insertion, all cards in the timeline must automatically adjust their positions to make space for the new card
- the timeline must visually fit up to 10 cards; committed cards may overlap slightly when compressed, but the hidden in-turn card must never overlap adjacent cards
- after insertion or rearrangement, the full set of cards in the player's timeline must remain visually centered as a group
- waiting players with at least one coin may arm a single active doubt before reveal; only one doubt may be armed at a time
- the player confirms the move using an end-turn button
- ending the turn pauses playback
- if a doubt is armed, the active player's turn pauses after their placement and the doubting player receives a temporary isolated placement UI for that same hidden card against the target timeline, without mutating the target timeline directly
- a successful doubt on an otherwise wrong placement spends one coin and steals the card into the doubter's own timeline; in all other doubt outcomes the coin is still spent and the original turn resolves normally
- the host validates whether the placement is chronologically correct
- the result is synchronized to all players
- the match continues with updated shared state

## Timeline Layout Rules
The player's timeline must behave like an ordered sequence with dynamic insertion.

Requirements:
- cards must support insertion at any valid index
- inserting a card must shift neighboring cards smoothly to their new positions
- the layout must recalculate after every insertion, removal, or reveal that affects spacing
- the timeline should remain centered as a whole, not anchored only from the left
- spacing between cards should remain visually consistent
- transitions should be animated smoothly rather than jumping abruptly where practical

Do not implement the timeline as a fixed-position layout that only appends cards to the end.

## Player UI Requirements
Each player's normal screen should focus on:
- their own personal timeline
- their current turn interaction
- shared match state relevant to everyone

The normal player view may include:
- the player's timeline
- current card
- deck area
- turn indicator
- end-turn button
- shared status / round result feedback
- score or progress summary

Avoid:
- complex shared-board layouts
- showing every player's timeline at all times
- multi-player card layouts on one screen during normal play
- revealing the active song's identifying details before the turn is resolved
- verbose instructional text on core gameplay screens when clearer layout and hierarchy can carry the state

## UI / UX Requirements
The game should feel modern, responsive, and animated.

Must have:
- smooth drag-and-drop interactions
- snapping behavior for card placement
- support for inserting a card between existing cards
- support for inserting a card at the far left or far right
- automatic repositioning of all timeline cards after insertion
- centered timeline layout after card insertion or adjustment
- clear active-turn indication
- clear end-turn action
- polished animations for draw, move, snap, shift, reveal, and validation
- immersive mobile presentation
- true fullscreen-style presentation that adapts cleanly across landscape phone aspect ratios
- large touch-friendly targets
- landscape-first ergonomics
- prevention of screen sleep while a match is active where supported

Should have:
- subtle UI feedback
- loading and reconnect states
- clear playback-state feedback
- a clean lobby and room-join flow
- a required display-name entry step before a player creates or joins a lobby
- the host lobby should only expose the start action once at least one non-host player is connected
- guest join flow should stay in an explicit connecting state until the authoritative host snapshot confirms that guest; do not transition guests into a fake empty lobby with `0 PLAYERS`
- if the guest does not receive an authoritative host snapshot within a short join window, surface a concrete connection error instead of hanging indefinitely on a connecting screen
- basic error feedback for networking and playback failures

Do not design core gameplay UI for portrait mode.

If a feature is unsupported on a platform:
- fail gracefully
- keep the match playable whenever possible

## State Management Rules
The host is authoritative for:
- deck order
- current player turn
- revealed / hidden card state
- validation results
- score / progress state
- end-of-game conditions
- authoritative timeline contents for all players

Clients should not own critical match state.

Prefer:
- deterministic state transitions
- explicit state models
- minimal hidden state
- clear separation between shared state and visual state

## Suggested Module Structure
Organize the project into clear modules or packages such as:
- core-model
- core-game
- playback-api
- playlist-data
- networking
- ui
- animations
- platform-android
- platform-ios

Keep rendering concerns separate from domain logic.

## Code Style
Code must be clean, readable, and consistent.

Requirements:
- use proper Kotlin formatting consistently
- prefer `val` over `var`
- prefer immutable data structures and immutable domain models whenever practical
- encourage a functional style for transformations, reducers, validation, and state updates
- keep side effects at the boundaries of the system
- keep classes small and focused
- use clear separation of concerns
- avoid god classes
- avoid hidden mutable shared state
- avoid business logic inside rendering code
- avoid business logic inside input-handling code
- prefer descriptive names over abbreviations
- prefer explicit domain models over weakly typed maps or ad-hoc structures

Architecture expectations:
- core rules must be testable without libGDX rendering
- platform integrations must be isolated behind interfaces
- UI code should orchestrate, not own, business rules
- networking messages should use explicit schemas / DTOs
- asynchronous code should be structured and easy to reason about

When possible:
- use pure functions for calculations and validation
- model state transitions explicitly
- make invalid states harder to represent

## Formatting and Cleanliness Expectations
The codebase should:
- have consistent formatting
- follow a predictable package structure
- use clear file names
- keep public APIs small and understandable
- avoid deeply nested logic where simpler structure is possible
- avoid premature abstraction
- avoid duplication when a clean shared abstraction is available

Prefer code that is:
- easy to read
- easy to test
- easy to extend
- easy to refactor

## Testing Expectations
At minimum, add tests for:
- timeline placement validation
- insertion at arbitrary timeline positions
- insertion at the far left
- insertion at the far right
- recalculation of centered card layout after insertion
- turn transitions
- deck behavior
- playlist parsing and validation
- synchronization-critical game rules

Prefer tests for pure shared logic before writing platform-specific integration code.

## Non-Goals for MVP
These are not MVP blockers:
- desktop version
- public matchmaking
- accounts
- cloud saves
- dedicated backend
- Bluetooth transport
- advanced anti-cheat
- spectator mode
- showing all players' timelines on every device

## Acceptance Criteria for First Playable MVP
The MVP is successful when:
- one player can host a local session
- at least one other player can join
- the main flow lets the user choose host or guest before entering the lobby
- Spotify pairing is shown only for Android hosts, not for guests
- available Android hosts are discoverable over the local network through a real networking layer rather than a simulated local flow
- the guest-only web build can discover and join an Android-hosted local session while rendering the same libGDX gameplay client
- the Android host can serve that guest-only web build itself over the local network, and that browser entry should join the single hosted session without requiring a separate host-selection tap
- each player sees only their own normal gameplay UI on their own device
- each player starts with one revealed random card already on their own timeline
- the deck is visible and interactive
- the active player can draw a card
- drawing starts playback through the playback integration layer
- the player can place the card on their own timeline
- the first player to reach 10 revealed timeline cards wins
- the player can insert the card between existing cards or at the far left or far right
- all timeline cards automatically shift to fit the new card
- the full timeline remains centered after insertion
- the card snaps to a valid slot
- the player can end the turn
- the host validates the placement
- all connected players receive the updated shared state without desync
- the match continues correctly across multiple turns

## Agent Instructions
When implementing features:
- preserve the personal one-device-per-player interaction model
- do not accidentally redesign the app into a shared-board UI
- keep core game logic independent from platform SDKs
- keep Spotify integration behind abstractions
- keep Spotify client credentials and redirect configuration in ignored local developer config such as `local.properties`; do not hardcode or commit environment-specific secrets
- a device must render and act only as its own local player; do not implement hotseat-style timeline switching or command impersonation based on the active turn
- keep Android host/session transport and browser guest transport as real networked flows, not simulated local stand-ins
- keep the browser guest build on the shared libGDX client path instead of introducing a separate browser-only UI
- prefer modular, testable, maintainable code
- prefer simple implementations that preserve future extensibility
- keep project-local Codex defaults in `.codex/config.toml`; for this repo use `approval_policy = "never"` and `sandbox_mode = "danger-full-access"` unless the user explicitly changes them
- after creating a commit, push it to the configured remote in the same turn unless the user explicitly says not to
- when a completed change affects Android/mobile gameplay behavior, rebuild or reuse the latest APK as appropriate and reinstall it on the connected device before closing the task
- do not sacrifice architecture cleanliness for short-term convenience unless explicitly required
- when a user request materially affects core functionality or future maintainability, update this file carefully so the requirement is preserved
- temporary local test automation such as a bot player must remain isolated in separate files/controllers and interact through narrow presenter or player-action APIs rather than UI hotseat switching
- treat this file as high-signal project guidance and avoid unnecessary edits or churn
