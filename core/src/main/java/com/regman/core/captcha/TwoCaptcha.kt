package com.regman.core.captcha

import com.regman.core.http.HttpClient
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TwoCaptcha(private val http: HttpClient, private val apiKey: String) : CaptchaProvider {
    override val name = "2captcha"
    private val json = Json { ignoreUnknownKeys = true }
    private val api = "https://api.2captcha.com"

    override suspend fun solveHcaptcha(siteKey: String, pageUrl: String): String? =
        solve("HCaptchaTaskProxyless", siteKey, pageUrl)

    override suspend fun solveTurnstile(siteKey: String, pageUrl: String): String? =
        solve("TurnstileTaskProxyless", siteKey, pageUrl)

    private suspend fun solve(taskType: String, siteKey: String, pageUrl: String): String? {
        val create = http.postJson("$api/createTask", """
            {"clientKey":"$apiKey","task":{"type":"$taskType","websiteKey":"$siteKey","websiteURL":"$pageUrl"}}
        """.trimIndent())
        val taskId = json.parseToJsonElement(create).jsonObject["taskId"]?.jsonPrimitive?.content ?: return null
        repeat(40) {
            delay(3_000)
            runCatching {
                val r = http.postJson("$api/getTaskResult", """{"clientKey":"$apiKey","taskId":"$taskId"}""")
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
