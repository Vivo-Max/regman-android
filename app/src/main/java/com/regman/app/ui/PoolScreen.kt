package com.regman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.regman.app.data.db.AccountEntity
import com.regman.app.vm.PoolViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 账号池：对应参考工具的卡片式账号管理界面 */
@Composable
fun PoolScreen(vm: PoolViewModel = hiltViewModel()) {
    val accounts by vm.accounts.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(accounts, key = { it.id }) { a -> AccountCard(a, onRefresh = { vm.refresh(a) }, onDelete = { vm.delete(a) }) }
    }
}

@Composable
private fun AccountCard(a: AccountEntity, onRefresh: () -> Unit, onDelete: () -> Unit) {
    val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.email, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(a.status)
            }
            Text("有效期至 ${df.format(Date(a.expiresAt))}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            QuotaBar("5.3-Flash", 0.97f)
            QuotaBar("5.3", 0.62f)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh) { Text("刷新额度") }
                TextButton(onClick = { /* TODO: 切换=导出 token 给目标客户端 */ }) { Text("切换") }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun QuotaBar(name: String, progress: Float) {
    Column {
        Row { Text(name, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.weight(1f)); Text("${(progress * 100).toInt()}%") }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = when (status) {
        "ACTIVE" -> MaterialTheme.colorScheme.primary
        "EXPIRED_SOON" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    SuggestionChip(onClick = {}, label = { Text(status, color = color) })
}
