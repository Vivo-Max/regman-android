package com.regman.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.regman.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    val captchaKey = settings.captchaKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val remoteUrl = settings.remoteConfigUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun saveCaptcha(v: String) = viewModelScope.launch { settings.setCaptchaKey(v.trim()) }
    fun saveRemoteUrl(v: String) = viewModelScope.launch { settings.setRemoteConfigUrl(v.trim()) }
}
