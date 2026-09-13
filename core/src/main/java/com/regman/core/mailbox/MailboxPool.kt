package com.regman.core.mailbox

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 邮箱池：加权轮询分配，账号注册成功后由上层决定 release 时机 */
class MailboxPool(providers: List<MailboxProvider>) {
    private val mutex = Mutex()
    private val available = ArrayDeque(providers)

    suspend fun acquire(): MailboxProvider = mutex.withLock {
        available.removeFirstOrNull() ?: throw MailboxException("邮箱池耗尽")
    }

    suspend fun release(p: MailboxProvider) = mutex.withLock { available.addLast(p) }
}
