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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 账号池：卡片式账号管理（额度条数据来自 queryQuota） */
@Composable
fun PoolScreen(vm: PoolViewModel = hiltViewModel()) {
    val accounts by vm.accounts.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(accounts, key = { it.id }) { a -> AccountCard(a, onRefresh = { vm.refresh(a) }, onDelete = { vm.delete(a) }) }
    }
}

private data class QuotaRow(val name: String, val total: Long, val used: Long)

@Composable
private fun AccountCard(a: AccountEntity, onRefresh: () -> Unit, onDelete: () -> Unit) {
    val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val quotas = remember(a.quotaJson) { parseQuotas(a.quotaJson) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.email, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(a.status)
            }
            if (a.expiresAt > 0) Text("有效期至 ${df.format(Date(a.expiresAt))}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (quotas.isEmpty()) {
                Text("暂无额度数据（点刷新）", style = MaterialTheme.typography.bodySmall)
            } else quotas.forEach { q ->
                QuotaBar(q.name, if (q.total > 0) q.used.toFloat() / q.total else 0f, q.used, q.total)
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh) { Text("刷新额度") }
                TextButton(onClick = { /* TODO: 分享 token 给目标客户端 */ }) { Text("切换") }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun parseQuotas(jsonStr: String): List<QuotaRow> = runCatching {
    Json.parseToJsonElement(jsonStr).jsonArray.map { q ->
        val o = q.jsonObject
        QuotaRow(
            o["name"]?.jsonPrimitive?.content ?: "?",
            o["total"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            o["used"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        )
    }
}.getOrDefault(emptyList())

@Composable
private fun QuotaBar(name: String, progress: Float, used: Long, total: Long) {
    Column {
        Row {
            Text(name, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text("$used / $total  (${(progress * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
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
