package com.regman.core.http

/** 极简 Cookie 存储：SSO 授权链跨请求携带会话 Cookie */
interface CookieStore {
    fun loadFor(url: String): Map<String, String>
    fun saveFrom(url: String, setCookies: List<String>)
}

class InMemoryCookieStore : CookieStore {
    private val jar = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, String>>()

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull() ?: url

    override fun loadFor(url: String): Map<String, String> =
        jar[hostOf(url)]?.toMap() ?: emptyMap()

    override fun saveFrom(url: String, setCookies: List<String>) {
        val m = jar.getOrPut(hostOf(url)) { mutableMapOf() }
        setCookies.forEach { sc ->
            val pair = sc.substringBefore(';')
            val i = pair.indexOf('=')
            if (i > 0) m[pair.substring(0, i).trim()] = pair.substring(i + 1).trim()
        }
    }
}
