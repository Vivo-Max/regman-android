package com.regman.core.platform

/** 启动时扫描注册；app 层通过 Hilt 多绑定注入全部插件 */
class PlatformRegistry(private val plugins: Set<@JvmSuppressWildcards PlatformPlugin>) {
    fun all(): List<PlatformPlugin> = plugins.toList()
    fun get(name: String): PlatformPlugin? = plugins.firstOrNull { it.name == name }
}
