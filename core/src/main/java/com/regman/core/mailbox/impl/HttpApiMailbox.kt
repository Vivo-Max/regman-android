package com.regman.core.mailbox.impl

import com.regman.core.http.HttpClient
import com.regman.core.mailbox.MailboxProvider
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 一类临时邮箱 HTTP API 的通用骨架（TempMail/Moemail 等），按实际 API 补全字段解析 */
class HttpApiMailbox(
    override val name: String,
    private val http: HttpClient,
    private val baseUrl: String,          // 如 https://api.tempmail.lol
    private val createPath: String = "/generate",
    private val messagesPath: String = "/auth/{token}",   // 占位
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MailboxProvider {

    @Volatile private var token: String = ""

    override suspend fun allocate(): String {
        val resp = http.getString(baseUrl + createPath)
        val obj = json.parseToJsonElement(resp).jsonObject
        token = obj["token"]?.jsonPrimitive?.content ?: ""
        return obj["address"]?.jsonPrimitive?.content
            ?: obj["email"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("临时邮箱 API 返回结构不符: $resp")
    }

    override suspend fun waitForCode(address: String, timeoutMillis: Long, extract: (String) -> String?): String? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            runCatching {
                val resp = http.getString(baseUrl + messagesPath.replace("{token}", token))
                json.parseToJsonElement(resp).jsonArray.forEach { m ->
                    val body = m.jsonObject["body"]?.jsonPrimitive?.content
                        ?: m.jsonObject["text"]?.jsonPrimitive?.content ?: return@forEach
                    extract(body)?.let { return it }
                }
            }
            delay(3_000)
        }
        return null
    }

    override suspend fun release(address: String) = Unit
}
