package com.munch.reddit.data.auth.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PkceUtils {
    private val secureRandom = SecureRandom()

    fun generateCodeVerifier(): String {
        val code = ByteArray(64)
        secureRandom.nextBytes(code)
        val encoded = Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return encoded.take(96).padEnd(43, 'A')
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateState(): String {
        val code = ByteArray(16)
        secureRandom.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
