package com.regman.core.mailbox.impl

import com.regman.core.mailbox.MailboxProvider
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** IMAP 账号池中的一个邮箱账号（自有域名/Outlook/iCloud 均可） */
class ImapMailbox(
    override val name: String,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val secure: Boolean = true,
) : MailboxProvider {

    override suspend fun allocate(): String = username

    override suspend fun waitForCode(address: String, timeoutMillis: Long, extract: (String) -> String?): String? =
        withContext(Dispatchers.IO) {
            val props = Properties().apply {
                put("mail.store.protocol", "imap")
                put("mail.imap.host", host)
                put("mail.imap.port", port.toString())
                if (secure) {
                    put("mail.imap.ssl.enable", "true")
                    put("mail.imap.ssl.checkserveridentity", "true")
                }
            }
            val store: Store = Session.getInstance(props).getStore()
            store.connect(username, password)
            try {
                val inbox: Folder = store.getFolder("INBOX").apply { open(Folder.READ_ONLY) }
                val seen = mutableSetOf<String>()
                val deadline = System.currentTimeMillis() + timeoutMillis
                while (System.currentTimeMillis() < deadline) {
                    inbox.getMessageCount().let { count ->
                        (count downTo 1).forEach { i ->
                            val m = inbox.getMessage(i)
                            val id = m.getHeader("Message-ID")?.firstOrNull() ?: return@forEach
                            if (id !in seen) {
                                seen += id
                                (extract(m.content.toString()))?.let { return@withContext it }
                            }
                        }
                    }
                    delay(3_000)
                }
                null
            } finally {
                runCatching { store.close() }
            }
        }

    override suspend fun release(address: String) = Unit
}
