package com.zai.agent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persistent store of the Z.ai session.
 *
 * We persist the full set of cookies captured from the WebView (not just the
 * `token` cookie) because the server may also check `acw_tc`, `cdn_sec_tc`,
 * `ssxmod_itna` etc. for bot detection.
 *
 * The JWT `token` is what authenticates the user, but we also store the user
 * profile (id, email, name, avatar) returned by `/api/v1/auths/` so the
 * native UI can render the user without making another network call on every
 * screen transition.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun saveToken(token: String?) {
        prefs.edit {
            if (token.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, token)
        }
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveCookies(cookies: List<ZaiCookie>) {
        val serialized = json.encodeToString(ListSerializer(ZaiCookie.serializer()), cookies)
        prefs.edit { putString(KEY_COOKIES, serialized) }
    }

    fun getCookies(): List<ZaiCookie> {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ZaiCookie.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    /**
     * Returns a single Cookie header string suitable for OkHttp:
     * `token=abc; _c_WBKFRo=def; acw_tc=ghi`
     */
    fun buildCookieHeader(): String? {
        val cookies = getCookies()
        if (cookies.isEmpty()) return null
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /**
     * Builds the `Authorization: Bearer ...` header value, or null if no token.
     */
    fun buildAuthHeader(): String? {
        val token = getToken() ?: return null
        return "Bearer $token"
    }

    fun saveUser(user: ZaiUser) {
        val serialized = json.encodeToString(ZaiUser.serializer(), user)
        prefs.edit { putString(KEY_USER, serialized) }
    }

    fun getUser(): ZaiUser? {
        val raw = prefs.getString(KEY_USER, null) ?: return null
        return runCatching { json.decodeFromString(ZaiUser.serializer(), raw) }.getOrNull()
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val PREFS_NAME = "zai_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_USER = "user"

        const val ZAI_BASE_URL = "https://chat.z.ai"
        const val ZAI_ORIGIN = "https://chat.z.ai"
        const val ZAI_FE_VERSION = "prod-fe-1.1.77"

        // Mimic the web client UA so the server returns the same shape it serves
        // to the in-browser chat UI.
        const val ZAI_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

@Serializable
data class ZaiCookie(
    val name: String,
    val value: String,
    val domain: String = "chat.z.ai",
    val path: String = "/",
)

@Serializable
data class ZaiUser(
    val id: String? = null,
    val email: String? = null,
    val name: String? = null,
    val profile_image_url: String? = null,
    val role: String? = null,
)
