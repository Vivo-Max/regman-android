package com.regman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.regman.app.vm.TasksViewModel

@Composable
fun TasksScreen(vm: TasksViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsState()
    val live by vm.orchestrator.tasks.collectAsState()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.pauseAll() }) { Text("全部暂停") }
            TextButton(onClick = { vm.clear() }) { Text("清空历史") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(live, key = { it.taskId }) { t ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${t.platform}  ${t.succeeded}/${t.done}/${t.total}  ${if (t.running) "运行中" else "结束"}")
                        t.log.takeLast(5).forEach { Text("[${it.step}] ${it.message}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            items(tasks, key = { it.id }) { t ->
                Text("${t.platform}  ${t.succeeded}/${t.total}  ${t.status}")
            }
        }
    }
}
