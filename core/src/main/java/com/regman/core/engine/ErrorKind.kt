package com.regman.core.engine

/** 失败归因：概览页错误统计与代理池加权轮询共用 */
enum class ErrorKind { NONE, PROXY_BLOCKED, CAPTCHA_FAILED, MAILBOX_ERROR, RISK_CONTROL, SECOND_FACTOR, RATE_LIMITED, UNKNOWN }

data class StepEvent(
    val taskId: String,
    val step: String,
    val message: String,
    val atMillis: Long = System.currentTimeMillis(),
)

data class Failure(
    val kind: ErrorKind,
    val step: String,
    val detail: String,
)
