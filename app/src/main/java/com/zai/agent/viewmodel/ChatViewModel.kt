package com.zai.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.agent.data.SseEvent
import com.zai.agent.data.ZaiApiClient
import com.zai.agent.data.ZaiSendMessageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val conversationId: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val streaming: Boolean = false,
    val streamingText: String = "",
    val errorMessage: String? = null,
    val agentMode: Boolean = true,
    val canSend: Boolean = true,
)

data class UiMessage(
    val id: String,
    val role: String,        // "user" | "assistant"
    val content: String,
    val pending: Boolean = false,
)

class ChatViewModel(
    private val apiClient: ZaiApiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    fun setConversation(id: String) {
        if (_uiState.value.conversationId == id) return
        cancelStreaming()
        _uiState.update { it.copy(conversationId = id, messages = emptyList(), streamingText = "", errorMessage = null) }
    }

    fun toggleAgentMode(enabled: Boolean) {
        _uiState.update { it.copy(agentMode = enabled) }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.streaming) return
        val conversationId = _uiState.value.conversationId ?: return

        val userMessage = UiMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = trimmed,
        )
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                streaming = true,
                streamingText = "",
                errorMessage = null,
                canSend = false,
            )
        }

        streamJob = viewModelScope.launch {
            val request = ZaiSendMessageRequest(
                conversationId = conversationId,
                content = trimmed,
                mode = if (_uiState.value.agentMode) "agent" else "chat",
            )
            try {
                apiClient.streamMessage(request).collect { event ->
                    when (event) {
                        is SseEvent.Delta -> {
                            if (event.content.isNotEmpty()) {
                                _uiState.update { state ->
                                    state.copy(streamingText = state.streamingText + event.content)
                                }
                            }
                        }
                        is SseEvent.Text -> {
                            _uiState.update { state ->
                                state.copy(streamingText = state.streamingText + event.raw)
                            }
                        }
                        SseEvent.Done -> finalizeAssistantMessage()
                    }
                }
                if (_uiState.value.streaming) finalizeAssistantMessage()
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        streaming = false,
                        canSend = true,
                        errorMessage = t.message ?: "Erro de conexão",
                    )
                }
            }
        }
    }

    private fun finalizeAssistantMessage() {
        val current = _uiState.value
        if (current.streamingText.isBlank() && current.errorMessage == null) {
            _uiState.update { it.copy(streaming = false, canSend = true) }
            return
        }
        val assistant = UiMessage(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = current.streamingText,
        )
        _uiState.update {
            it.copy(
                messages = it.messages + assistant,
                streaming = false,
                streamingText = "",
                canSend = true,
            )
        }
    }

    fun cancelStreaming() {
        streamJob?.cancel()
        streamJob = null
        finalizeAssistantMessage()
    }

    fun regenerateLast() {
        val state = _uiState.value
        val lastUser = state.messages.lastIndexOfFirst { it.role == "user" }
        if (lastUser < 0) return
        val text = state.messages[lastUser].content
        // Drop everything from the last user message onward and resend.
        _uiState.update {
            it.copy(messages = it.messages.subList(0, lastUser))
        }
        sendMessage(text)
    }

    private fun <T> List<T>.lastIndexOfFirst(predicate: (T) -> Boolean): Int {
        for (i in indices.reversed()) if (predicate(this[i])) return i
        return -1
    }
}
