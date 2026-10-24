package com.logiclinear.reading.data.secret

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SecretStore 계약. EncryptedSecretStore는 Keystore가 필요해 실기기(T-508)에서 확인하고, 여기서는 계약만 고정한다. */
class InMemorySecretStoreTest {
    @Test
    fun 공백을_정리해_저장하고_빈_값은_지운다() = runTest {
        val store = InMemorySecretStore()
        store.set(SecretKey.NL_OPENAPI, "  nl-key  ")
        assertEquals("nl-key", store.get(SecretKey.NL_OPENAPI))
        assertTrue(store.observeIsSet(SecretKey.NL_OPENAPI).first())

        store.set(SecretKey.NL_OPENAPI, "   ")
        assertNull(store.get(SecretKey.NL_OPENAPI))
        assertFalse(store.isSet(SecretKey.NL_OPENAPI))
    }

    @Test
    fun 키마다_독립이다() {
        val store = InMemorySecretStore(mapOf(SecretKey.ANTHROPIC_API to "sk"))
        assertTrue(store.isSet(SecretKey.ANTHROPIC_API))
        assertFalse(store.isSet(SecretKey.NL_OPENAPI))
    }
}
