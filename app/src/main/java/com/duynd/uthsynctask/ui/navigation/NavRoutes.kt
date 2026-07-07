package com.duynd.uthsynctask.ui.navigation

/**
 * Danh sách route điều hướng cấp cao nhất của app.
 */
object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
}

/**
 * Các tab trong màn hình chính (bottom navigation bar).
 * Mỗi tab tương ứng với một chức năng lớn của app - sẽ được xây dựng đầy đủ
 * ở các phần tiếp theo (Lịch/Đồng bộ ở Part 2, Thông báo ở Part 3, Cài đặt ở Part 4).
 */
sealed class MainTab(val route: String, val label: String) {
    data object Schedule : MainTab("tab_schedule", "Lịch")
    data object Notifications : MainTab("tab_notifications", "Thông báo")
    data object Settings : MainTab("tab_settings", "Cài đặt")

    companion object {
        val items: List<MainTab> get() = listOf(Schedule, Notifications, Settings)
    }
}
