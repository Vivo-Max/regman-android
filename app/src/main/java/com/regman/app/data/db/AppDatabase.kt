package com.regman.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, TaskEntity::class, ProxyEntity::class, MailboxEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun taskDao(): TaskDao
    abstract fun proxyDao(): ProxyDao
    abstract fun mailboxDao(): MailboxDao
}
