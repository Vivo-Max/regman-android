package com.regman.core.crypto

/**
 * Kiro 请求/响应体加密（对齐 KiroX 的 internal/crypto）。
 * XXTEA 为标准公开算法，此处完整实现；
 * JWE 提供紧凑序列化解析，解密所需的密钥方案见 [CAPTURE] 注释。
 */
object Xxtea {
    private const val DELTA = -0x61c88647  // 0x9E3779B9

    private fun mx(sum: Int, y: Int, z: Int, p: Int, e: Int, k: IntArray): Int =
        (((z ushr 5) xor (y shl 2)) + ((y ushr 3) xor (z shl 4))) xor ((sum xor y) + (k[(p and 3) xor e] xor z))

    private fun keyInts(key: ByteArray): IntArray {
        val k = ByteArray(16)
        key.copyInto(k, 0, 0, minOf(key.size, 16))
        return IntArray(4) { i ->
            (k[i * 4].toInt() and 0xFF) or ((k[i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((k[i * 4 + 2].toInt() and 0xFF) shl 16) or ((k[i * 4 + 3].toInt() and 0xFF) shl 24)
        }
    }

    private fun toInts(buf: ByteArray): IntArray = IntArray(buf.size / 4) { i ->
        (buf[i * 4].toInt() and 0xFF) or ((buf[i * 4 + 1].toInt() and 0xFF) shl 8) or
                ((buf[i * 4 + 2].toInt() and 0xFF) shl 16) or ((buf[i * 4 + 3].toInt() and 0xFF) shl 24)
    }

    private fun toBytes(v: IntArray): ByteArray = ByteArray(v.size * 4) { i ->
        (v[i / 4] ushr ((i % 4) * 8)).toByte()
    }

    fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        val k = keyInts(key)
        val pad = (4 - data.size % 4) % 4
        val buf = data + ByteArray(pad)
        val v = toInts(buf).copyOf(buf.size / 4 + 1)
        v[v.size - 1] = data.size
        val n = v.size
        if (n < 2) return toBytes(v)
        var rounds = 6 + 52 / n
        var sum = 0
        var z = v[n - 1]
        do {
            sum += DELTA
            val e = sum ushr 2 and 3
            var p = 0
            while (p < n - 1) {
                val y = v[p + 1]
                v[p] += mx(sum, y, z, p, e, k)
                z = v[p]
                p++
            }
            val y = v[0]
            v[n - 1] += mx(sum, y, z, n - 1, e, k)
            z = v[n - 1]
        } while (--rounds > 0)
        return toBytes(v)
    }

    fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        val k = keyInts(key)
        val v = toInts(data).copyOf()
        val n = v.size
        if (n < 2) return toBytes(v)
        var rounds = 6 + 52 / n
        var sum = rounds * DELTA
        var y = v[0]
        do {
            val e = sum ushr 2 and 3
            var p = n - 1
            while (p > 0) {
                val z = v[p - 1]
                v[p] -= mx(sum, y, z, p, e, k)
                y = v[p]
                p--
            }
            val z = v[n - 1]
            v[0] -= mx(sum, y, z, 0, e, k)
            y = v[0]
            sum -= DELTA
        } while (--rounds > 0)
        val out = toBytes(v)
        val len = v[n - 1]
        return if (len in 0 until out.size) out.copyOf(len) else out
    }
}

object Jwe {
    data class Parsed(
        val rawHeader: String, val rawEncryptedKey: String,
        val rawIv: String, val rawCiphertext: String, val rawTag: String,
    )

    fun parse(jwe: String): Parsed? {
        val p = jwe.split('.')
        if (p.size != 5) return null
        return Parsed(p[0], p[1], p[2], p[3], p[4])
    }

    fun b64url(s: String): ByteArray = java.util.Base64.getUrlDecoder().decode(s)

    /** [CAPTURE] KiroX 使用特定密钥方案解 JWE；移植其 internal/crypto 的具体实现后补全 */
    fun decodeClaims(jwe: String): String? {
        val p = parse(jwe) ?: return null
        return runCatching { String(b64url(p.rawHeader), Charsets.UTF_8) }.getOrNull()
    }
}
