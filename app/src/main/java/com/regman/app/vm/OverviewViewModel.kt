package com.regman.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.regman.app.data.db.AccountDao
import com.regman.app.data.db.TaskDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class OverviewState(val total: Int = 0, val active: Int = 0, val running: Int = 0)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    accountDao: AccountDao,
    taskDao: TaskDao,
) : ViewModel() {
    val state = combine(
        kotlinx.coroutines.flow.flow { emit(accountDao.count()) },
        kotlinx.coroutines.flow.flow { emit(accountDao.activeCount()) },
        taskDao.observeAll(),
    ) { total, active, tasks ->
        OverviewState(total, active, tasks.count { it.status == "RUNNING" })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewState())
}
