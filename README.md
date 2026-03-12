# Hitster Clone

Initial MVP foundation for a local-network multiplayer music timeline game inspired by Hitster.

## Modules

- `core-model`: immutable shared domain models.
- `core-game`: deterministic host-authoritative turn reducer and validation rules.
- `playback-api`: playback abstraction used by shared code.
- `playlist-data`: JSON playlist parsing with user-friendly validation errors.
- `networking`: explicit session and game-state DTOs for transport layers.
- `animations`: UI-facing animation specs and cues.
- `ui`: libGDX-based shared rendering and interaction shell.
- `platform-android`: Android launcher and Android playback bridge stubs.
- `platform-ios`: Swift integration stubs for an eventual native iOS host app.

## Current Scope

This repository implements the shared logic and a thin libGDX UI shell for the first MVP loop:

1. Create a host-authoritative session.
2. Start a local match with at least two players.
3. Drag from the deck to draw a card.
4. Snap the pending card onto a timeline slot.
5. End the turn and validate the placement.
6. Emit synchronization-ready snapshots for all accepted commands.

The Android and iOS platform folders intentionally keep native SDK concerns at the edge. Spotify SDK wiring and real LAN transport can be added behind the existing interfaces without changing the shared rules.

## Verification

The project includes tests for deck behavior, playlist parsing, timeline validation, turn transitions, and synchronization-critical reducer behavior.

The local environment available during implementation did not provide a working Gradle runtime, so the code was written defensively but not executed here.
