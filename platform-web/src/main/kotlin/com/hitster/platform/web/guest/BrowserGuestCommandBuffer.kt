package com.hitster.platform.web

/**
 * Coalesces browser guest commands so drag traffic stays ordered and lightweight over the HTTP proxy transport.
 */

import com.hitster.networking.ClientCommandDto

internal enum class BrowserGuestCommandKind {
    IMMEDIATE,
    MOVE_PENDING,
    MOVE_DOUBT,
}

internal data class BrowserGuestBufferedCommand(
    val kind: BrowserGuestCommandKind,
    val payload: String,
)

internal class BrowserGuestCommandBuffer {
    private val commands = mutableListOf<BrowserGuestBufferedCommand>()

    fun enqueue(kind: BrowserGuestCommandKind, payload: String) {
        val command = BrowserGuestBufferedCommand(kind = kind, payload = payload)
        when (kind) {
            BrowserGuestCommandKind.MOVE_PENDING,
            BrowserGuestCommandKind.MOVE_DOUBT,
            -> {
                val existingIndex = commands.indexOfLast { it.kind == kind }
                if (existingIndex >= 0) {
                    commands[existingIndex] = command
                } else {
                    commands += command
                }
            }

            BrowserGuestCommandKind.IMMEDIATE -> commands += command
        }
    }

    fun prepend(command: BrowserGuestBufferedCommand) {
        commands.add(0, command)
    }

    fun poll(): BrowserGuestBufferedCommand? {
        if (commands.isEmpty()) {
            return null
        }
        return commands.removeAt(0)
    }

    fun clear() {
        commands.clear()
    }
}

internal fun browserGuestCommandKind(command: ClientCommandDto): BrowserGuestCommandKind {
    return when (command) {
        is ClientCommandDto.MovePendingCard -> BrowserGuestCommandKind.MOVE_PENDING
        is ClientCommandDto.MoveDoubtCard -> BrowserGuestCommandKind.MOVE_DOUBT
        else -> BrowserGuestCommandKind.IMMEDIATE
    }
}
