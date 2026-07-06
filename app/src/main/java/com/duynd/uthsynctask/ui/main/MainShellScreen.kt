package com.duynd.uthsynctask.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.duynd.uthsynctask.data.local.SecureCredentialStore
import com.duynd.uthsynctask.ui.navigation.MainTab
import kotlinx.coroutines.launch

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
                ComingSoonPlaceholder(
                    icon = Icons.Filled.DateRange,
                    title = "Lịch học & Deadline",
                    description = "Danh sách bài tập/deadline đồng bộ từ Courses và thnn UTH sẽ hiển thị ở đây. Tính năng đang được xây dựng ở phần Đồng bộ tiếp theo."
                )
            }
            composable(MainTab.Notifications.route) {
                ComingSoonPlaceholder(
                    icon = Icons.Filled.Notifications,
                    title = "Thông báo",
                    description = "Cài đặt và lịch sử nhắc nhở deadline sẽ có ở đây. Tính năng đang được xây dựng."
                )
            }
            composable(MainTab.Settings.route) {
                SettingsPlaceholder(onLogout = onLogout)
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

@Composable
private fun ComingSoonPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsPlaceholder(onLogout: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Cài đặt",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Đổi tài khoản UTH, kết nối Google Calendar và tuỳ chỉnh thông báo sẽ có ở phần tiếp theo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        SecureCredentialStore(context).clearCredentials()
                        onLogout()
                    }
                }
            ) {
                Icon(Icons.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Đăng xuất tài khoản UTH", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
