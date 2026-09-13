package com.regman.core.captcha

import com.regman.core.http.HttpClient
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class YesCaptcha(private val http: HttpClient, private val clientKey: String) : CaptchaProvider {
    override val name = "yescaptcha"
    private val json = Json { ignoreUnknownKeys = true }
    private val api = "https://api.yescaptcha.com"

    override suspend fun solveHcaptcha(siteKey: String, pageUrl: String): String? =
        solve("HCaptchaTaskProxyless", siteKey, pageUrl)

    override suspend fun solveTurnstile(siteKey: String, pageUrl: String): String? =
        solve("TurnstileTaskProxyless", siteKey, pageUrl)

    private suspend fun solve(taskType: String, siteKey: String, pageUrl: String): String? {
        val create = http.postJson("$api/createTask", """
            {"clientKey":"$clientKey","task":{"type":"$taskType","websiteKey":"$siteKey","websiteURL":"$pageUrl"}}
        """.trimIndent())
        val taskId = json.parseToJsonElement(create).jsonObject["taskId"]?.jsonPrimitive?.content
            ?: return null
        repeat(40) {
            delay(3_000)
            runCatching {
                val r = http.postJson("$api/getTaskResult", """{"clientKey":"$clientKey","taskId":"$taskId"}""")
                val obj = json.parseToJsonElement(r).jsonObject
                if (obj["status"]?.jsonPrimitive?.content == "ready") {
                    return obj["solution"]?.jsonObject?.get("token")?.jsonPrimitive?.content
                        ?: obj["solution"]?.jsonObject?.get("gRecaptchaResponse")?.jsonPrimitive?.content
                }
            }
        }
        return null
    }
}
