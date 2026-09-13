package com.regman.core.engine

import com.regman.core.platform.PlatformPlugin
import com.regman.core.proxy.ProxyPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class TaskState(
    val taskId: String,
    val platform: String,
    val total: Int,
    val done: Int = 0,
    val succeeded: Int = 0,
    val running: Boolean = false,
    val log: List<StepEvent> = emptyList(),
)

interface RegisterOrchestrator {
    val tasks: StateFlow<List<TaskState>>
    suspend fun enqueue(platform: PlatformPlugin, count: Int, concurrency: Int): String
    fun pauseAll()
    fun resumeAll()
}

class DefaultRegisterOrchestrator(
    private val scope: CoroutineScope,
    private val proxyPool: ProxyPool,
    private val onAccount: suspend (AccountDraft) -> Unit,   // 入库回调（app 层注入）
) : RegisterOrchestrator {

    private val _tasks = MutableStateFlow<List<TaskState>>(emptyList())
    override val tasks = _tasks.asStateFlow()

    private var paused = false
    private val pauseGate = kotlinx.coroutines.sync.Mutex()

    override suspend fun enqueue(platform: PlatformPlugin, count: Int, concurrency: Int): String {
        val taskId = "task-" + System.currentTimeMillis()
        update(taskId) { copy(platform = platform.name, total = count, running = true) }

        val gate = Semaphore(concurrency)
        scope.async(Dispatchers.IO) {
            val jobs = (1..count).map { index ->
                scope.async {
                    gate.withPermit {
                        if (paused) pauseGate.lock().also { pauseGate.unlock() } // 简化占位：暂停语义见 TaskQueue 注释
                        val proxy = proxyPool.acquire()
                        val ctx = buildContext(taskId, index, proxy)
                        emit(taskId, "申请邮箱", "第 $index 个")
                        when (val r = runCatching { platform.register(ctx) }.getOrElse { e ->
                            RegisterResult.Failure(Failure(ErrorKind.UNKNOWN, "exception", e.message ?: ""))
                        }) {
                            is RegisterResult.Success -> {
                                proxyPool.reportSuccess(proxy)
                                onAccount(r.draft)
                                update(taskId) { copy(done = done + 1, succeeded = succeeded + 1) }
                                emit(taskId, "完成", r.draft.email)
                            }
                            is RegisterResult.Failure -> {
                                proxyPool.reportFailure(proxy, r.failure.kind)
                                update(taskId) { copy(done = done + 1) }
                                emit(taskId, "失败[${r.failure.kind}]", r.failure.detail)
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
            update(taskId) { copy(running = false) }
        }
        return taskId
    }

    private suspend fun buildContext(taskId: String, index: Int, proxy: com.regman.core.proxy.ProxyEntry?): RegisterContext {
        // MailboxProvider / CaptchaProvider 由 app 层经工厂注入到 platform 插件内部使用；
        // 此处仅示意上下文组装点。
        TODO("由 app 层注入 MailboxPool/CaptchaProvider 后组装")
    }

    private suspend fun emit(taskId: String, step: String, message: String) {
        val e = StepEvent(taskId, step, message)
        _tasks.value = _tasks.value.map { if (it.taskId == taskId) it.copy(log = (it.log + e).takeLast(200)) else it }
    }

    private fun update(taskId: String, block: TaskState.() -> TaskState) {
        _tasks.value = _tasks.value.map { if (it.taskId == taskId) it.block() else it }
    }

    override fun pauseAll() { paused = true }
    override fun resumeAll() { paused = false }
}
