package com.duynd.uthsynctask.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.duynd.uthsynctask.ui.navigation.MainTab
import com.duynd.uthsynctask.ui.notifications.NotificationSettingsScreen
import com.duynd.uthsynctask.ui.schedule.ScheduleScreen
import com.duynd.uthsynctask.ui.settings.SettingsScreen

@Composable
fun MainShellScreen(
    onLogout: () -> Unit
) {
    val tabNavController = rememberNavController()

    Scaffold(
        bottomBar = { UthBottomBar(tabNavController) }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = MainTab.Schedule.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainTab.Schedule.route) {
                ScheduleScreen(
                    onNavigateToSettings = {
                        tabNavController.navigate(MainTab.Settings.route) {
                            popUpTo(tabNavController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(MainTab.Notifications.route) {
                NotificationSettingsScreen()
            }
            composable(MainTab.Settings.route) {
                SettingsScreen(onLoggedOut = onLogout)
            }
        }
    }
}

@Composable
private fun UthBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        MainTab.items.forEach { tab ->
            val icon = when (tab) {
                MainTab.Schedule -> Icons.Filled.DateRange
                MainTab.Notifications -> Icons.Filled.Notifications
                MainTab.Settings -> Icons.Filled.Settings
            }
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute != tab.route) {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}
