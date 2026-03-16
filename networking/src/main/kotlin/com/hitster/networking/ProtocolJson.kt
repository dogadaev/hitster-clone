package com.hitster.networking

import kotlinx.serialization.json.Json

val protocolJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
