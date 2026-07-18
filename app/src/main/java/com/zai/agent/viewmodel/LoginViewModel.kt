package com.zai.agent.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.agent.data.SessionStore
import com.zai.agent.data.ZaiCookie
import com.zai.agent.data.ZaiUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: ZaiUser? = null,
    val statusMessage: String? = null,
    val webViewVisible: Boolean = false,
    val webViewUrl: String = "https://chat.z.ai/",
    val error: String? = null,
)

/**
 * Drives the login flow:
 *
 * 1) If the user already has a valid token in SessionStore, call
 *    [validateSession] to confirm it works (GET /api/v1/auths/). If yes,
 *    skip the WebView entirely.
 *
 * 2) Otherwise, open a WebView pre-loaded with the cookies captured from any
 *    previous login (so the user doesn't have to re-type credentials if
 *    their session is still alive in chat.z.ai).
 *
 * 3) The WebView reports cookies back via [onCookiesUpdated] every time a
 *    page finishes loading. As soon as a `token` cookie is present, we save
 *    it and call [validateSession] again to fetch the user profile. If the
 *    validation succeeds, we mark the user as logged in.
 *
 * Important: we never mark isLoggedIn=true just because a `token` cookie
 * exists — we always verify against /api/v1/auths/ first. This prevents
 * the bug where an anonymous token (set by chat.z.ai before the user signs
 * in) is mistaken for an authenticated session.
 */
class LoginViewModel(
    private val sessionStore: SessionStore,
    private val repository: com.zai.agent.data.ZaiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(
            isLoggedIn = false,
            user = sessionStore.getUser(),
            webViewVisible = false,
        )
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // On startup, if we have a stored token, try to validate it. Only
        // after the server confirms the session is alive do we set
        // isLoggedIn=true.
        if (sessionStore.isLoggedIn()) {
            validateSession()
        }
    }

    fun openWebView() {
        _uiState.update {
            it.copy(
                webViewVisible = true,
                statusMessage = "Abrindo login…",
                error = null,
            )
        }
    }

    fun closeWebView() {
        _uiState.update { it.copy(webViewVisible = false) }
    }

    /**
     * Called by the WebView callback whenever a page finishes loading.
     * We persist all cookies and, if a `token` is present, validate the
     * session before marking the user as logged in.
     */
    fun onCookiesUpdated(cookieMap: Map<String, String>) {
        val cookies = cookieMap.map { (name, value) ->
            ZaiCookie(name = name, value = value, domain = "chat.z.ai", path = "/")
        }
        sessionStore.saveCookies(cookies)

        val token = cookieMap["token"]
        if (!token.isNullOrBlank()) {
            sessionStore.saveToken(token)
            validateSession()
        }
    }

    fun validateSession() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, statusMessage = "Verificando sessão…", error = null)
            }
            repository.validateSession().collect { result ->
                result.fold(
                    onSuccess = { user ->
                        _uiState.update {
                            it.copy(
                                loading = false,
                                isLoggedIn = true,
                                user = user,
                                statusMessage = null,
                                webViewVisible = false,
                                error = null,
                            )
                        }
                    },
                    onFailure = { err ->
                        Log.w("LoginViewModel", "session validation failed", err)
                        _uiState.update {
                            it.copy(
                                loading = false,
                                isLoggedIn = false,
                                statusMessage = null,
                                error = err.message ?: "Sessão expirada, faça login novamente",
                            )
                        }
                    }
                )
            }
        }
    }

    fun logout() {
        sessionStore.clear()
        _uiState.update { LoginUiState() }
    }
}
