package com.zai.agent

import android.app.Application
import com.zai.agent.data.AppJson
import com.zai.agent.data.SessionStore
import com.zai.agent.data.ZaiRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Manual DI container. We keep it tiny on purpose — no Hilt/Koin dependency
 * means the build is faster and the APK is smaller, which is what we want
 * for a CI-built artifact.
 */
class ZaiApplication : Application() {

    lateinit var sessionStore: SessionStore
        private set

    lateinit var httpClient: OkHttpClient
        private set

    lateinit var repository: ZaiRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        sessionStore = SessionStore(this)

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
            else HttpLoggingInterceptor.Level.NONE
        }

        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .build()

        repository = ZaiRepository(httpClient, sessionStore, AppJson)
    }

    companion object {
        lateinit var instance: ZaiApplication
            private set
    }
}
