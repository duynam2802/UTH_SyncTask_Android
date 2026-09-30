package com.duynd.uthsynctask.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duynd.uthsynctask.data.local.SecureCredentialStore
import com.duynd.uthsynctask.ui.navigation.MainTab
import com.duynd.uthsynctask.ui.notifications.NotificationSettingsScreen
import com.duynd.uthsynctask.ui.schedule.ScheduleScreen
import com.duynd.uthsynctask.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MainShellScreen(
    onLogout: () -> Unit,
    openPortalLoginOnStart: Boolean = false,
    onPortalLoginConsumed: () -> Unit = {}
) {
    val tabs = remember { MainTab.items }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    
    val context = LocalContext.current
    val credentialStore = remember { SecureCredentialStore(context) }

    LaunchedEffect(openPortalLoginOnStart) {
        if (openPortalLoginOnStart) {
            val settingsTabIdx = tabs.indexOf(MainTab.Settings)
            if (settingsTabIdx >= 0) {
                pagerState.scrollToPage(settingsTabIdx)
            }
        }
    }

    Scaffold(
        bottomBar = {
            UthModernBottomBar(
                tabs = tabs,
                currentPage = pagerState.currentPage,
                currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                onTabSelected = { tab ->
                    val targetIdx = tabs.indexOf(tab)
                    if (targetIdx != pagerState.currentPage) {
                        scope.launch {
                            // Chuyển trang sử dụng Spring animation chuẩn vật lý, chống khựng/giật
                            pagerState.animateScrollToPage(
                                page = targetIdx,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioNoBouncy
                                )
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
            beyondViewportPageCount = 1,
            userScrollEnabled = true
        ) { page ->
            // Tính toán hiệu ứng chuyển cảnh mượt mà (Scale + Alpha) trực tiếp trên RenderThread qua graphicsLayer
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absOffset = abs(pageOffset).coerceIn(0f, 1f)
            val scale = 0.96f + (1f - absOffset) * 0.04f
            val alpha = 0.6f + (1f - absOffset) * 0.4f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            ) {
                when (tabs[page]) {
                    MainTab.Schedule -> ScheduleScreen(
                        onNavigateToSettings = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = tabs.indexOf(MainTab.Settings),
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy
                                    )
                                )
                            }
                        }
                    )
                    MainTab.Notifications -> NotificationSettingsScreen()
                    MainTab.Settings -> SettingsScreen(
                        onLoggedOut = onLogout,
                        credentialStore = credentialStore,
                        openPortalLoginOnStart = openPortalLoginOnStart,
                        onPortalLoginConsumed = onPortalLoginConsumed
                    )
                }
            }
        }
    }
}

@Composable
private fun UthModernBottomBar(
    tabs: List<MainTab>,
    currentPage: Int,
    currentPageOffsetFraction: Float,
    onTabSelected: (MainTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .height(68.dp),
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        val tabCount = tabs.size
        val currentContinuousPosition = (currentPage + currentPageOffsetFraction).coerceIn(0f, (tabCount - 1).toFloat())

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val icon = when (tab) {
                    MainTab.Schedule -> Icons.Filled.DateRange
                    MainTab.Notifications -> Icons.Filled.Notifications
                    MainTab.Settings -> Icons.Filled.Settings
                }

                val selectionFraction = (1f - abs(currentContinuousPosition - index)).coerceIn(0f, 1f)

                UthModernTabItem(
                    tab = tab,
                    icon = icon,
                    selectionFraction = selectionFraction,
                    modifier = Modifier.weight(1f),
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
    selectionFraction: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    val contentColor by animateColorAsState(
        targetValue = if (selectionFraction > 0.4f) activeColor else inactiveColor,
        animationSpec = tween(150),
        label = "tabContentColor"
    )

    // Scaling và Alpha siêu mượt chạy trực tiếp trên GPU RenderThread
    val iconScale = 0.95f + (selectionFraction * 0.15f)
    val contentAlpha = 0.7f + (selectionFraction * 0.3f)

    // Thông số cho indicator capsule nền biến thiên mượt theo cử chỉ
    val pillAlpha = selectionFraction * 0.65f
    val pillScaleX = 0.75f + (selectionFraction * 0.25f)
    val pillColor = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.height(34.dp)
        ) {
            // Nền capsule (Indicator active) biến đổi mượt mà
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.75f)
                    .graphicsLayer {
                        this.alpha = pillAlpha
                        this.scaleX = pillScaleX
                        this.scaleY = selectionFraction
                    }
                    .clip(RoundedCornerShape(17.dp))
                    .background(pillColor)
            )

            Icon(
                imageVector = icon,
                contentDescription = tab.label,
                tint = contentColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = contentAlpha
                    }
            )
        }

        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, // Giữ cố định FontWeight để tránh re-measure text gây giật
            color = contentColor,
            fontSize = 10.sp,
            modifier = Modifier
                .padding(top = 2.dp)
                .graphicsLayer {
                    alpha = contentAlpha
                }
        )
    }
}
