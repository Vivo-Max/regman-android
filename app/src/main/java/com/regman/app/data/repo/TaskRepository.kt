package com.regman.app.data.repo

import com.regman.app.data.db.TaskDao
import com.regman.app.data.db.TaskEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(private val dao: TaskDao) {
    fun observeAll() = dao.observeAll()
    suspend fun clear() = dao.clear()
}
