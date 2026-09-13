package com.regman.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.regman.core.crypto.AccountCipher
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES/GCM + Android Keystore：token/密码密文落盘，密钥不出安全硬件 */
class KeystoreAccountCipher : AccountCipher {

    private val keyAlias = "regman_account_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    override fun encrypt(plain: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain.toByteArray())
    }

    override fun decrypt(cipherBytes: ByteArray): String {
        val iv = cipherBytes.copyOfRange(0, 12)
        val body = cipherBytes.copyOfRange(12, cipherBytes.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(body))
    }

    /** SQLCipher 口令：用主密钥派生并持久化到私有目录（首次生成） */
    fun dbPassphrase(): ByteArray {
        // TODO: 用 DataStore 保存 Keystore 加密后的随机口令；此处返回固定长度随机字节
        return ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
    }
}
