package com.duynd.uthsynctask.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Bảng màu thương hiệu UTH - tông xanh dương hiện đại.
 * Đây là màu cố định của app (không dùng Material You dynamic color)
 * để đảm bảo nhận diện thương hiệu nhất quán trên mọi thiết bị.
 */

// ---- Xanh dương chủ đạo (Primary) ----
val UthBlueDeepest = Color(0xFF0A1F3D)      // dùng cho text trên nền sáng nếu cần rất đậm (không dùng chính)
val UthBluePrimary = Color(0xFF0B4F9E)      // xanh dương đậm - màu chính
val UthBluePrimaryDark = Color(0xFF083B77)  // xanh dương đậm hơn - dùng cho pressed/gradient
val UthBluePrimaryLight = Color(0xFF3E7BC4) // xanh dương sáng hơn - dùng cho gradient/hover
val UthBlueContainer = Color(0xFFDCEAFB)    // nền container nhạt cho light theme
val UthBlueContainerDark = Color(0xFF163A5E)// nền container cho dark theme

// ---- Xanh cyan phụ trợ (Secondary / Accent) ----
val UthCyanAccent = Color(0xFF00B8D9)
val UthCyanAccentDark = Color(0xFF00A0BD)

// ---- Trạng thái ----
val UthSuccess = Color(0xFF1FAA59)
val UthSuccessContainer = Color(0xFFDDF6E5)
val UthWarning = Color(0xFFF5A524)
val UthWarningContainer = Color(0xFFFFF2DA)
val UthError = Color(0xFFE5484D)
val UthErrorContainer = Color(0xFFFDE3E4)

// ---- Nền & bề mặt - Light theme ----
val UthBackgroundLight = Color(0xFFF4F8FD)
val UthSurfaceLight = Color(0xFFFFFFFF)
val UthSurfaceVariantLight = Color(0xFFE8F0FA)
val UthOnBackgroundLight = Color(0xFF13202E)
val UthOnSurfaceLight = Color(0xFF13202E)
val UthOutlineLight = Color(0xFFC3D3E5)

// ---- Nền & bề mặt - Dark theme ----
val UthBackgroundDark = Color(0xFF0A1420)
val UthSurfaceDark = Color(0xFF101E2E)
val UthSurfaceVariantDark = Color(0xFF17293C)
val UthOnBackgroundDark = Color(0xFFE6EEF7)
val UthOnSurfaceDark = Color(0xFFE6EEF7)
val UthOutlineDark = Color(0xFF2E4562)

// ---- Gradient dùng cho logo / header ----
val UthGradientStart = Color(0xFF0B4F9E)
val UthGradientEnd = Color(0xFF12A0C9)
