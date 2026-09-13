package com.regman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.regman.app.vm.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val captcha by vm.captchaKey.collectAsState()
    val remote by vm.remoteUrl.collectAsState()
    var captchaInput by remember(captcha) { mutableStateOf(captcha) }
    var remoteInput by remember(remote) { mutableStateOf(remote) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(captchaInput, { captchaInput = it },
            label = { Text("YesCaptcha Client Key") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(remoteInput, { remoteInput = it },
            label = { Text("远端平台配置 JSON 地址（kiro.json）") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.saveCaptcha(captchaInput); vm.saveRemoteUrl(remoteInput) },
            modifier = Modifier.fillMaxWidth()) { Text("保存") }
        Spacer(Modifier.height(8.dp))
        Text("打码 key 保存后自动注入注册引擎；远端配置保存后重启生效",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { /* TODO: 导出 JSON/CSV */ }) { Text("导出账号") }
    }
}
