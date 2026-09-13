package com.regman.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.regman.app.data.repo.TaskRepository
import com.regman.core.engine.RegisterOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repo: TaskRepository,
    val orchestrator: RegisterOrchestrator,
) : ViewModel() {
    val tasks = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pauseAll() = orchestrator.pauseAll()
    fun clear() = viewModelScope.launch { repo.clear() }
}
