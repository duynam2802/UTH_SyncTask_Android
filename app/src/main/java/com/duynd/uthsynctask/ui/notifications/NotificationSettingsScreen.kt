package com.duynd.uthsynctask.ui.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    Scaffold(topBar = { TopAppBar(title = { Text("Thông báo") }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasPermission) {
                PermissionBanner(onRequest = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }

            SettingsSwitchCard(
                title = "Nhắc nhở deadline",
                description = "Nhắc trước 12 giờ, lặp lại mỗi giờ. Trong 1 giờ cuối nếu chưa " +
                    "hoàn thành sẽ nhắc gấp hơn (mỗi 15 phút). Chỉ áp dụng cho deadline chưa hoàn thành.",
                checked = settings.enabled,
                onCheckedChange = { viewModel.setEnabled(it) }
            )

            SettingsSwitchCard(
                title = "Âm thanh",
                description = "Phát âm thanh khi có thông báo nhắc nhở.",
                checked = settings.soundEnabled,
                onCheckedChange = { viewModel.setSoundEnabled(it) },
                enabled = settings.enabled
            )

            SettingsSwitchCard(
                title = "Rung",
                description = "Rung khi có thông báo. Rung mạnh hơn khi ở mức khẩn cấp.",
                checked = settings.vibrationEnabled,
                onCheckedChange = { viewModel.setVibrationEnabled(it) },
                enabled = settings.enabled
            )

            SettingsSwitchCard(
                title = "Thông báo toàn màn hình (Báo thức)",
                description = "Khi có deadline khẩn cấp, hiển thị màn hình nhắc nhở toàn màn hình ngay cả khi điện thoại đang khoá.",
                checked = settings.fullScreenEnabled,
                onCheckedChange = { viewModel.setFullScreenEnabled(it) },
                enabled = settings.enabled
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Xem thử thông báo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Gửi ngay 1 thông báo mẫu để xem giao diện, không ảnh hưởng tới deadline thật.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { viewModel.sendTestNotification() },
                        enabled = hasPermission
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Gửi thông báo thử")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Chưa được cấp quyền thông báo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "Cần quyền này để app có thể nhắc deadline. Bấm để cấp quyền.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onRequest) { Text("Cấp quyền") }
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}
