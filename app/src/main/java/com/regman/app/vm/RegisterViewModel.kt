package com.regman.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.regman.core.engine.RegisterOrchestrator
import com.regman.core.platform.PlatformRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registry: PlatformRegistry,
    private val orchestrator: RegisterOrchestrator,
) : ViewModel() {
    val platforms get() = registry.all()
    val tasks = orchestrator.tasks

    fun start(platformName: String, count: Int, concurrency: Int) {
        val plugin = registry.get(platformName) ?: return
        viewModelScope.launch { orchestrator.enqueue(plugin, count, concurrency) }
    }
}
