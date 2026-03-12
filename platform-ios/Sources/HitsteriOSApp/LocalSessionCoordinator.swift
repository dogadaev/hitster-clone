import Foundation

struct SessionAdvertisement {
    let sessionID: String
    let hostDisplayName: String
    let playerCount: Int
}

protocol LocalSessionCoordinating {
    func startHosting() async throws -> SessionAdvertisement
    func join(sessionID: String) async throws
    func stop()
}

final class LocalSessionCoordinator: LocalSessionCoordinating {
    func startHosting() async throws -> SessionAdvertisement {
        return SessionAdvertisement(
            sessionID: UUID().uuidString,
            hostDisplayName: "Host Player",
            playerCount: 1
        )
    }

    func join(sessionID: String) async throws {
    }

    func stop() {
    }
}
