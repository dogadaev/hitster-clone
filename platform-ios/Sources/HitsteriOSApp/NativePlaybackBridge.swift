import Foundation

enum NativePlaybackError: Error {
    case appNotInstalled
    case notAuthenticated
    case playbackUnavailable
    case missingMetadata
    case platformRestriction
}

protocol NativePlaybackControlling {
    func play(spotifyURI: String) throws
    func pause() throws
}

final class SpotifyPlaybackBridge: NativePlaybackControlling {
    func play(spotifyURI: String) throws {
        guard !spotifyURI.isEmpty else {
            throw NativePlaybackError.missingMetadata
        }

        throw NativePlaybackError.notAuthenticated
    }

    func pause() throws {
    }
}

