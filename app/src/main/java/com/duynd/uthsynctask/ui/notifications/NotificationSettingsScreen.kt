package com.duynd.uthsynctask.ui.notifications

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import com.duynd.uthsynctask.data.model.NotificationSettings
import androidx.compose.ui.tooling.preview.Preview
import com.duynd.uthsynctask.ui.theme.UTHSyncTaskTheme

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

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setSoundUri(uri?.toString())
        }
    }

    val currentRingtoneName = remember(settings.soundUri) {
        try {
            val uri = settings.soundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            RingtoneManager.getRingtone(context, uri).getTitle(context)
        } catch (e: Exception) {
            "Mặc định"
        }
    }

    NotificationSettingsContent(
        settings = settings,
        hasPermission = hasPermission,
        onPermissionRequest = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onEnabledChange = { viewModel.setEnabled(it) },
        onSoundEnabledChange = { viewModel.setSoundEnabled(it) },
        onVibrationEnabledChange = { viewModel.setVibrationEnabled(it) },
        onFullScreenEnabledChange = { viewModel.setFullScreenEnabled(it) },
        onPickRingtone = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Chọn âm thanh thông báo")
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    settings.soundUri?.let { Uri.parse(it) }
                )
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            }
            ringtonePickerLauncher.launch(intent)
        },
        onSendTestNotification = { viewModel.sendTestNotification() },
        currentRingtoneName = currentRingtoneName
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSettingsContent(
    settings: NotificationSettings,
    hasPermission: Boolean,
    onPermissionRequest: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSoundEnabledChange: (Boolean) -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onFullScreenEnabledChange: (Boolean) -> Unit,
    onPickRingtone: () -> Unit,
    onSendTestNotification: () -> Unit,
    currentRingtoneName: String
) {
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
                PermissionBanner(onRequest = onPermissionRequest)
            }

            SettingsSwitchCard(
                title = "Nhắc nhở deadline",
                description = "Nhắc trước 12 giờ, lặp lại mỗi giờ. Trong 1 giờ cuối nếu chưa " +
                    "hoàn thành sẽ nhắc gấp hơn (mỗi 15 phút). Chỉ áp dụng cho deadline chưa hoàn thành.",
                checked = settings.enabled,
                onCheckedChange = onEnabledChange
            )

            SettingsSwitchCard(
                title = "Âm thanh",
                description = "Phát âm thanh khi có thông báo nhắc nhở.",
                checked = settings.soundEnabled,
                onCheckedChange = onSoundEnabledChange,
                enabled = settings.enabled
            )

            if (settings.soundEnabled && settings.enabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickRingtone() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tiếng thông báo", style = MaterialTheme.typography.titleMedium)
                        Text(
                            currentRingtoneName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            SettingsSwitchCard(
                title = "Rung",
                description = "Rung khi có thông báo. Rung mạnh hơn khi ở mức khẩn cấp.",
                checked = settings.vibrationEnabled,
                onCheckedChange = onVibrationEnabledChange,
                enabled = settings.enabled
            )

            SettingsSwitchCard(
                title = "Thông báo toàn màn hình (Báo thức)",
                description = "Khi có deadline khẩn cấp, hiển thị màn hình nhắc nhở toàn màn hình ngay cả khi điện thoại đang khoá.",
                checked = settings.fullScreenEnabled,
                onCheckedChange = onFullScreenEnabledChange,
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
                        onClick = onSendTestNotification,
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

@Preview(showBackground = true)
@Composable
fun NotificationSettingsPreview() {
    UTHSyncTaskTheme {
        NotificationSettingsContent(
            settings = NotificationSettings(
                enabled = true,
                soundEnabled = true,
                vibrationEnabled = true,
                fullScreenEnabled = false,
                soundUri = null
            ),
            hasPermission = true,
            onPermissionRequest = {},
            onEnabledChange = {},
            onSoundEnabledChange = {},
            onVibrationEnabledChange = {},
            onFullScreenEnabledChange = {},
            onPickRingtone = {},
            onSendTestNotification = {},
            currentRingtoneName = "Mặc định"
        )
    }
}

