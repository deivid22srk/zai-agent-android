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

data class ChatListUiState(
    val loading: Boolean = false,
    val conversations: List<ZaiConversation> = emptyList(),
    val errorMessage: String? = null,
    val creating: Boolean = false,
)

class ChatListViewModel(
    private val repository: ZaiRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    fun loadConversations() {
        if (_uiState.value.loading) return
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.listConversations().collect { result ->
                result.fold(
                    onSuccess = { convos ->
                        _uiState.update {
                            it.copy(loading = false, conversations = convos, errorMessage = null)
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(loading = false, errorMessage = err.message ?: "Erro ao carregar")
                        }
                    }
                )
            }
        }
    }

    fun createConversation(title: String?, agentMode: Boolean, onCreated: (String) -> Unit) {
        if (_uiState.value.creating) return
        _uiState.update { it.copy(creating = true, errorMessage = null) }
        viewModelScope.launch {
            repository.createConversation(title = title, agentMode = agentMode).collect { result ->
                result.fold(
                    onSuccess = { convo ->
                        _uiState.update {
                            it.copy(
                                creating = false,
                                conversations = listOf(convo.let { c ->
                                    ZaiConversation(id = c.id, title = c.title ?: title ?: "Nova conversa")
                                }) + it.conversations
                            )
                        }
                        onCreated(convo.id)
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(creating = false, errorMessage = err.message ?: "Erro ao criar conversa")
                        }
                    }
                )
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id).collect { result ->
                result.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(conversations = it.conversations.filterNot { c -> c.id == id })
                        }
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(errorMessage = err.message) }
                    }
                )
            }
        }
    }

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch {
            repository.renameConversation(id, title).collect { result ->
                result.fold(
                    onSuccess = {
                        _uiState.update { state ->
                            state.copy(
                                conversations = state.conversations.map { c ->
                                    if (c.id == id) c.copy(title = title) else c
                                }
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(errorMessage = err.message) }
                    }
                )
            }
        }
    }

    fun logout() {
        sessionStore.clear()
        _uiState.update { ChatListUiState() }
    }
}
