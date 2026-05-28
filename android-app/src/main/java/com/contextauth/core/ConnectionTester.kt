package com.contextauth.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ConnectionTester(private val client: OkHttpClient) {
    suspend fun testHealth(serverUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(serverUrl.trimEnd('/') + "/ready")
                .get()
                .build()
            val started = System.nanoTime()
            client.newCall(request).execute().use { response ->
                val rtt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                val body = response.body?.string()?.take(120).orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}, ${rtt}ms")
                "HTTP ${response.code}, ${rtt}ms, ${body.ifBlank { "ok" }}"
            }
        }
    }
}
