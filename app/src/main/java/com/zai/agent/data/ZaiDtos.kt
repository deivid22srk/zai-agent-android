package com.zai.agent.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs that mirror the public Z.ai chat endpoints.
 *
 * These shapes were derived from the public traffic that https://chat.z.ai/ uses
 * when a user is logged in via the web app. They are intentionally tolerant —
 * unknown fields are ignored by the kotlinx-serialization parser.
 */

@Serializable
data class ZaiConversation(
    val id: String,
    val title: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
    @SerialName("model_id") val modelId: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    val summary: String? = null,
    val pinned: Boolean? = null,
)

@Serializable
data class ZaiConversationListResponse(
    val items: List<ZaiConversation> = emptyList(),
    val next: String? = null,
)

@Serializable
data class ZaiMessage(
    val id: String? = null,
    val role: String,
    val content: String,
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class ZaiSendMessageRequest(
    @SerialName("conversation_id") val conversationId: String,
    val content: String,
    val role: String = "user",
    @SerialName("model_id") val modelId: String = DEFAULT_MODEL,
    @SerialName("agent_id") val agentId: String? = null,
    val mode: String = "agent",
    val stream: Boolean = true,
    val params: Map<String, JsonElement>? = null,
) {
    companion object {
        const val DEFAULT_MODEL = "glm-4.6"
    }
}

@Serializable
data class ZaiCreateConversationRequest(
    val title: String? = null,
    @SerialName("model_id") val modelId: String = ZaiSendMessageRequest.DEFAULT_MODEL,
    @SerialName("agent_id") val agentId: String? = null,
    val mode: String = "agent",
)

@Serializable
data class ZaiCreateConversationResponse(
    val id: String,
    val title: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class ZaiUser(
    val id: String? = null,
    val email: String? = null,
    val name: String? = null,
    val avatar: String? = null,
)
