package com.duynd.uthsynctask.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duynd.uthsynctask.ui.navigation.MainTab
import com.duynd.uthsynctask.ui.notifications.NotificationSettingsScreen
import com.duynd.uthsynctask.ui.schedule.ScheduleScreen
import com.duynd.uthsynctask.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun MainShellScreen(
    onLogout: () -> Unit
) {
    // Sử dụng remember để tránh việc tính toán lại danh sách liên tục
    val tabs = remember { MainTab.items }
    
    // Sử dụng PagerState thay cho NavHost để hỗ trợ vuốt chuyển Tab như Telegram/iOS
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    
    // Xác định tab hiện tại dựa trên trang của Pager
    val currentTab = tabs[pagerState.currentPage]

    Scaffold(
        bottomBar = {
            UthModernBottomBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    val targetIdx = tabs.indexOf(tab)
                    if (targetIdx != pagerState.currentPage) {
                        scope.launch {
                            // Cuộn mượt tới trang mới với hiệu ứng One UI
                            pagerState.animateScrollToPage(
                                page = targetIdx,
                                animationSpec = spring(stiffness = Spring.StiffnessLow)
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Load sẵn toàn bộ các trang để vuốt mượt mà tuyệt đối
            beyondViewportPageCount = 2,
            userScrollEnabled = true
        ) { page ->
            // Bọc thêm một lớp Box có clipToBounds để chặn tuyệt đối nội dung tràn sang trang khác
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds() 
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            // Hiệu ứng Parallax: Chỉ trượt nội dung bên trong vùng đã được clip
                            translationX = pageOffset * size.width * 0.25f
                            alpha = 1f - (pageOffset.coerceIn(-1f, 1f).let { if (it < 0) -it else it } * 0.15f)
                        }
                ) {
                    when (tabs[page]) {
                        MainTab.Schedule -> ScheduleScreen(
                            onNavigateToSettings = {
                                scope.launch { pagerState.animateScrollToPage(tabs.indexOf(MainTab.Settings)) }
                            }
                        )
                        MainTab.Notifications -> NotificationSettingsScreen()
                        MainTab.Settings -> SettingsScreen(onLoggedOut = onLogout)
                    }
                }
            }
        }
    }
}

@Composable
private fun UthModernBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    // Thiết kế dạng Floating Bar phong cách One UI
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .height(72.dp),
        shape = RoundedCornerShape(36.dp), // Bo tròn cực đại
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 4.dp, // Giảm xuống để nhẹ hơn
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.items.forEach { tab ->
                val isSelected = currentTab.route == tab.route
                val icon = when (tab) {
                    MainTab.Schedule -> Icons.Filled.DateRange
                    MainTab.Notifications -> Icons.Filled.Notifications
                    MainTab.Settings -> Icons.Filled.Settings
                }
                
                UthModernTabItem(
                    tab = tab,
                    icon = icon,
                    isSelected = isSelected,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun UthModernTabItem(
    tab: MainTab,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    // Animate màu sắc icon: Xanh khi chọn, Xám khi không chọn
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = tween(300),
        label = "color"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, 
            stiffness = Spring.StiffnessMedium // Tăng độ nhạy
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer { 
                    scaleX = scale
                    scaleY = scale
                }
                .size(40.dp) // Thu nhỏ box lại vì không còn nền
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tab.label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        
        val labelAlpha by animateFloatAsState(
            targetValue = if (isSelected) 1f else 0.5f,
            animationSpec = tween(200),
            label = "alpha"
        )
        val labelOffset by animateDpAsState(
            targetValue = if (isSelected) 0.dp else 2.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "offset"
        )

        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = contentColor, // Dùng chung màu với icon cho đồng bộ
            fontSize = 10.sp,
            modifier = Modifier
                .graphicsLayer { 
                    alpha = labelAlpha
                    translationY = labelOffset.toPx()
                }
                .padding(top = 2.dp)
        )
    }
}
