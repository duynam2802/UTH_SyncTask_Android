package com.duynd.uthsynctask.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duynd.uthsynctask.data.local.SecureCredentialStore
import com.duynd.uthsynctask.data.model.GoogleCalendarOption
import com.duynd.uthsynctask.ui.login.PortalLoginScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    credentialStore: SecureCredentialStore,
    viewModel: SettingsViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val selectedCalendar by viewModel.selectedCalendar.collectAsState()
    val pendingConsent by viewModel.pendingConsent.collectAsState()
    val cleanupState by viewModel.cleanupState.collectAsState()

    var showCalendarPicker by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showPortalLogin by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConsentResult(result.data)
    }

    LaunchedEffect(pendingConsent) {
        val intentSender = pendingConsent
        if (intentSender != null) {
            consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            viewModel.clearPendingConsent()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Cài đặt") }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GoogleCalendarSection(
                connectionState = connectionState,
                selectedCalendarName = selectedCalendar?.summary,
                onConnect = { viewModel.connectGoogle() },
                onChangeCalendar = { showCalendarPicker = true },
                onDisconnect = { showDisconnectConfirm = true }
            )

            CleanupSection(
                cleanupState = cleanupState,
                enabled = connectionState is GoogleConnectionState.Connected && selectedCalendar != null,
                onCleanup = { viewModel.cleanupDuplicates() },
                onDismissResult = { viewModel.dismissCleanupState() }
            )

            UthAccountSection(
                onLogoutRequest = { showLogoutConfirm = true },
                onPortalLoginRequest = { showPortalLogin = true }
            )
        }
    }

    if (showPortalLogin) {
        AlertDialog(
            onDismissRequest = { showPortalLogin = false },
            modifier = Modifier.fillMaxSize(),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PortalLoginScreen(
                credentialStore = credentialStore,
                onLoginSuccess = { showPortalLogin = false }
            )
        }
    }

    if (showCalendarPicker && connectionState is GoogleConnectionState.Connected) {
        CalendarPickerDialog(
            calendars = (connectionState as GoogleConnectionState.Connected).calendars,
            currentSelectedId = selectedCalendar?.id,
            onSelect = {
                viewModel.selectCalendar(it)
                showCalendarPicker = false
            },
            onDismiss = { showCalendarPicker = false }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Đăng xuất tài khoản UTH?") },
            text = { Text("Bạn sẽ cần đăng nhập lại để tiếp tục đồng bộ deadline.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    viewModel.logoutUth(onLoggedOut)
                }) { Text("Đăng xuất") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Huỷ") } }
        )
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Ngắt kết nối Google Calendar?") },
            text = { Text("App sẽ không thể đồng bộ deadline lên Google Calendar cho tới khi bạn kết nối lại.") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    viewModel.disconnectGoogle()
                }) { Text("Ngắt kết nối") }
            },
            dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text("Huỷ") } }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun GoogleCalendarSection(
    connectionState: GoogleConnectionState,
    selectedCalendarName: String?,
    onConnect: () -> Unit,
    onChangeCalendar: () -> Unit,
    onDisconnect: () -> Unit
) {
    SettingsCard(title = "Google Calendar") {
        when (connectionState) {
            is GoogleConnectionState.NotConnected -> {
                Text(
                    "Chưa kết nối. Kết nối để chọn lịch lưu deadline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onConnect) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Kết nối Google Calendar", modifier = Modifier.padding(start = 8.dp))
                }
            }
            is GoogleConnectionState.Connecting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Đang kết nối...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            is GoogleConnectionState.Connected -> {
                Text(
                    text = "Đang lưu vào: ${selectedCalendarName ?: "Chưa chọn lịch"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onChangeCalendar) { Text("Đổi lịch lưu") }
                    OutlinedButton(onClick = onDisconnect) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Ngắt kết nối", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            is GoogleConnectionState.Error -> {
                Text(
                    connectionState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = onConnect) { Text("Thử lại") }
            }
        }
    }
}

@Composable
private fun CleanupSection(
    cleanupState: CleanupState,
    enabled: Boolean,
    onCleanup: () -> Unit,
    onDismissResult: () -> Unit
) {
    SettingsCard(title = "Dọn dẹp sự kiện trùng lặp") {
        Text(
            "Xoá các deadline bị lưu nhầm ở lịch khác (VD: sau khi bạn đổi lịch lưu).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (cleanupState) {
            CleanupState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Đang dọn dẹp...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            is CleanupState.Done -> {
                Text(
                    "Đã xoá ${cleanupState.removedCount} bản trùng lặp.",
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onDismissResult) { Text("Đóng") }
            }
            is CleanupState.Failed -> {
                Text(cleanupState.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismissResult) { Text("Đóng") }
            }
            CleanupState.Idle -> {
                OutlinedButton(onClick = onCleanup, enabled = enabled) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Dọn dẹp ngay", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun UthAccountSection(
    onLogoutRequest: () -> Unit,
    onPortalLoginRequest: () -> Unit
) {
    SettingsCard(title = "Tài khoản UTH") {
        Text(
            "Tài khoản dùng chung cho Portal, Courses và thnn.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPortalLoginRequest) {
                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Đăng nhập Portal", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onLogoutRequest) {
                Icon(Icons.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Đăng xuất", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun CalendarPickerDialog(
    calendars: List<GoogleCalendarOption>,
    currentSelectedId: String?,
    onSelect: (GoogleCalendarOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn lịch để lưu deadline") },
        text = {
            Column {
                calendars.forEach { calendar ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = calendar.id == currentSelectedId,
                            onClick = { onSelect(calendar) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(calendar.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}
