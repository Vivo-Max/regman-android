package com.regman.app.data.http

import android.content.Context
import com.regman.core.http.CookieStore
import com.regman.core.http.HttpClient
import com.regman.core.http.InMemoryCookieStore
import com.regman.core.proxy.ProxyEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.chromium.net.CronetEngine
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cronet(Chromium 网络栈) 实现：TLS 指纹与 Android Chrome 同源。
 * 全应用共享一个 CookieStore（SSO 会话）；每代理一个 engine 实例。
 */
@Singleton
class CronetHttpClientFactory @Inject constructor(
    private val context: Context,
) {
    private val executor = Executors.newCachedThreadPool()
    private val cookieStore: CookieStore = InMemoryCookieStore()

    fun create(proxy: ProxyEntry?): HttpClient = CronetHttpClient(buildEngine(proxy), executor, cookieStore)

    private fun buildEngine(proxy: ProxyEntry?): CronetEngine {
        val b = CronetEngine.Builder(context)
            .enableHttp2(true)
            .enableQuic(true)
            .enableBrotli(true)
            .setUserAgent(UA)
        // TODO: CronetEngine 支持设置代理时在此注入 proxy.url()；否则通过 Cronet 的 ProxyController 做全局代理
        return b.build()
    }

    companion object {
        // 对齐桌面 Chrome 119；可经 RemoteConfig 热更新
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
    }
}

private class CronetHttpClient(
    private val engine: CronetEngine,
    private val executor: java.util.concurrent.Executor,
    private val cookies: CookieStore,
) : HttpClient {

    private fun request(
        url: String, method: String, body: String?, headers: Map<String, String>,
        onDone: (String, String) -> Unit, onError: (Throwable) -> Unit,
    ): UrlRequest {
        val rb = engine.newUrlRequestBuilder(url, object : UrlRequest.Callback() {
            private val sb = StringBuilder()
            override fun onRedirectReceived(req: UrlRequest, info: UrlResponseInfo, loc: String) = req.followRedirect()
            override fun onResponseStarted(req: UrlRequest, info: UrlResponseInfo) {
                runCatching { cookies.saveFrom(url, info.allHeaders.getOrDefault("Set-Cookie", emptyList())) }
                req.read(ByteBuffer.allocateDirect(64 * 1024))
            }
            override fun onReadCompleted(req: UrlRequest, info: UrlResponseInfo, buf: ByteBuffer) {
                buf.flip()
                sb.append(Charsets.UTF_8.decode(buf))
                buf.clear()
                req.read(buf)
            }
            override fun onSucceeded(req: UrlRequest, info: UrlResponseInfo) = onDone(sb.toString(), info.url)
            override fun onFailed(req: UrlRequest, info: UrlResponseInfo?, error: org.chromium.net.CronetException) = onError(error)
            override fun onCanceled(req: UrlRequest, info: UrlResponseInfo?) = onError(java.util.concurrent.CancellationException())
        }, executor).setHttpMethod(method)

        val cookieHeader = cookies.loadFor(url).entries.joinToString("; ") { "${it.key}=${it.value}" }
        if (cookieHeader.isNotEmpty()) rb.addHeader("Cookie", cookieHeader)
        headers.forEach { (k, v) -> rb.addHeader(k, v) }
        body?.let { rb.setUploadDataProvider(org.chromium.net.UploadDataProviders.create(it.toByteArray()), executor) }
        val req = rb.build()
        req.start()
        return req
    }

    private suspend fun exec(url: String, method: String, body: String?, headers: Map<String, String>): Pair<String, String> =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val req = request(url, method, body, headers,
                    onDone = { b, u -> if (cont.isActive) cont.resume(b to u) },
                    onError = { if (cont.isActive) cont.resumeWithException(it) })
                cont.invokeOnCancellation { runCatching { req.cancel() } }
            }
        }

    override suspend fun getString(url: String, headers: Map<String, String>): String = exec(url, "GET", null, headers).first
    override suspend fun getStringWithUrl(url: String, headers: Map<String, String>): Pair<String, String> = exec(url, "GET", null, headers)
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String =
        exec(url, "POST", body, headers + mapOf("Content-Type" to "application/json")).first
    override suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String>): String =
        exec(url, "POST", form.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" },
            headers + mapOf("Content-Type" to "application/x-www-form-urlencoded")).first

    override fun withProxy(proxy: ProxyEntry): HttpClient =
        throw UnsupportedOperationException("通过 CronetHttpClientFactory.create(proxy) 获取带代理实例")
}
