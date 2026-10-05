package com.logiclinear.reading.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 모든 라우트는 여기에만 선언한다(rules/android.md). Navigation Compose 타입 안전 라우트.
 * 시작 화면은 [HomeRoute](카메라). 하단 탭은 요구사항 표대로 서재·분석·추천·설정 3개([TopLevelRoute]).
 * 카메라로 돌아가는 길은 시스템 뒤로가기다: 탭 전환이 시작 화면까지 popUpTo하므로 뒤로 한 번이면 카메라다.
 */
@Serializable
object HomeRoute

@Serializable
object LibraryRoute

@Serializable
object AnalysisRoute

@Serializable
object SettingsRoute

/** 촬영 직후 문장 선택(T-204). OCR 결과는 CaptureStore에 있어 인자가 없다. */
@Serializable
object SelectRoute

/** 책 상세(T-111). */
@Serializable
data class BookDetailRoute(val bookId: Long)

/**
 * 직접 입력 책 등록(T-110). 알라딘 검색(T-303)이 생기면 그 화면이 먼저 뜨고, 검색 실패·오프라인이면
 * 여기로 넘어온다(요구사항 "예외 처리": 직접 입력 폼으로 전환). [initialTitle]은 그때 검색어를 미리 채우기 위한
 * 자리로, 백로그 T-305 설명에 따른 것이다. 현재 호출자는 null만 넘긴다.
 */
@Serializable
data class BookFormRoute(val initialTitle: String? = null)

/** 하단 탭에 노출되는 라우트. 카메라 홈은 탭이 아니지만 하단 탭은 홈에서도 보인다. */
enum class TopLevelRoute(val route: Any) {
    LIBRARY(LibraryRoute),
    ANALYSIS(AnalysisRoute),
    SETTINGS(SettingsRoute),
}
