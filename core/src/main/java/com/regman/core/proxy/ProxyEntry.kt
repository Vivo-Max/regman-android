package com.regman.core.proxy

sealed class ProxyProtocol { object HTTP : ProxyProtocol(); object SOCKS5 : ProxyProtocol() }

data class ProxyEntry(
    val id: String,
    val host: String,
    val port: Int,
    val protocol: ProxyProtocol,
    val username: String? = null,
    val password: String? = null,
    val weight: Int = 1,
    val consecutiveFails: Int = 0,
    val disabled: Boolean = false,
) {
    fun url(): String = buildString {
        append(if (protocol is ProxyProtocol.SOCKS5) "socks5" else "http"); append("://")
        if (username != null) { append(username); append(':'); append(password ?: ""); append('@') }
        append(host); append(':'); append(port)
    }
}
