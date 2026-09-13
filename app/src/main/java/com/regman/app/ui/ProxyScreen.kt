package com.regman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.regman.app.data.db.AppDatabase
import com.regman.app.data.db.ProxyEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProxyViewModel @Inject constructor(db: AppDatabase) : androidx.lifecycle.ViewModel() {
    val items = db.proxyDao().observeAll()
        .stateIn(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default), SharingStarted.WhileSubscribed(5_000), emptyList())
    private val dao = db.proxyDao()
    fun add(url: String) = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        dao.upsert(ProxyEntity(UUID.randomUUID().toString(), url))
    }
    fun delete(p: ProxyEntity) = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { dao.delete(p) }
    fun test(p: ProxyEntity) { /* TODO: HEAD 请求测延迟，结果写回 weight */ }
}

@Composable
fun ProxyScreen(vm: ProxyViewModel = hiltViewModel()) {
    val items by vm.items.collectAsState()
    var url by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row {
            OutlinedTextField(url, { url = it }, label = { Text("http://user:pass@host:port") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (url.isNotBlank()) { vm.add(url); url = "" } }) { Text("添加") }
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(items, key = { it.id }) { p ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(p.url)
                            Text("权重 ${p.weight}  连续失败 ${p.consecutiveFails}${if (p.disabled) "  [已禁用]" else ""}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { vm.test(p) }) { Text("测速") }
                        TextButton(onClick = { vm.delete(p) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
