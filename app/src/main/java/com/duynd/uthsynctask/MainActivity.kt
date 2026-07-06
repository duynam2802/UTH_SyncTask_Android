package com.duynd.uthsynctask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.duynd.uthsynctask.ui.navigation.AppNavHost
import com.duynd.uthsynctask.ui.theme.UTHSyncTaskTheme

/**
 * Lưu ý (Part 1): MainActivity giờ chỉ đảm nhiệm dựng theme + điều hướng.
 * Toàn bộ logic đăng nhập cũ (UTHScraper, CalendarHelper, WorkManager...) đã được
 * chuyển ra khỏi đây và sẽ được kết nối lại đúng chỗ ở module Đồng bộ (Part 2),
 * dùng kiến trúc mới (Repository + ViewModel) thay vì gọi trực tiếp trong Activity.
 *
 * Các file cũ (UTHScraper.kt, CalendarHelper.kt, GoogleCalendarService.kt,
 * parseEventsFromHtml.kt, SyncWorker.kt, Assignment.kt) vẫn được giữ nguyên trong
 * project, chưa bị xoá - sẽ được nâng cấp và gọi lại ở Part 2.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UTHSyncTaskTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}
