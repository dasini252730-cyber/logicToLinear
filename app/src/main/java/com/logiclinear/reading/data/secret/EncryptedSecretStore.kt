package com.logiclinear.reading.data.secret

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/**
 * EncryptedSharedPreferences 기반 저장소(요구사항 "기술 스택"). 키는 Android Keystore의 마스터 키로 암호화된다.
 * 파일은 앱 데이터 영역에만 있고 백업 규칙(allowBackup=false, data_extraction_rules)으로 클라우드 백업·기기 이전에서 제외된다.
 * security-crypto는 Google이 유지보수 종료를 예고했지만 요구사항이 이 방식을 명시했고 1.1.0 안정판이 있어 그대로 쓴다.
 */
class EncryptedSecretStore(context: Context) : SecretStore {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun get(key: SecretKey): String? = prefs.getString(key.prefName, null)?.takeIf { it.isNotBlank() }

    override fun set(key: SecretKey, value: String?) {
        val trimmed = value?.trim().orEmpty()
        prefs.edit {
            if (trimmed.isEmpty()) remove(key.prefName) else putString(key.prefName, trimmed)
        }
    }

    override fun observeIsSet(key: SecretKey): Flow<Boolean> = callbackFlow {
        trySend(isSet(key))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key.prefName) trySend(isSet(key))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO) // 첫 접근의 Keystore 초기화·복호화를 메인 스레드에서 떼어낸다

    private companion object {
        const val FILE_NAME = "secrets"
    }
}
