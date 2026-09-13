package com.regman.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.regman.app.data.db.AccountEntity
import com.regman.app.data.repo.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PoolViewModel @Inject constructor(
    private val repo: AccountRepository,
) : ViewModel() {
    val accounts = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh(a: AccountEntity) = viewModelScope.launch { repo.refreshQuota(a) }
    fun delete(a: AccountEntity) = viewModelScope.launch { repo.delete(a) }
    fun export() = viewModelScope.launch { repo.exportJson() }
}
