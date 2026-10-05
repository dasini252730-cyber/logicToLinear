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

/** 알라딘 제목 검색으로 책 등록(T-303). 서재 +에서 들어온다. */
@Serializable
object BookSearchRoute

/**
 * 직접 입력 책 등록(T-110). 알라딘 검색(T-303)이 먼저 뜨고, 검색 실패·오프라인·"직접 입력" 버튼이면
 * 여기로 넘어온다(요구사항 "예외 처리": 직접 입력 폼으로 전환). [initialTitle]에 그때의 검색어가 들어와 제목을 미리 채운다(T-305).
 */
@Serializable
data class BookFormRoute(val initialTitle: String? = null)

/**
 * 완독 후 토론 화면(T-703). [fresh]가 true면 방금 만든 토론이라 첫 질문을 자동 요청한다(요구사항: 첫 메시지는 앱이 요청).
 * 책 상세 목록에서 다시 열 때는 false — 첫 질문이 없으면 버튼으로 다시 요청한다.
 */
@Serializable
data class DiscussionRoute(val discussionId: Long, val fresh: Boolean = false)

/** 하단 탭에 노출되는 라우트. 카메라 홈은 탭이 아니지만 하단 탭은 홈에서도 보인다. */
enum class TopLevelRoute(val route: Any) {
    LIBRARY(LibraryRoute),
    ANALYSIS(AnalysisRoute),
    SETTINGS(SettingsRoute),
}
