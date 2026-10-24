package com.logiclinear.reading.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.logiclinear.reading.R
import com.logiclinear.reading.ui.analysis.AnalysisEntry
import com.logiclinear.reading.ui.book.BookDetailEntry
import com.logiclinear.reading.ui.book.BookFormEntry
import com.logiclinear.reading.ui.capture.CaptureEntry
import com.logiclinear.reading.ui.discussion.DiscussionEntry
import com.logiclinear.reading.ui.library.LibraryEntry
import com.logiclinear.reading.ui.search.BookSearchEntry
import com.logiclinear.reading.ui.select.SelectEntry
import com.logiclinear.reading.ui.settings.SettingsEntry

/**
 * 앱 전체 네비게이션. 시작 화면은 서재(2026-10-05 사용자 결정, T-806). 하단 탭(서재·분석·설정)은 탭 화면에서 보인다.
 * 카메라는 책 상세 "글귀 찍기"로만 연다([CaptureRoute]).
 * 바깥 Scaffold가 인셋을 계산하고 `consumeWindowInsets`로 소비해 안쪽 화면의 Scaffold가 인셋을 두 번 넣지 않게 한다.
 */
@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    // 검색·토론 위에 쌓인 설정(T-809·T-810)은 탭 화면이 아니라 뒤로가기로 나가는 하위 화면이므로 탭을 숨긴다.
    val showBottomBar = TopLevelRoute.entries.any { destination.isOn(it) } && backStackEntry?.isStackedSettings() != true

    Scaffold(
        bottomBar = { if (showBottomBar) BottomTabs(destination) { navController.navigateTopLevel(it) } },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LibraryRoute,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            // 책 전용 카메라(T-806). 책 id는 ViewModel이 라우트에서 읽는다. 뒤로 가면 책 상세.
            // 고정한 책이 READING에서 빠지면 "읽는 중인 책을 먼저 등록하세요" 버튼이 상세로 돌려보낸다.
            composable<CaptureRoute> {
                CaptureEntry(
                    onRecognized = { navController.navigate(SelectRoute) },
                    onBack = { navController.popBackStack() },
                )
            }
            // 저장·취소는 문장 선택 화면만 걷어 바로 아래의 책 전용 카메라로 돌아간다.
            composable<SelectRoute> { SelectEntry(onDone = { navController.popBackStack() }) }
            composable<LibraryRoute> {
                LibraryEntry(
                    onBookClick = { navController.navigate(BookDetailRoute(it)) },
                    onAddClick = { navController.navigate(BookSearchRoute) },
                )
            }
            composable<BookSearchRoute> {
                BookSearchEntry(
                    onBack = { navController.popBackStack() },
                    onRegistered = { navController.popBackStack<LibraryRoute>(inclusive = false) },
                    // 검색 실패·오프라인·직접 입력: 직접 입력 폼으로 전환(요구사항 "예외 처리"). 검색 화면은 백스택에 남겨
                    // 뒤로가기로 검색어만 고칠 수 있게 한다(리뷰 반영). 저장되면 폼이 서재까지 팝한다.
                    onManualEntry = { query -> navController.navigate(BookFormRoute(initialTitle = query.ifBlank { null })) },
                    // 설정을 검색 위에 쌓아 키를 넣고 뒤로가기로 검색에 돌아온다. stacked로 하단 탭을 숨겨(T-809)
                    // 탭 전환의 popUpTo(서재)가 검색 화면과 검색어를 걷어내지 못하게 한다.
                    onOpenSettings = { navController.navigateStackedSettings() },
                )
            }
            composable<AnalysisRoute> { AnalysisEntry(onOpenSettings = { navController.navigateTopLevel(TopLevelRoute.SETTINGS) }) }
            composable<SettingsRoute> { entry ->
                val stacked = entry.toRoute<SettingsRoute>().stacked
                // 설정 엔트리 자신까지만 팝한다. 빠르게 두 번 눌러도 둘째 호출은 팝할 설정이 없어 아래 화면(검색·토론)이 걷히지 않는다.
                SettingsEntry(onBack = if (stacked) ({ navController.popBackStack<SettingsRoute>(inclusive = true) }) else null)
            }
            // 저장·삭제 완료는 "서재까지" 팝한다. 사용자가 그 사이 뒤로를 눌러 이미 서재에 있으면 아무 일도 하지 않는다.
            composable<BookFormRoute> {
                BookFormEntry(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack<LibraryRoute>(inclusive = false) },
                )
            }
            composable<BookDetailRoute> {
                BookDetailEntry(
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack<LibraryRoute>(inclusive = false) },
                    onOpenDiscussion = { id, fresh -> navController.navigate(DiscussionRoute(id, fresh)) },
                    onCaptureQuote = { bookId -> navController.navigate(CaptureRoute(bookId)) },
                )
            }
            composable<DiscussionRoute> {
                DiscussionEntry(
                    onBack = { navController.popBackStack() },
                    // 탭 전환(navigateTopLevel)은 popUpTo(서재){saveState}가 책 상세·토론을 서재 id에 매달아 서재 탭이
                    // 토론을 복원하는 문제가 있어(T-810), 검색과 같이 토론 위에 쌓고 뒤로가기로 돌아온다.
                    onOpenSettings = { navController.navigateStackedSettings() },
                )
            }
        }
    }
}

@Composable
private fun BottomTabs(destination: NavDestination?, onSelect: (TopLevelRoute) -> Unit) {
    NavigationBar {
        TopLevelRoute.entries.forEach { tab ->
            NavigationBarItem(
                selected = destination.isOn(tab),
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(stringResource(tab.labelRes())) },
            )
        }
    }
}

private fun NavDestination?.isOn(tab: TopLevelRoute): Boolean = hasRouteOf(tab.route::class)

private fun NavBackStackEntry.isStackedSettings(): Boolean =
    destination.hasRoute<SettingsRoute>() && toRoute<SettingsRoute>().stacked

private fun NavDestination?.hasRouteOf(route: kotlin.reflect.KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(route) } == true

/**
 * 검색·토론 위에 설정을 하위 화면으로 쌓는다(T-809·T-810). launchSingleTop으로 "설정으로"를 빠르게 두 번 눌러도
 * 설정이 두 겹 쌓이지 않는다(두 겹이면 뒤로가기 한 번에 설정이 또 뜬다).
 */
private fun NavHostController.navigateStackedSettings() {
    navigate(SettingsRoute(stacked = true)) { launchSingleTop = true }
}

/** 탭 전환: 백스택을 시작 화면(서재)까지 비우고 상태를 보존해 탭마다 하나의 인스턴스만 둔다. */
private fun NavHostController.navigateTopLevel(tab: TopLevelRoute) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun TopLevelRoute.labelRes(): Int = when (this) {
    TopLevelRoute.LIBRARY -> R.string.tab_library
    TopLevelRoute.ANALYSIS -> R.string.tab_analysis
    TopLevelRoute.SETTINGS -> R.string.tab_settings
}

private fun TopLevelRoute.icon(): ImageVector = when (this) {
    TopLevelRoute.LIBRARY -> Icons.Filled.Menu
    TopLevelRoute.ANALYSIS -> Icons.Filled.Star
    TopLevelRoute.SETTINGS -> Icons.Filled.Settings
}
