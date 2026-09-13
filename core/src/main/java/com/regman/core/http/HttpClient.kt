package com.regman.core.http

import com.regman.core.proxy.ProxyEntry

/** 注册主链路 HTTP 抽象。app 层提供 Cronet 实现（见 CronetHttpClient）。 */
interface HttpClient {
    suspend fun getString(url: String, headers: Map<String, String> = emptyMap()): String
    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String
    suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): String
    fun withProxy(proxy: ProxyEntry): HttpClient
}

/**
 * 浏览器指纹配置。TLS 层由 Cronet(Chromium 网络栈) 天然提供接近 Chrome 的指纹；
 * 应用层头由本数据类驱动，可经 RemoteConfig 热更新。
 */
data class ClientFingerprint(
    val userAgent: String,
    val acceptLanguage: String = "zh-CN,zh;q=0.9,en;q=0.8",
    val secChUa: String = "\"Chromium\";v=\"119\", \"Google Chrome\";v=\"119\", \"Not?A_Brand\";v=\"24\"",
    val secChUaMobile: String = "?0",
    val secChUaPlatform: String = "\"Windows\"",
) {
    fun headers(): Map<String, String> = mapOf(
        "User-Agent" to userAgent,
        "Accept-Language" to acceptLanguage,
        "sec-ch-ua" to secChUa,
        "sec-ch-ua-mobile" to secChUaMobile,
        "sec-ch-ua-platform" to secChUaPlatform,
    )
}
