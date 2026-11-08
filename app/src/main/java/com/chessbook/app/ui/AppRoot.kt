package com.chessbook.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chessbook.app.ui.screens.AboutScreen
import com.chessbook.app.ui.screens.AnalysisScreen
import com.chessbook.app.ui.screens.HomeScreen
import com.chessbook.app.ui.screens.PlayScreen
import com.chessbook.app.ui.screens.PremiumScreen
import com.chessbook.app.ui.screens.ToolsScreen

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest(Routes.HOME, "Home", Icons.Default.Home),
    Dest(Routes.PLAY, "Play", Icons.Default.PlayArrow),
    Dest(Routes.ANALYSIS, "Analysis", Icons.Default.Search),
    Dest(Routes.TOOLS, "Tools", Icons.Default.Settings),
    Dest(Routes.PREMIUM, "Premium", Icons.Default.Star),
    Dest(Routes.ABOUT, "About", Icons.Default.Info),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    fun go(r: String) {
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (d in DESTS) {
                    NavigationBarItem(
                        selected = route == d.route,
                        onClick = { go(d.route) },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
        ) {
            composable(Routes.HOME) { HomeScreen(onNavigate = ::go) }
            composable(Routes.PLAY) { PlayScreen(onAnalyse = { go(Routes.ANALYSIS) }) }
            composable(Routes.ANALYSIS) { AnalysisScreen(onGoPremium = { go(Routes.PREMIUM) }) }
            composable(Routes.TOOLS) { ToolsScreen() }
            composable(Routes.PREMIUM) { PremiumScreen() }
            composable(Routes.ABOUT) { AboutScreen() }
        }
    }
}
