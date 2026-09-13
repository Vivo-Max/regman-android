package com.regman.app.vm

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    // TODO: DataStore 持久化打码 key / 远端配置地址 / 通知开关
}
