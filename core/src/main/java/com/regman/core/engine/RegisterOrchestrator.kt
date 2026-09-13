package com.regman.core.engine

import com.regman.core.captcha.CaptchaProvider
import com.regman.core.http.ClientFingerprint
import com.regman.core.mailbox.MailboxPool
import com.regman.core.platform.PlatformPlugin
import com.regman.core.proxy.ProxyPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 远程打码 Provider 的持有者：设置页保存 key 后由 app 层注入 */
class CaptchaHolder { @Volatile var current: CaptchaProvider? = null }

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
    private val mailboxPool: MailboxPool,
    private val captchaHolder: CaptchaHolder,
    private val onAccount: suspend (AccountDraft) -> Unit,
    private val fingerprint: ClientFingerprint = ClientFingerprint(),
) : RegisterOrchestrator {

    private val _tasks = MutableStateFlow<List<TaskState>>(emptyList())
    override val tasks = _tasks.asStateFlow()

    @Volatile private var paused = false

    override suspend fun enqueue(platform: PlatformPlugin, count: Int, concurrency: Int): String {
        val taskId = "task-" + System.currentTimeMillis()
        _tasks.value += TaskState(taskId, platform.name, count, running = true)

        val gate = Semaphore(concurrency.coerceAtLeast(1))
        scope.launch(Dispatchers.IO) {
            val jobs = (1..count).map { index ->
                scope.async {
                    gate.withPermit {
                        if (paused) { update(taskId) { copy(done = done + 1) }; return@withPermit }
                        val proxy = proxyPool.acquire()
                        val mailbox = mailboxPool.acquire()
                        val log: suspend (StepEvent) -> Unit = { e -> emit(e) }
                        try {
                            val ctx = RegisterContext(taskId, mailbox, captchaHolder.current, proxy, fingerprint, log)
                            when (val r = runCatching { platform.register(ctx) }.getOrElse { e ->
                                RegisterResult.Failure(Failure(ErrorKind.UNKNOWN, "exception", e.message ?: e.toString()))
                            }) {
                                is RegisterResult.Success -> {
                                    proxyPool.reportSuccess(proxy)
                                    onAccount(r.draft)
                                    update(taskId) { copy(done = done + 1, succeeded = succeeded + 1) }
                                    emit(StepEvent(taskId, "完成", r.draft.email))
                                }
                                is RegisterResult.Failure -> {
                                    proxyPool.reportFailure(proxy, r.failure.kind)
                                    update(taskId) { copy(done = done + 1) }
                                    emit(StepEvent(taskId, "失败[${r.failure.kind}]", r.failure.detail))
                                }
                            }
                        } finally {
                            runCatching { mailboxPool.release(mailbox) }
                        }
                    }
                }
            }
            jobs.awaitAll()
            update(taskId) { copy(running = false) }
            emit(StepEvent(taskId, "任务结束", ""))
        }
        return taskId
    }

    private suspend fun emit(e: StepEvent) {
        _tasks.value = _tasks.value.map { if (it.taskId == e.taskId) it.copy(log = (it.log + e).takeLast(200)) else it }
    }

    private fun update(taskId: String, block: TaskState.() -> TaskState) {
        _tasks.value = _tasks.value.map { if (it.taskId == taskId) it.block() else it }
    }

    override fun pauseAll() { paused = true }
    override fun resumeAll() { paused = false }
}
