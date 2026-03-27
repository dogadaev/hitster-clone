package com.hitster.networking

/**
 * Explicit JSON encoding and decoding helpers for browser-safe transport payloads.
 */

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

val protocolJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Encodes client commands into a stable browser-safe JSON payload without relying on polymorphic runtime serializers. */
fun encodeClientCommandPayload(command: ClientCommandDto): String {
    return when (command) {
        is ClientCommandDto.JoinSession -> buildJsonObject {
            put("type", "join_session")
            put("actorId", command.actorId)
            put("displayName", command.displayName)
        }

        is ClientCommandDto.UpdatePlayerName -> buildJsonObject {
            put("type", "update_player_name")
            put("actorId", command.actorId)
            put("displayName", command.displayName)
        }

        is ClientCommandDto.ReorderLobbyPlayers -> buildJsonObject {
            put("type", "reorder_lobby_players")
            put("actorId", command.actorId)
            put("playerId", command.playerId)
            put("targetIndex", command.targetIndex)
        }

        is ClientCommandDto.StartGame -> buildJsonObject {
            put("type", "start_game")
            put("actorId", command.actorId)
        }

        is ClientCommandDto.DrawCard -> buildJsonObject {
            put("type", "draw_card")
            put("actorId", command.actorId)
        }

        is ClientCommandDto.RedrawCard -> buildJsonObject {
            put("type", "redraw_card")
            put("actorId", command.actorId)
        }

        is ClientCommandDto.ToggleDoubt -> buildJsonObject {
            put("type", "toggle_doubt")
            put("actorId", command.actorId)
        }

        is ClientCommandDto.MovePendingCard -> buildJsonObject {
            put("type", "move_pending_card")
            put("actorId", command.actorId)
            put("requestedSlotIndex", command.requestedSlotIndex)
        }

        is ClientCommandDto.MoveDoubtCard -> buildJsonObject {
            put("type", "move_doubt_card")
            put("actorId", command.actorId)
            put("requestedSlotIndex", command.requestedSlotIndex)
        }

        is ClientCommandDto.AdjustPlayerCoins -> buildJsonObject {
            put("type", "adjust_player_coins")
            put("actorId", command.actorId)
            put("playerId", command.playerId)
            put("delta", command.delta)
        }

        is ClientCommandDto.EndTurn -> buildJsonObject {
            put("type", "end_turn")
            put("actorId", command.actorId)
        }
    }.toString()
}

/** Decodes the small subset of host events the browser guest transport handles through manual JSON parsing. */
fun decodeHostEventPayload(payload: String): HostEventDto? {
    val parsed = runCatching {
        protocolJson.parseToJsonElement(payload).jsonObject
    }.getOrNull() ?: return null

    return when (parsed["type"]?.jsonPrimitive?.contentOrNull) {
        "snapshot" -> {
            val stateElement = parsed["state"] ?: return null
            runCatching {
                HostEventDto.SnapshotPublished(
                    state = protocolJson.decodeFromJsonElement<GameStateDto>(stateElement),
                )
            }.getOrNull()
        }

        "command_rejected" -> {
            val actorId = parsed["actorId"]?.jsonPrimitive?.contentOrNull ?: return null
            val reason = parsed["reason"]?.jsonPrimitive?.contentOrNull ?: return null
            val revision = parsed["revision"]?.jsonPrimitive?.longOrNull ?: return null
            HostEventDto.CommandRejected(
                actorId = actorId,
                reason = reason,
                revision = revision,
            )
        }

        else -> null
    }
}
