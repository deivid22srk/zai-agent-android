package com.zai.agent.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zai.agent.R
import com.zai.agent.ZaiApplication
import com.zai.agent.viewmodel.ChatViewModel

/**
 * Chat screen backed by the chat.z.ai web app inside a WebView.
 *
 * Why a WebView instead of a pure OkHttp + Compose implementation?
 *
 * 1) chat.z.ai requires an Aliyun Captcha widget (JavaScript) before it will
 *    accept a completion request. Solving that captcha requires a real
 *    browser environment — OkHttp alone returns 426 / FRONTEND_CAPTCHA_REQUIRED.
 *
 * 2) The web app already handles streaming SSE, agent mode, file uploads,
 *    MCP tools (web search, deep research, etc) and the entire UI. Reusing
 *    it via WebView means the user gets a feature-parity experience with
 *    zero re-implementation cost.
 *
 * The native Material 3 chrome (TopAppBar, FAB, drawer navigation) still
 * wraps the WebView so the app feels native.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZaiApplication
                ChatViewModel(app.repository, app.sessionStore)
            }
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadingProgress by remember { mutableStateOf(0) }

    LaunchedEffect(conversationId) {
        viewModel.openConversation(conversationId)
    }

    val chatUrl = remember(conversationId) {
        // The web app's URL for an existing conversation is /c/{id}
        "https://chat.z.ai/c/$conversationId"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.title ?: "Conversa",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.agentMode == true) {
                            Text(
                                text = stringResource(R.string.agent_mode),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recarregar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    // Pre-seed the WebView's CookieManager with the session cookies
                    // so the user is already signed in when the chat page loads.
                    CookieManager.getInstance().setAcceptCookie(true)
                    val app = ctx.applicationContext as ZaiApplication
                    app.sessionStore.getCookies().forEach { c ->
                        CookieManager.getInstance().setCookie(
                            "https://${c.domain}",
                            "${c.name}=${c.value}; domain=${c.domain}; path=${c.path}"
                        )
                    }
                    CookieManager.getInstance().flush()

                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.userAgentString = com.zai.agent.data.SessionStore.ZAI_USER_AGENT
                        settings.mediaPlaybackRequiresUserGesture = false

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean = false

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Re-capture any cookies that the server may have refreshed.
                                val cookieStr = CookieManager.getInstance().getCookie(com.zai.agent.data.SessionStore.ZAI_BASE_URL)
                                val map = parseCookieHeader(cookieStr)
                                if (map.isNotEmpty()) {
                                    viewModel.onCookiesRefreshed(map)
                                }
                                // Inject a small JS hook to extract the conversation title.
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                      try {
                                        var titleEl = document.querySelector('textarea[placeholder], [class*="title"], header h1, header h2');
                                        if (titleEl && titleEl.value) {
                                          AndroidBridge.onTitle(titleEl.value);
                                        } else if (titleEl && titleEl.textContent) {
                                          AndroidBridge.onTitle(titleEl.textContent.trim());
                                        }
                                      } catch(e) {}
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }
                        }

                        // Add a JS bridge so the page can report its title back to us.
                        addJavascriptInterface(
                            object {
                                @android.webkit.JavascriptInterface
                                fun onTitle(title: String) {
                                    viewModel.onTitleExtracted(title)
                                }
                            },
                            "AndroidBridge"
                        )

                        loadUrl(chatUrl)
                        webView = this
                    }
                }
            )

            // Loading progress bar at the top while the chat page is loading.
            if (loadingProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadingProgress / 100f },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxSize(),
                )
            }

            if (uiState.loading && uiState.title == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                    }
                }
            }

            uiState.error?.let { err ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private fun parseCookieHeader(header: String?): Map<String, String> {
    if (header.isNullOrBlank()) return emptyMap()
    return header.split(";")
        .mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null
            else part.substring(0, idx).trim() to part.substring(idx + 1).trim()
        }
        .toMap()
}
