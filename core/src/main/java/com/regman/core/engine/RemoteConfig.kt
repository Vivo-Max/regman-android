package com.regman.core.engine

import com.regman.core.http.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 平台注册参数的云端热更新：
 * 平台改版时只改远端 JSON，不发版。首次拉取失败时回退到内置资源。
 */
class RemoteConfig(
    private val http: HttpClient,
    private val fallbackJson: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: JsonObject? = null

    suspend fun get(url: String): JsonObject {
        cache?.let { return it }
        val remote = runCatching {
            json.parseToJsonElement(http.getString(url)).let { it as JsonObject }
        }.getOrNull()
        cache = remote ?: json.parseToJsonElement(fallbackJson) as JsonObject
        return cache!!
    }

    fun invalidate() { cache = null }
}
