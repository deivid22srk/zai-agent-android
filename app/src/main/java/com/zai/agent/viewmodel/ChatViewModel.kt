package com.zai.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.agent.data.SessionStore
import com.zai.agent.data.ZaiRepository
import com.zai.agent.data.ZaiConversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversationId: String? = null,
    val title: String? = null,
    val agentMode: Boolean? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Drives the ChatScreen. Most of the heavy lifting (streaming, captcha, MCP
 * tools) is handled by the WebView, so this VM is intentionally thin — it
 * only fetches the conversation metadata (title, type) and refreshes the
 * session cookies whenever the WebView reports new ones.
 */
class ChatViewModel(
    private val repository: ZaiRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun openConversation(id: String) {
        if (_uiState.value.conversationId == id) return
        _uiState.update {
            ChatUiState(conversationId = id, loading = true)
        }
        viewModelScope.launch {
            repository.getConversation(id).collect { result ->
                result.fold(
                    onSuccess = { detail ->
                        _uiState.update {
                            it.copy(
                                loading = false,
                                title = detail.title?.takeIf { t -> t.isNotBlank() } ?: "Conversa",
                                agentMode = detail.type == "general_agent",
                                error = null,
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                loading = false,
                                title = "Conversa",
                                error = err.message,
                            )
                        }
                    }
                )
            }
        }
    }

    /**
     * Called from the WebView's onPageFinished via JS bridge whenever we
     * successfully extract the conversation title from the DOM.
     */
    fun onTitleExtracted(title: String) {
        if (title.isBlank()) return
        _uiState.update { it.copy(title = title) }
    }

    /**
     * Called from the WebView whenever the server refreshes cookies. We
     * persist them so the next API call (and the next WebView load) reuses
     * the same session.
     */
    fun onCookiesRefreshed(cookieMap: Map<String, String>) {
        val cookies = cookieMap.map { (name, value) ->
            com.zai.agent.data.ZaiCookie(name = name, value = value)
        }
        sessionStore.saveCookies(cookies)
        cookieMap["token"]?.let { sessionStore.saveToken(it) }
    }
}
