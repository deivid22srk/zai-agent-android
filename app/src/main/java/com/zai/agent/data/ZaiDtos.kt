package com.zai.agent.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs that mirror the public Z.ai chat endpoints.
 *
 * These shapes were verified against the live https://chat.z.ai API on
 * 2026-07-18 using a real authenticated session.
 */

@Serializable
data class ZaiConversation(
    val id: String,
    val title: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
    val type: String? = null,             // "general_agent" | "default"
    @SerialName("im_context") val imContext: ImContext? = null,
    val archived: Boolean? = null,
    val pinned: Boolean? = null,
) {
    val isAgent: Boolean get() = type == "general_agent"
}

@Serializable
data class ImContext(
    @SerialName("session_id") val sessionId: String? = null,
    val type: String? = null,
    val channel: String? = null,
    val model: String? = null,
    val status: String? = null,
)

@Serializable
data class ZaiConversationListResponse(
    val items: List<ZaiConversation> = emptyList(),
    val next: String? = null,
)

/**
 * Detailed chat object returned by GET /api/v1/chats/{id}.
 * The `chat.history.messages` field is a map of messageId -> message, in
 * OpenWebUI format. We only need the flat ordered list to display in the UI.
 */
@Serializable
data class ZaiChatDetail(
    val id: String,
    val title: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val chat: ChatPayload? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    val type: String? = null,
    @SerialName("im_context") val imContext: ImContext? = null,
) {
    @Serializable
    data class ChatPayload(
        val id: String? = null,
        val models: List<String> = emptyList(),
        val history: ChatHistory? = null,
    )

    @Serializable
    data class ChatHistory(
        val messages: Map<String, ChatMessage> = emptyMap(),
        @SerialName("currentId") val currentId: String? = null,
    )

    @Serializable
    data class ChatMessage(
        val id: String? = null,
        @SerialName("parentId") val parentId: String? = null,
        @SerialName("childrenIds") val childrenIds: List<String> = emptyList(),
        val role: String,
        val content: String = "",
        val timestamp: Long? = null,
        val done: Boolean? = null,
    )

    /**
     * Returns the messages in chronological order by walking the tree from the
     * root (parentId == null) following the first child of each message.
     */
    fun orderedMessages(): List<ChatMessage> {
        val history = chat?.history ?: return emptyList()
        val messages = history.messages
        if (messages.isEmpty()) return emptyList()
        val byParent = messages.values.groupBy { it.parentId }
        val roots = byParent[null] ?: byParent[""] ?: emptyList()
        val result = mutableListOf<ChatMessage>()
        var current = roots.firstOrNull()
        val visited = mutableSetOf<String>()
        while (current != null && current.id != null && visited.add(current.id)) {
            result.add(current)
            val nextId = current.childrenIds.firstOrNull() ?: break
            current = messages[nextId]
        }
        return result
    }
}

@Serializable
data class ZaiCreateConversationRequest(
    val chat: ChatBody,
) {
    @Serializable
    data class ChatBody(
        val title: String = "",
        val models: List<String> = listOf("glm-5.2"),
    )
}

@Serializable
data class ZaiCreateConversationResponse(
    val id: String,
    val title: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    val type: String? = null,
)
