package com.regman.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY createdAt DESC") fun observeAll(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts WHERE platform = :platform") fun observeByPlatform(platform: String): Flow<List<AccountEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(a: AccountEntity)
    @Delete suspend fun delete(a: AccountEntity)
    @Query("SELECT COUNT(*) FROM accounts") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM accounts WHERE status = 'ACTIVE'") suspend fun activeCount(): Int
    @Query("SELECT * FROM accounts") suspend fun all(): List<AccountEntity>
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC") fun observeAll(): Flow<List<TaskEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(t: TaskEntity)
    @Query("DELETE FROM tasks") suspend fun clear()
}

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxies") fun observeAll(): Flow<List<ProxyEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: ProxyEntity)
    @Delete suspend fun delete(p: ProxyEntity)
}

@Dao
interface MailboxDao {
    @Query("SELECT * FROM mailboxes") fun observeAll(): Flow<List<MailboxEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(m: MailboxEntity)
    @Delete suspend fun delete(m: MailboxEntity)
}
