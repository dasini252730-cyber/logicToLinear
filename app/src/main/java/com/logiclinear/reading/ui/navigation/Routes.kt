package com.logiclinear.reading.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 모든 라우트는 여기에만 선언한다(rules/android.md). Navigation Compose 타입 안전 라우트.
 * 하단 탭 4개(카메라·서재·분석·추천·설정)는 [TopLevelRoute]에 순서대로 둔다.
 */
@Serializable
object HomeRoute

@Serializable
object LibraryRoute

@Serializable
object AnalysisRoute

@Serializable
object SettingsRoute

/** 책 상세(T-111). */
@Serializable
data class BookDetailRoute(val bookId: Long)

/** 직접 입력 책 등록(T-110). 알라딘 검색(T-303)이 생기면 그 화면이 먼저 뜨고 여기로 넘어온다. */
@Serializable
object BookFormRoute

/** 하단 탭에 노출되는 최상위 라우트. */
enum class TopLevelRoute(val route: Any) {
    HOME(HomeRoute),
    LIBRARY(LibraryRoute),
    ANALYSIS(AnalysisRoute),
    SETTINGS(SettingsRoute),
}
