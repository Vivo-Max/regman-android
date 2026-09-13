package com.regman.core.crypto

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/** RFC 6238 TOTP：本地 2FA，无需第三方应用 */
object Totp {
    fun now(base32Secret: String, stepSeconds: Long = 30, digits: Int = 6): String {
        val key = base32Decode(base32Secret)
        val counter = System.currentTimeMillis() / 1000 / stepSeconds
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretSpecFix(key)) }.doFinal(msg)
        val offset = (mac.last() and 0x0F).toInt()
        val code = (ByteBuffer.wrap(mac, offset, 4).int and 0x7FFFFFFF) % pow10(digits)
        return code.toString().padStart(digits, '0')
    }

    private fun SecretSpecFix(key: ByteArray) = SecretKeySpec(key, "HmacSHA1")
    private fun pow10(n: Int): Int = (1..n).fold(1) { a, _ -> a * 10 }

    fun base32Decode(s: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0; var value = 0
        return s.uppercase().filter { it != '=' && it != ' ' }.map { alphabet.indexOf(it) }
            .fold(mutableListOf<Byte>()) { acc, v ->
                value = (value shl 5) or v; bits += 5
                if (bits >= 8) { bits -= 8; acc += ((value ushr bits) and 0xFF).toByte() }
                acc
            }.toByteArray()
    }
}
