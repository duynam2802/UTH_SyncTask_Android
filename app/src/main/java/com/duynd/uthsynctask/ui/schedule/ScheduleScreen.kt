package com.duynd.uthsynctask.ui.schedule

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duynd.uthsynctask.data.model.EventSource
import com.duynd.uthsynctask.data.model.SyncOutcome
import com.duynd.uthsynctask.data.model.SyncedEvent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val displayFormat = SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale("vi")).apply {
    timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    var eventPendingDelete by remember { mutableStateOf<SyncedEvent?>(null) }
    var eventToShowDetail by remember { mutableStateOf<SyncedEvent?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Tất cả", "Lịch học", "Deadline")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Lịch học & Deadline", fontWeight = FontWeight.ExtraBold) },
                    actions = {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("content://com.android.calendar/time/")
                            }
                            context.startActivity(intent)
                        }) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = "Mở ứng dụng Lịch",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
                
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {}
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                if (syncState is ScheduleSyncState.Syncing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.syncNow() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                text = { Text("Đồng bộ ngay") }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            LastSyncBanner(uiState.lastSyncAtMillis)

            val filteredGroups = remember(uiState.groups, selectedTabIndex) {
                uiState.groups.map { group ->
                    group.copy(
                        events = group.events.filter { event ->
                            when (selectedTabIndex) {
                                1 -> event.source == EventSource.PORTAL
                                2 -> event.source == EventSource.COURSES || event.source == EventSource.THNN
                                else -> true
                            }
                        }
                    )
                }.filter { it.events.isNotEmpty() }
            }

            AnimatedContent(
                targetState = filteredGroups.isEmpty(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "ScheduleContent"
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        filteredGroups.forEach { group ->
                            stickyHeader {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                ) {
                                    Text(
                                        text = group.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                            
                            items(group.events, key = { it.id }) { event ->
                                Box(modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    EventCard(
                                        event = event,
                                        onToggleCompleted = { viewModel.toggleCompleted(event) },
                                        onDeleteRequest = { eventPendingDelete = event },
                                        onClick = { eventToShowDetail = event }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs and BottomSheets
    if (syncState is ScheduleSyncState.Finished) {
        val state = syncState as ScheduleSyncState.Finished
        SyncResultDialog(
            outcome = state.outcome,
            onDismiss = { viewModel.dismissSyncResult() },
            onGoToSettings = {
                viewModel.dismissSyncResult()
                onNavigateToSettings()
            }
        )
    }

    eventToShowDetail?.let { event ->
        ModalBottomSheet(
            onDismissRequest = { eventToShowDetail = null },
            sheetState = sheetState
        ) {
            EventDetailContent(
                event = event,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) eventToShowDetail = null
                    }
                }
            )
        }
    }

    eventPendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { eventPendingDelete = null },
            title = { Text("Xoá sự kiện này?") },
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
            Text("Không có dữ liệu", style = MaterialTheme.typography.titleMedium)
            Text(
                "Thử đổi bộ lọc hoặc nhấn \"Đồng bộ ngay\"",
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
    onDeleteRequest: () -> Unit,
    onClick: () -> Unit
) {
    val isUpcoming = !event.isCompleted && 
            event.startTimeMillis > System.currentTimeMillis() && 
            event.startTimeMillis <= System.currentTimeMillis() + 24 * 60 * 60 * 1000

    val isPortal = event.source == EventSource.PORTAL
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUpcoming) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUpcoming) 2.dp else 0.5.dp),
        border = if (isUpcoming) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        } else null
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isPortal) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPortal) Icons.Filled.Class else Icons.Filled.Assignment,
                        contentDescription = null,
                        tint = if (isPortal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isPortal) "Lịch học" else "Deadline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPortal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    if (isUpcoming) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                "SẮP DIỄN RA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
                
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isUpcoming) FontWeight.Bold else FontWeight.Medium,
                    textDecoration = if (event.isCompleted && !isPortal) TextDecoration.LineThrough else null,
                    color = if (event.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPortal) {
                            "${displayFormat.format(java.util.Date(event.startTimeMillis))} - ${displayFormat.format(java.util.Date(event.endTimeMillis)).substringBefore(",")}"
                        } else {
                            "Hết hạn: ${displayFormat.format(java.util.Date(event.endTimeMillis))}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onToggleCompleted) {
                Icon(
                    imageVector = if (event.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (isPortal) "Đánh dấu đã xem" else "Hoàn thành",
                    tint = if (event.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            
            if (isPortal) {
                IconButton(onClick = onDeleteRequest) {
                    Icon(Icons.Filled.Delete, contentDescription = "Xoá", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun EventDetailContent(
    event: SyncedEvent,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceBadge(event.source)
            if (event.isCompleted) {
                val statusText = if (event.source == EventSource.PORTAL) "Đã xem" else "Đã hoàn thành"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Text(
            text = event.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (!event.courseName.isNullOrBlank()) {
            DetailItem(
                icon = Icons.Filled.Class,
                label = "Môn học",
                value = event.courseName
            )
        }

        DetailItem(
            icon = Icons.Filled.CalendarMonth,
            label = "Thời gian bắt đầu",
            value = displayFormat.format(java.util.Date(event.startTimeMillis))
        )

        DetailItem(
            icon = Icons.Filled.Event,
            label = "Thời gian kết thúc",
            value = displayFormat.format(java.util.Date(event.endTimeMillis))
        )

        if (!event.isPreciseTime) {
            Text(
                text = "⚠️ Đây là giờ ước tính, bạn nên kiểm tra lại trên trang web của trường.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { uriHandler.openUri(event.sourceUrl) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Mở trang nguồn")
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Đóng")
            }
        }
    }
}

@Composable
private fun DetailItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
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
