package com.zai.agent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persistent store of the Z.ai session token and any extra cookies captured by the
 * WebView login flow. The store is intentionally minimal — we only persist what the
 * HTTP API needs to authenticate subsequent requests.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String?) {
        prefs.edit {
            if (token.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, token)
        }
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveExtraCookies(cookies: Map<String, String>) {
        prefs.edit {
            putString(KEY_COOKIES, cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
    }

    fun getExtraCookiesHeader(): String? = prefs.getString(KEY_COOKIES, null)

    /**
     * Builds the Cookie header that the OkHttp interceptor attaches to every API
     * request. The `token` cookie is what chat.z.ai actually checks.
     */
    fun buildCookieHeader(): String? {
        val token = getToken() ?: return null
        val extras = getExtraCookiesHeader()
        return buildString {
            append("token=").append(token)
            if (!extras.isNullOrBlank()) {
                append("; ").append(extras)
            }
        }
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val PREFS_NAME = "zai_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_COOKIES = "cookies"

        const val ZAI_BASE_URL = "https://chat.z.ai"
        const val ZAI_ORIGIN = "https://chat.z.ai"
    }
}
