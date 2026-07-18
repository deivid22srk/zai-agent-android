package com.zai.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.agent.data.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val statusMessage: String? = null,
    val webViewVisible: Boolean = false,
)

/**
 * Drives the login flow:
 * 1) Show a "tap to login" screen.
 * 2) Open a WebView to https://chat.z.ai/.
 * 3) Capture the `token` cookie once the user signs in.
 * 4) Verify by checking SessionStore.isLoggedIn.
 */
class LoginViewModel(
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(isLoggedIn = sessionStore.isLoggedIn()))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun openWebView() {
        _uiState.update { it.copy(webViewVisible = true, statusMessage = null) }
    }

    fun closeWebView() {
        _uiState.update { it.copy(webViewVisible = false) }
    }

    /**
     * Called by the WebView callback whenever a new cookie is set on the .z.ai
     * domain. We are only interested in the `token` cookie, which is what the
     * Z.ai backend uses to authenticate API calls.
     */
    fun onCookiesUpdated(cookieMap: Map<String, String>) {
        val token = cookieMap["token"]
        if (!token.isNullOrBlank()) {
            sessionStore.saveToken(token)
            val extras = cookieMap.filterKeys { it != "token" }
            sessionStore.saveExtraCookies(extras)
            _uiState.update {
                it.copy(
                    isLoggedIn = true,
                    statusMessage = "Sessão ativa",
                    webViewVisible = false,
                )
            }
        }
    }

    fun checkSession() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, statusMessage = "Verificando sessão…")
            }
            val loggedIn = sessionStore.isLoggedIn()
            _uiState.update {
                it.copy(
                    loading = false,
                    isLoggedIn = loggedIn,
                    statusMessage = if (loggedIn) "Sessão ativa" else null,
                )
            }
        }
    }

    fun logout() {
        sessionStore.clear()
        _uiState.update { LoginUiState() }
    }
}
