package com.regman.core.crypto

/** 敏感字段加解密抽象；core 保持纯 Kotlin，app 层用 Android Keystore 实现 */
interface AccountCipher {
    fun encrypt(plain: String): ByteArray
    fun decrypt(cipher: ByteArray): String
}
