package com.regman.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val email: String,
    val passwordCipher: ByteArray,
    val accessTokenCipher: ByteArray,
    val refreshTokenCipher: ByteArray,
    val plan: String = "",
    val quotaJson: String = "{}",     // [{"name":"5.3-Flash","total":100000000,"used":0}]
    val expiresAt: Long = 0L,
    val status: String = "ACTIVE",    // ACTIVE / INVALID / RISK / EXPIRED_SOON
    val proxyId: String? = null,
    val tag: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val total: Int,
    val succeeded: Int,
    val status: String,               // RUNNING / DONE / PAUSED
    val errorKindJson: String = "{}", // 错误归因计数
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey val id: String,
    val url: String,
    val weight: Int = 1,
    val disabled: Boolean = false,
    val consecutiveFails: Int = 0,
)

@Entity(tableName = "mailboxes")
data class MailboxEntity(
    @PrimaryKey val id: String,
    val type: String,                 // IMAP / HTTP_API
    val name: String,
    val configJson: String,           // 主机/账号或 API 地址
)
