package com.guardpulse.parentcontrol.shared

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class PinHash(
    val salt: String,
    val hash: String
)

object PinHasher {
    private val random = SecureRandom()

    fun create(pin: String): PinHash {
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        val salt = Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes)
        return PinHash(salt = salt, hash = hash(pin, salt))
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        if (pin.isBlank() || salt.isBlank() || expectedHash.isBlank()) return false
        return MessageDigest.isEqual(
            hash(pin, salt).toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    private fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
