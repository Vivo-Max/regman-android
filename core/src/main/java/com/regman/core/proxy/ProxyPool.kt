package com.regman.core.proxy

import com.regman.core.engine.ErrorKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 静态代理池：成功率加权轮询，连续失败 5 次自动禁用（对齐 any-auto-register 的代理池语义） */
class ProxyPool(initial: List<ProxyEntry> = emptyList()) {

    private val mutex = Mutex()
    private val entries = mutableListOf<ProxyEntry>()
    private var cursor = 0

    init { entries += initial }

    suspend fun upsertAll(list: List<ProxyEntry>) = mutex.withLock {
        entries.clear(); entries += list; cursor = 0
    }

    suspend fun acquire(): ProxyEntry? = mutex.withLock {
        val alive = entries.filter { !it.disabled }
        if (alive.isEmpty()) return@withLock null
        // 加权轮询：权重越高被选中越多
        val total = alive.sumOf { it.weight.coerceAtLeast(1) }
        var r = (cursor++ % total) + 1
        var pick = alive.first()
        for (e in alive) { r -= e.weight.coerceAtLeast(1); if (r <= 0) { pick = e; break } }
        pick
    }

    suspend fun reportSuccess(proxy: ProxyEntry?) {
        if (proxy == null) return
        mutex.withLock {
            val i = entries.indexOfFirst { it.id == proxy.id }
            if (i >= 0) entries[i] = entries[i].copy(consecutiveFails = 0, weight = (entries[i].weight + 1).coerceAtMost(10))
        }
    }

    suspend fun reportFailure(proxy: ProxyEntry?, kind: ErrorKind) {
        if (proxy == null) return
        mutex.withLock {
            val i = entries.indexOfFirst { it.id == proxy.id }
            if (i >= 0) {
                val fails = entries[i].consecutiveFails + 1
                entries[i] = entries[i].copy(
                    consecutiveFails = fails,
                    weight = (entries[i].weight - 1).coerceAtLeast(1),
                    disabled = fails >= 5,
                )
            }
        }
    }

    suspend fun snapshot(): List<ProxyEntry> = mutex.withLock { entries.toList() }
}
