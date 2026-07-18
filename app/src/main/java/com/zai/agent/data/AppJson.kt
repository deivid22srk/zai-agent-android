package com.zai.agent.data

import kotlinx.serialization.json.Json

/**
 * Shared JSON instance used across networking and persistence. Settings chosen:
 * - ignoreUnknownKeys: the server may add new fields; the client must not crash.
 * - explicitNulls = false: when serializing outgoing requests we omit nulls.
 * - coerceInputValues: bad/null values from the server fall back to defaults.
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = false
    prettyPrint = false
}
