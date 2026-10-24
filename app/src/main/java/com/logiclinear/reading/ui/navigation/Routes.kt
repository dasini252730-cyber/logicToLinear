package com.logiclinear.reading.ui.navigation

import kotlinx.serialization.Serializable

// 모든 라우트는 여기에만 선언한다(rules/android.md). Navigation Compose 타입 안전 라우트.
// 시작 화면은 LibraryRoute(서재). 하단 탭은 서재·분석·설정(TopLevelRoute). 분석 화면 안에 추천 섹션이 있다.
// 2026-10-05 사용자 결정(T-806): 홈 카메라를 없애고 카메라는 책 안(CaptureRoute)에서만 연다.

/**
 * 책 상세 "글귀 찍기"(T-806): 그 책 전용 카메라. 책을 바꿀 수 없고 하단 탭이 없으며,
 * 저장 뒤에는 여기로 돌아오고 뒤로 가면 책 상세다. 앱에서 카메라가 뜨는 유일한 경로다.
 */
@Serializable
data class CaptureRoute(val bookId: Long)

@Serializable
object LibraryRoute

@Serializable
object AnalysisRoute

/**
 * 설정(T-501). 보통은 하단 탭으로 들어오고, [stacked]가 true면 책 검색(T-809)·토론(T-810)의 "설정으로"에서
 * 그 화면 위에 하위 화면으로 쌓인 것이다. 그때는 하단 탭을 숨기고 뒤로가기로만 나가게 해, 탭 전환의
 * popUpTo(서재)가 아래 화면(검색어·토론)을 걷어내거나 saveState로 서재 탭에 매달지 않게 한다.
 */
@Serializable
data class SettingsRoute(val stacked: Boolean = false)

/** 촬영 직후 문장 선택(T-204). OCR 결과는 CaptureStore에 있어 인자가 없다. */
@Serializable
object SelectRoute

/** 책 상세(T-111). */
@Serializable
data class BookDetailRoute(val bookId: Long)

/** 제목 검색(국립중앙도서관 소장자료 검색)으로 책 등록(T-303). 서재 +에서 들어온다. */
@Serializable
object BookSearchRoute

/**
 * 직접 입력 책 등록(T-110). 책 검색(T-303)이 먼저 뜨고, 검색 실패·오프라인·"직접 입력" 버튼이면
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

/** 하단 탭에 노출되는 라우트. 서재가 시작 화면이다. */
enum class TopLevelRoute(val route: Any) {
    LIBRARY(LibraryRoute),
    ANALYSIS(AnalysisRoute),
    SETTINGS(SettingsRoute()),
}
