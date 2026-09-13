package com.regman.core.mailbox

/** 邮箱 Provider 抽象：申请地址 / 等待验证码 / 释放 */
interface MailboxProvider {
    val name: String

    suspend fun allocate(): String                       // 返回可收信的地址
    suspend fun waitForCode(address: String, timeoutMillis: Long, extract: (String) -> String?): String?
    suspend fun release(address: String)
}

class MailboxException(msg: String) : Exception(msg)
