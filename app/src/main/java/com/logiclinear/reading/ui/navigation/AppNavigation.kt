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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.logiclinear.reading.R
import com.logiclinear.reading.ui.analysis.AnalysisEntry
import com.logiclinear.reading.ui.book.BookDetailEntry
import com.logiclinear.reading.ui.book.BookFormEntry
import com.logiclinear.reading.ui.discussion.DiscussionEntry
import com.logiclinear.reading.ui.home.HomeEntry
import com.logiclinear.reading.ui.library.LibraryEntry
import com.logiclinear.reading.ui.search.BookSearchEntry
import com.logiclinear.reading.ui.select.SelectEntry
import com.logiclinear.reading.ui.settings.SettingsEntry

/**
 * 앱 전체 네비게이션. 시작 화면은 카메라 홈(요구사항 "첫 화면"). 하단 탭(서재·분석·추천·설정)은 홈과 탭 화면에서 보인다.
 * 바깥 Scaffold가 인셋을 계산하고 `consumeWindowInsets`로 소비해 안쪽 화면의 Scaffold가 인셋을 두 번 넣지 않게 한다.
 */
@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = destination.hasRouteOf(HomeRoute::class) || TopLevelRoute.entries.any { destination.isOn(it) }

    Scaffold(
        bottomBar = { if (showBottomBar) BottomTabs(destination) { navController.navigateTopLevel(it) } },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable<HomeRoute> {
                HomeEntry(
                    onRecognized = { navController.navigate(SelectRoute) },
                    onGoToLibrary = { navController.navigateTopLevel(TopLevelRoute.LIBRARY) },
                )
            }
            composable<SelectRoute> { SelectEntry(onDone = { navController.popBackStack<HomeRoute>(inclusive = false) }) }
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
                    // 설정도 위에 쌓아 키를 넣고 뒤로가기로 검색에 돌아온다.
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<AnalysisRoute> { AnalysisEntry(onOpenSettings = { navController.navigateTopLevel(TopLevelRoute.SETTINGS) }) }
            composable<SettingsRoute> { SettingsEntry() }
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
                )
            }
            composable<DiscussionRoute> {
                DiscussionEntry(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigateTopLevel(TopLevelRoute.SETTINGS) },
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

private fun NavDestination?.hasRouteOf(route: kotlin.reflect.KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(route) } == true

/** 탭 전환: 백스택을 시작 화면(카메라)까지 비우고 상태를 보존해 탭마다 하나의 인스턴스만 둔다. */
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
