package com.logiclinear.reading.data.secret

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 테스트·미리보기용. Keystore 없이 동작한다. 프로덕션 코드에서는 쓰지 않는다. */
class InMemorySecretStore(initial: Map<SecretKey, String> = emptyMap()) : SecretStore {
    private val values = MutableStateFlow(initial)

    override fun get(key: SecretKey): String? = values.value[key]?.takeIf { it.isNotBlank() }

    override fun set(key: SecretKey, value: String?) {
        val trimmed = value?.trim().orEmpty()
        values.value = if (trimmed.isEmpty()) values.value - key else values.value + (key to trimmed)
    }

    override fun observeIsSet(key: SecretKey): Flow<Boolean> = values.map { !it[key].isNullOrBlank() }
}
