package com.nexo.smartnotes.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** 本地账号密码哈希（PBKDF2-HMAC-SHA256，120000 次迭代）。 */
object Passwords {

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(password: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encoded = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(encoded, Base64.NO_WRAP)
    }

    fun verify(password: String, hash: String, saltBase64: String): Boolean {
        val candidate = hash(password, saltBase64)
        if (candidate.length != hash.length) return false
        var diff = 0
        for (i in hash.indices) diff = diff or (candidate[i].code xor hash[i].code)
        return diff == 0
    }
}
