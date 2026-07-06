package com.duynd.uthsynctask.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duynd.uthsynctask.data.model.LoginErrorKind
import com.duynd.uthsynctask.data.model.LoginUiState
import com.duynd.uthsynctask.ui.components.ResultDialogType
import com.duynd.uthsynctask.ui.components.UthLogo
import com.duynd.uthsynctask.ui.components.UthPasswordField
import com.duynd.uthsynctask.ui.components.UthPrimaryButton
import com.duynd.uthsynctask.ui.components.UthResultDialog
import com.duynd.uthsynctask.ui.components.UthTextField
import com.duynd.uthsynctask.ui.theme.UthBackgroundLight
import com.duynd.uthsynctask.ui.theme.UthBluePrimary

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(UthBluePrimary, UthBackgroundLight),
                    endY = 340f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -40 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UthLogo()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "UTH SyncTask",
                        style = MaterialTheme.typography.headlineLarge,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Đồng bộ lịch học UTH về Google Calendar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(600, delayMillis = 150)) +
                    slideInVertically(tween(600, delayMillis = 150)) { 60 }
            ) {
                LoginCard(viewModel = viewModel, uiState = uiState)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tài khoản đăng nhập dùng chung cho Portal, Courses và trang thnn của UTH.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    val errorState = uiState as? LoginUiState.Error
    if (errorState != null) {
        val (type, title) = when (errorState.kind) {
            LoginErrorKind.WRONG_CREDENTIALS -> ResultDialogType.ERROR to "Sai tài khoản hoặc mật khẩu"
            LoginErrorKind.NETWORK -> ResultDialogType.WARNING to "Lỗi kết nối"
            LoginErrorKind.UNKNOWN -> ResultDialogType.ERROR to "Không thể đăng nhập"
        }
        UthResultDialog(
            type = type,
            title = title,
            message = errorState.message,
            onDismiss = { viewModel.onDialogDismissed() }
        )
    }
}

@Composable
private fun LoginCard(
    viewModel: LoginViewModel,
    uiState: LoginUiState
) {
    val isLoading = uiState is LoginUiState.Loading || uiState is LoginUiState.CheckingSavedAccount

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Đăng nhập",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            UthTextField(
                value = viewModel.mssvInput,
                onValueChange = viewModel::onMssvChange,
                label = "Mã số sinh viên",
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                enabled = !isLoading
            )

            UthPasswordField(
                value = viewModel.passwordInput,
                onValueChange = viewModel::onPasswordChange,
                label = "Mật khẩu",
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                enabled = !isLoading,
                onImeAction = { if (!isLoading) viewModel.login() }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = viewModel.rememberMe,
                    onCheckedChange = viewModel::onRememberMeChange,
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Ghi nhớ đăng nhập",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (uiState is LoginUiState.CheckingSavedAccount) {
                Text(
                    text = "Đang tự động đăng nhập bằng tài khoản đã lưu...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            UthPrimaryButton(
                text = "Đăng nhập",
                onClick = { viewModel.login() },
                isLoading = isLoading
            )
        }
    }
}
