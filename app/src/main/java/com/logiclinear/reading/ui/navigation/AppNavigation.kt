package com.logiclinear.reading.ui.navigation

import androidx.compose.foundation.layout.padding
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.logiclinear.reading.R
import com.logiclinear.reading.ui.analysis.AnalysisScreen
import com.logiclinear.reading.ui.home.HomeScreen
import com.logiclinear.reading.ui.library.LibraryEntry
import com.logiclinear.reading.ui.settings.SettingsScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star

/**
 * 앱 전체 네비게이션. 시작 화면은 카메라 홈(요구사항 "첫 화면"). 하단 탭은 최상위 라우트에서만 보인다.
 * Scaffold가 상태바·내비바 인셋을 처리한다(T-101 리뷰 인계).
 */
@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TopLevelRoute.entries.any { destination.isOn(it) }

    Scaffold(
        bottomBar = { if (showBottomBar) BottomTabs(destination) { navController.navigateTopLevel(it) } },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<HomeRoute> { HomeScreen() }
            composable<LibraryRoute> {
                LibraryEntry(
                    onBookClick = { navController.navigate(BookDetailRoute(it)) },
                    onAddClick = { navController.navigate(BookFormRoute) },
                )
            }
            composable<AnalysisRoute> { AnalysisScreen() }
            composable<SettingsRoute> { SettingsScreen() }
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

private fun NavDestination?.isOn(tab: TopLevelRoute): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.route::class) } == true

private val NavDestination.hierarchy: Sequence<NavDestination>
    get() = generateSequence(this) { it.parent }

/** 탭 전환: 백스택을 홈까지 비우고 상태를 보존해 탭마다 하나의 인스턴스만 둔다. */
private fun NavHostController.navigateTopLevel(tab: TopLevelRoute) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun TopLevelRoute.labelRes(): Int = when (this) {
    TopLevelRoute.HOME -> R.string.tab_camera
    TopLevelRoute.LIBRARY -> R.string.tab_library
    TopLevelRoute.ANALYSIS -> R.string.tab_analysis
    TopLevelRoute.SETTINGS -> R.string.tab_settings
}

private fun TopLevelRoute.icon(): ImageVector = when (this) {
    TopLevelRoute.HOME -> Icons.Filled.Home
    TopLevelRoute.LIBRARY -> Icons.Filled.Menu
    TopLevelRoute.ANALYSIS -> Icons.Filled.Star
    TopLevelRoute.SETTINGS -> Icons.Filled.Settings
}
