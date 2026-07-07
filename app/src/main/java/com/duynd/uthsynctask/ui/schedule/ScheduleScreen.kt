package com.duynd.uthsynctask.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duynd.uthsynctask.data.model.EventSource
import com.duynd.uthsynctask.data.model.SyncOutcome
import com.duynd.uthsynctask.data.model.SyncedEvent
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val displayFormat = SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale("vi")).apply {
    timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    var eventPendingDelete by remember { mutableStateOf<SyncedEvent?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Lịch học & Deadline") }
                )
                if (syncState is ScheduleSyncState.Syncing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.syncNow() },
                icon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                text = { Text("Đồng bộ ngay") }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            LastSyncBanner(uiState.lastSyncAtMillis)

            if (uiState.events.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.events, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            onToggleCompleted = { viewModel.toggleCompleted(event) },
                            onDeleteRequest = { eventPendingDelete = event }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    when (val state = syncState) {
        is ScheduleSyncState.Finished -> {
            SyncResultDialog(
                outcome = state.outcome,
                onDismiss = { viewModel.dismissSyncResult() },
                onGoToSettings = {
                    viewModel.dismissSyncResult()
                    onNavigateToSettings()
                }
            )
        }
        else -> Unit
    }

    eventPendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { eventPendingDelete = null },
            title = { Text("Xoá deadline này?") },
            text = { Text("\"${event.title}\" sẽ bị xoá khỏi danh sách và khỏi Google Calendar (nếu đã đồng bộ).") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEvent(event)
                    eventPendingDelete = null
                }) { Text("Xoá") }
            },
            dismissButton = {
                TextButton(onClick = { eventPendingDelete = null }) { Text("Huỷ") }
            }
        )
    }
}

@Composable
private fun LastSyncBanner(lastSyncAtMillis: Long?) {
    val text = if (lastSyncAtMillis != null) {
        "Đồng bộ lần cuối: ${displayFormat.format(java.util.Date(lastSyncAtMillis))}"
    } else {
        "Chưa đồng bộ lần nào - bấm \"Đồng bộ ngay\" để bắt đầu"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Text("Chưa có deadline nào", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bấm \"Đồng bộ ngay\" để lấy deadline từ Courses và thnn",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EventCard(
    event: SyncedEvent,
    onToggleCompleted: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleCompleted) {
                Icon(
                    imageVector = if (event.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (event.isCompleted) "Đã hoàn thành" else "Đánh dấu hoàn thành",
                    tint = if (event.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(event.source)
                    if (!event.isPreciseTime) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "giờ ước tính",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (event.isCompleted) TextDecoration.LineThrough else null,
                    color = if (event.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = "${displayFormat.format(java.util.Date(event.startTimeMillis))} → ${displayFormat.format(java.util.Date(event.endTimeMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteRequest) {
                Icon(Icons.Filled.Delete, contentDescription = "Xoá", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SourceBadge(source: EventSource) {
    val color = when (source) {
        EventSource.COURSES -> MaterialTheme.colorScheme.primary
        EventSource.THNN -> MaterialTheme.colorScheme.secondary
        EventSource.PORTAL -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = source.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SyncResultDialog(
    outcome: SyncOutcome,
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    when (outcome) {
        is SyncOutcome.Success -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Đồng bộ xong") },
                text = {
                    Column {
                        Text("Tổng cộng ${outcome.totalEventsCount} deadline.")
                        Text("Mới: ${outcome.newEventsCount} · Cập nhật: ${outcome.updatedEventsCount}")
                        if (outcome.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                outcome.warnings.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
            )
        }
        SyncOutcome.NeedsGoogleAuthorization -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Chưa kết nối Google Calendar") },
                text = { Text("Vào Cài đặt để kết nối Google Calendar trước khi đồng bộ.") },
                confirmButton = { TextButton(onClick = onGoToSettings) { Text("Đến Cài đặt") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Để sau") } }
            )
        }
        SyncOutcome.NeedsCalendarSelection -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Chưa chọn lịch lưu") },
                text = { Text("Vào Cài đặt để chọn lịch Google Calendar sẽ lưu deadline vào.") },
                confirmButton = { TextButton(onClick = onGoToSettings) { Text("Đến Cài đặt") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Để sau") } }
            )
        }
        is SyncOutcome.UthLoginFailed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Đăng nhập UTH thất bại") },
                text = { Text(outcome.message) },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Đã hiểu") } }
            )
        }
        is SyncOutcome.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Đồng bộ thất bại") },
                text = { Text(outcome.message) },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Đã hiểu") } }
            )
        }
    }
}
