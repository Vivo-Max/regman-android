package com.regman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.regman.app.vm.OverviewViewModel

@Composable
fun OverviewScreen(vm: OverviewViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("概览", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(listOf("总账号 ${s.total}", "存活 ${s.active}", "运行中任务 ${s.running}")) { label ->
                ElevatedCard(Modifier.width(140.dp)) {
                    Text(label, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("错误归因（按失败次数）", style = MaterialTheme.typography.titleSmall)
        // TODO: 从 TaskEntity.errorKindJson 渲染饼图
    }
}
