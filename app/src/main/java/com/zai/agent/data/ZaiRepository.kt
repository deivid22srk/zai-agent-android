package com.zai.agent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * REST-style calls used by the chat-list / drawer UI. Streaming is handled by
 * [ZaiApiClient]; this repository is for the bookkeeping endpoints.
 */
class ZaiRepository(
    private val httpClient: OkHttpClient,
    private val json: Json = AppJson,
) {

    fun listConversations(): Flow<Result<List<ZaiConversation>>> = flow {
        val request = Request.Builder()
            .url("${SessionStore.ZAI_BASE_URL}$PATH_LIST")
            .get()
            .build()
        val resp = runCatching { httpClient.newCall(request).execute() }
            .getOrElse { emit(Result.failure(it)); return@flow }
        resp.use { r ->
            if (!r.isSuccessful) {
                emit(Result.failure(ApiException(r.code, "list failed: ${r.message}")))
                return@flow
            }
            val body = r.body?.string().orEmpty()
            val parsed = runCatching {
                json.decodeFromString(ZaiConversationListResponse.serializer(), body)
            }.getOrElse {
                // Fallback: try to parse a top-level JSON array of conversations
                runCatching {
                    json.decodeFromString<List<ZaiConversation>>(body)
                }.map { ZaiConversationListResponse(items = it) }.getOrNull()
            }
            emit(Result.success(parsed?.items ?: emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun createConversation(title: String? = null, agentMode: Boolean = true): Flow<Result<ZaiCreateConversationResponse>> = flow {
        val req = ZaiCreateConversationRequest(
            title = title,
            modelId = ZaiSendMessageRequest.DEFAULT_MODEL,
            mode = if (agentMode) "agent" else "chat"
        )
        val payload = json.encodeToString(ZaiCreateConversationRequest.serializer(), req)
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${SessionStore.ZAI_BASE_URL}$PATH_CREATE")
            .post(payload)
            .build()
        val resp = runCatching { httpClient.newCall(request).execute() }
            .getOrElse { emit(Result.failure(it)); return@flow }
        resp.use { r ->
            if (!r.isSuccessful) {
                emit(Result.failure(ApiException(r.code, "create failed: ${r.message}")))
                return@flow
            }
            val body = r.body?.string().orEmpty()
            val parsed = runCatching {
                json.decodeFromString(ZaiCreateConversationResponse.serializer(), body)
            }
            emit(parsed.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(it) }
            ))
        }
    }.flowOn(Dispatchers.IO)

    fun deleteConversation(id: String): Flow<Result<Unit>> = flow {
        val request = Request.Builder()
            .url("${SessionStore.ZAI_BASE_URL}$PATH_DELETE/$id")
            .delete()
            .build()
        val resp = runCatching { httpClient.newCall(request).execute() }
            .getOrElse { emit(Result.failure(it)); return@flow }
        resp.use { r ->
            if (!r.isSuccessful) {
                emit(Result.failure(ApiException(r.code, "delete failed: ${r.message}")))
                return@flow
            }
            emit(Result.success(Unit))
        }
    }.flowOn(Dispatchers.IO)

    fun renameConversation(id: String, title: String): Flow<Result<Unit>> = flow {
        val payload = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("title", JsonPrimitive(title))
            }
        ).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${SessionStore.ZAI_BASE_URL}$PATH_RENAME/$id")
            .patch(payload)
            .build()
        val resp = runCatching { httpClient.newCall(request).execute() }
            .getOrElse { emit(Result.failure(it)); return@flow }
        resp.use { r ->
            if (!r.isSuccessful) {
                emit(Result.failure(ApiException(r.code, "rename failed: ${r.message}")))
                return@flow
            }
            emit(Result.success(Unit))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val PATH_LIST = "/api/chat/conversations"
        private const val PATH_CREATE = "/api/chat/conversations"
        private const val PATH_DELETE = "/api/chat/conversations"
        private const val PATH_RENAME = "/api/chat/conversations"
    }
}
