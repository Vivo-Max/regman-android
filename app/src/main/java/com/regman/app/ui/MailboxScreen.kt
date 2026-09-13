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
import com.regman.app.data.db.MailboxEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MailboxViewModel @Inject constructor(db: AppDatabase) : androidx.lifecycle.ViewModel() {
    val items = db.mailboxDao().observeAll()
        .stateIn(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default), SharingStarted.WhileSubscribed(5_000), emptyList())
    private val dao = db.mailboxDao()
    fun add(type: String, name: String, config: String) = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        dao.upsert(MailboxEntity(UUID.randomUUID().toString(), type, name, config))
    }
    fun delete(m: MailboxEntity) = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { dao.delete(m) }
}

@Composable
fun MailboxScreen(vm: MailboxViewModel = hiltViewModel()) {
    val items by vm.items.collectAsState()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Button(onClick = { vm.add("IMAP", "新邮箱", "{}") }, modifier = Modifier.fillMaxWidth()) { Text("+ 添加邮箱 Provider") }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(items, key = { it.id }) { m ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp)) {
                        Column(Modifier.weight(1f)) { Text(m.name); Text(m.type, style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { vm.delete(m) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
