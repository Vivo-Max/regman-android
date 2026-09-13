package com.regman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.regman.app.vm.RegisterViewModel

@Composable
fun RegisterScreen(vm: RegisterViewModel = hiltViewModel()) {
    var platform by remember { mutableStateOf("kiro") }
    var count by remember { mutableStateOf("1") }
    var concurrency by remember { mutableStateOf("1") }
    val tasks by vm.tasks.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("批量注册", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(platform, { platform = it }, label = { Text("平台") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(count, { count = it.filter(Char::isDigit) }, label = { Text("注册数量") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(concurrency, { concurrency = it.filter(Char::isDigit) }, label = { Text("并发数") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.start(platform, count.toIntOrNull() ?: 1, concurrency.toIntOrNull() ?: 1) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("开始注册") }
        Spacer(Modifier.height(16.dp))
        tasks.forEach { t ->
            Text("${t.platform}: ${t.done}/${t.total} (成功 ${t.succeeded})")
            t.log.takeLast(20).forEach { Text("[${it.step}] ${it.message}", style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
    }
}
