package com.regman.core.mailbox

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 邮箱池：DB 变更后整体替换；加权轮询分配 */
class MailboxPool(providers: List<MailboxProvider> = emptyList()) {
    private val mutex = Mutex()
    private val available = ArrayDeque(providers)

    suspend fun acquire(): MailboxProvider = mutex.withLock {
        available.removeFirstOrNull() ?: throw MailboxException("邮箱池为空，请先在邮箱池页面添加")
    }

    suspend fun release(p: MailboxProvider) = mutex.withLock { available.addLast(p) }

    suspend fun replaceAll(providers: List<MailboxProvider>) = mutex.withLock {
        available.clear()
        available.addAll(providers)
    }
}
