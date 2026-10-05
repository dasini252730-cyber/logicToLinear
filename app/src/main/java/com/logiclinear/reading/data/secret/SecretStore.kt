package com.logiclinear.reading.data.secret

import kotlinx.coroutines.flow.Flow

/** 앱이 보관하는 비밀. 요구사항 "기술 스택": 설정 화면에서 사용자가 입력, 코드에 하드코딩하지 않음, 백업에 넣지 않음. */
enum class SecretKey(val prefName: String) {
    /** 카카오 책 검색 REST API 키(이전 공급자 OpenAPI 종료로 교체, 2026-10-05). */
    KAKAO_REST("kakao_rest_key"),
    ANTHROPIC_API("anthropic_api_key"),
}

/**
 * 비밀 값 저장소. 실제 구현은 [EncryptedSecretStore]. 테스트는 [InMemorySecretStore].
 * 값은 로그·백업·크래시 리포트에 쓰지 않는다.
 */
interface SecretStore {
    fun get(key: SecretKey): String?

    /** 빈 문자열이나 null이면 지운다. 앞뒤 공백은 정리한다. */
    fun set(key: SecretKey, value: String?)

    /** 설정 화면이 저장 여부 표시에 쓴다. 값 자체가 아니라 존재 여부만 노출하는 쪽이 안전하다. */
    fun observeIsSet(key: SecretKey): Flow<Boolean>
}

fun SecretStore.isSet(key: SecretKey): Boolean = !get(key).isNullOrBlank()
