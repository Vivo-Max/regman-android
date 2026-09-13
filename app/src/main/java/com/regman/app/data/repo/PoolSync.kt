package com.regman.app.data.repo

import com.regman.app.data.db.AppDatabase
import com.regman.app.data.db.MailboxEntity
import com.regman.app.data.db.ProxyEntity
import com.regman.core.http.HttpClient
import com.regman.core.mailbox.MailboxProvider
import com.regman.core.mailbox.MailboxPool
import com.regman.core.mailbox.impl.HttpApiMailbox
import com.regman.core.mailbox.impl.ImapMailbox
import com.regman.core.proxy.ProxyEntry
import com.regman.core.proxy.ProxyPool
import com.regman.core.proxy.ProxyProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** 数据库变更 → 引擎池同步（代理池 / 邮箱池） */
@Singleton
class PoolSync @Inject constructor(
    private val db: AppDatabase,
    private val proxyPool: ProxyPool,
    private val mailboxPool: MailboxPool,
    private val http: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun attach(scope: CoroutineScope) {
        scope.launch {
            db.proxyDao().observeAll().collect { list ->
                proxyPool.upsertAll(list.map { it.toEntry() })
            }
        }
        scope.launch {
            db.mailboxDao().observeAll().collect { list ->
                mailboxPool.replaceAll(list.mapNotNull { runCatching { it.toProvider() }.getOrNull() })
            }
        }
    }

    private fun ProxyEntity.toEntry(): ProxyEntry {
        val uri = java.net.URI(url)
        val proto = if (uri.scheme == "socks5") ProxyProtocol.SOCKS5 else ProxyProtocol.HTTP
        val user = uri.userInfo?.substringBefore(':')
        val pass = uri.userInfo?.substringAfter(':', "")
        return ProxyEntry(id, uri.host ?: "", if (uri.port > 0) uri.port else 1080,
            proto, user, pass?.ifEmpty { null }, weight, consecutiveFails, disabled)
    }

    private fun MailboxEntity.toProvider(): MailboxProvider {
        val c = json.parseToJsonElement(configJson).jsonObject
        fun s(k: String) = c[k]?.jsonPrimitive?.content ?: ""
        return when (type) {
            "IMAP" -> ImapMailbox(
                name = name,
                host = s("host"),
                port = s("port").toIntOrNull() ?: 993,
                username = s("username"),
                password = s("password"),
                secure = s("secure").ifEmpty { "true" }.toBooleanStrictOrNull() ?: true,
            )
            else -> HttpApiMailbox(
                name = name,
                http = http,
                baseUrl = s("baseUrl"),
                createPath = s("createPath").ifEmpty { "/generate" },
                messagesPath = s("messagesPath").ifEmpty { "/auth/{token}" },
            )
        }
    }
}
