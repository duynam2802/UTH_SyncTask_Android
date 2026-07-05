package com.duynd.uthsynctask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UTHMainScreen(
    uiState: AppUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRememberChanged: (Boolean) -> Unit,
    onLoginClicked: () -> Unit,
    onGoogleLoginClicked: () -> Unit,
    onManualSyncClicked: () -> Unit,
    onLogoutGoogleClicked: () -> Unit,
    onAutoSyncChanged: (Boolean) -> Unit,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onNotificationModeChanged: (NotificationMode) -> Unit,
    onCalendarSelected: (String, String) -> Unit,
    onEventAction: (Assignment, String) -> Unit,
    onDismissDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var calendarExpanded by remember { mutableStateOf(false) }
    val tabs = listOf("Tổng quan", "Sự kiện", "Cài đặt")

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(listOf(Color(0xFF0F6DB3), Color(0xFF1E88E5)))
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("UTH SyncTask", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Đồng bộ lịch học UTH vào Google Calendar", color = Color(0xFFE3F2FD), fontSize = 14.sp)
                        Text("Trạng thái: ${uiState.status}", color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            NavigationBar(modifier = Modifier.padding(horizontal = 8.dp)) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        label = { Text(title) },
                        icon = {}
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(onClick = onGoogleLoginClicked, modifier = Modifier.weight(1f)) {
                                Text("Đăng nhập Google")
                            }
                            FilledTonalButton(onClick = onManualSyncClicked, modifier = Modifier.weight(1f)) {
                                Text("Đồng bộ thủ công")
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Tài khoản UTH", fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(value = uiState.username, onValueChange = onUsernameChanged, label = { Text("MSSV / tài khoản") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = uiState.password, onValueChange = onPasswordChanged, label = { Text("Mật khẩu") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Ghi nhớ tài khoản và mật khẩu")
                                    Switch(checked = uiState.rememberCredentials, onCheckedChange = onRememberChanged)
                                }
                                FilledTonalButton(onClick = onLoginClicked, modifier = Modifier.fillMaxWidth()) {
                                    Text("Kiểm tra đăng nhập")
                                }
                            }
                        }
                    }
                    1 -> {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Sự kiện gần đây", fontWeight = FontWeight.SemiBold)
                                if (uiState.isLoading) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator()
                                        Text("Đang đồng bộ...")
                                    }
                                } else if (uiState.events.isEmpty()) {
                                    Text("Chưa có dữ liệu. Hãy bấm Đồng bộ thủ công để lấy lịch học.")
                                } else {
                                    LazyColumn(modifier = Modifier.height(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(uiState.events) { event ->
                                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(event.title, fontWeight = FontWeight.SemiBold)
                                                    Text("Ngày: ${event.date}")
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        OutlinedButton(onClick = { onEventAction(event, "complete") }) { Text("Hoàn thành") }
                                                        OutlinedButton(onClick = { onEventAction(event, "delete") }) { Text("Xóa") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Cài đặt", fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Tự động chạy mỗi 1 giờ")
                                    Switch(checked = uiState.autoSyncEnabled, onCheckedChange = onAutoSyncChanged)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Thông báo nhắc việc")
                                    Switch(checked = uiState.reminderEnabled, onCheckedChange = onReminderEnabledChanged)
                                }
                                Text("Mức độ thông báo")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NotificationMode.values().forEach { mode ->
                                        OutlinedButton(onClick = { onNotificationModeChanged(mode) }) { Text(mode.label) }
                                    }
                                }
                                Text("Google Calendar")
                                Text("Tài khoản: ${uiState.googleAccountName}")
                                ExposedDropdownMenuBox(expanded = calendarExpanded, onExpandedChange = { calendarExpanded = !calendarExpanded }) {
                                    OutlinedTextField(
                                        value = uiState.selectedCalendarName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Chọn lịch lưu") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = calendarExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(expanded = calendarExpanded, onDismissRequest = { calendarExpanded = false }) {
                                        uiState.availableCalendars.forEach { calendar ->
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text(calendar.summary) },
                                                onClick = {
                                                    onCalendarSelected(calendar.id, calendar.summary)
                                                    calendarExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                OutlinedButton(onClick = onLogoutGoogleClicked, modifier = Modifier.fillMaxWidth()) {
                                    Text("Đăng xuất Google")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.dialogMessage != null) {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text("Thông báo") },
                text = { Text(uiState.dialogMessage) },
                confirmButton = {
                    TextButton(onClick = onDismissDialog) { Text("Đã hiểu") }
                }
            )
        }
    }
}
