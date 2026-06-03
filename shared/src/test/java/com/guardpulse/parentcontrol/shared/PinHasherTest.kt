package com.guardpulse.parentcontrol.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun verifiesOnlyMatchingPin() {
        val created = PinHasher.create("123456")

        assertTrue(PinHasher.verify("123456", created.salt, created.hash))
        assertFalse(PinHasher.verify("654321", created.salt, created.hash))
        assertFalse(PinHasher.verify("", created.salt, created.hash))
    }
}
