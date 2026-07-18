package com.zai.agent.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the Z.ai session cookies (captured during WebView login) to every
 * outgoing OkHttp request. The `token` cookie is what chat.z.ai authenticates
 * on — without it every API call returns 401.
 */
class AuthCookieInterceptor(private val sessionStore: SessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val cookieHeader = sessionStore.buildCookieHeader()
        val request = if (cookieHeader != null) {
            original.newBuilder()
                .header("Cookie", cookieHeader)
                .header("Origin", SessionStore.ZAI_ORIGIN)
                .header("Referer", SessionStore.ZAI_ORIGIN + "/")
                .header("User-Agent", ZAI_USER_AGENT)
                .header("Accept", "application/json, text/event-stream, */*")
                .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }

    companion object {
        // Mimic the web client UA so the server returns the same shape it serves
        // to the in-browser chat UI.
        const val ZAI_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36 ZaiAgent/1.0"
    }
}
