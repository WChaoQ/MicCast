package com.miccast.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miccast.model.ConnectionMethod
import com.miccast.model.ConnectionState
import com.miccast.ui.components.ConnectionDiagram
import com.miccast.ui.components.ConnectionMethodSelector
import com.miccast.viewmodel.MicCastViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MicCastViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Error Dialog ──────────────────────────────────────────────
    if (!uiState.errorMessage.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "连接错误",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    )
                }
            },
            text = {
                Text(
                    text = uiState.errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(
                        "确认",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 0.dp
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MicCast",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 24.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Connection diagram ────────────────────────────────────────
            ConnectionDiagram(
                connectionState = uiState.connectionState,
                connectionMethod = uiState.connectionMethod,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // ── Connection method selector ────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionLabel(
                    icon = Icons.Outlined.Link,
                    text = "连接方式"
                )

                ConnectionMethodSelector(
                    selected = uiState.connectionMethod,
                    onMethodSelected = viewModel::onConnectionMethodChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.connectionState == ConnectionState.DISCONNECTED ||
                            uiState.connectionState == ConnectionState.ERROR
                )
            }

            // ── Target device section ─────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionLabel(
                    icon = Icons.Outlined.Cast,
                    text = "目标设备"
                )

                when (uiState.connectionMethod) {
                    ConnectionMethod.WIFI -> WifiConfigSection(
                        ipAddress = uiState.wifiConfig.ipAddress,
                        port = uiState.wifiConfig.port.toString(),
                        onIpChange = viewModel::onIpAddressChanged,
                        onPortChange = viewModel::onPortChanged,
                        enabled = uiState.connectionState == ConnectionState.DISCONNECTED ||
                                uiState.connectionState == ConnectionState.ERROR
                    )

                    ConnectionMethod.USB -> UsbConfigSection(
                        port = uiState.usbConfig.port.toString(),
                        onPortChange = viewModel::onUsbPortChanged,
                        enabled = uiState.connectionState != ConnectionState.CONNECTED
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Bottom action buttons ─────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                when (uiState.connectionState) {
                    ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                        Button(
                            onClick = viewModel::onConnectClicked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            enabled = (uiState.connectionMethod == ConnectionMethod.WIFI && uiState.wifiConfig.ipAddress.isNotBlank())
                                    || uiState.connectionMethod == ConnectionMethod.USB
                        ) {
                            Icon(Icons.Outlined.Link, contentDescription = null)
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("连接", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    ConnectionState.CONNECTING -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("正在连接…")
                        }
                    }

                    ConnectionState.CONNECTED -> {
                        Button(
                            onClick = viewModel::onDisconnectClicked,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Outlined.LinkOff, contentDescription = null)
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("断开")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Design By AI",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  WiFi configuration fields
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WifiConfigSection(
    ipAddress: String,
    port: String,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = ipAddress,
            onValueChange = onIpChange,
            label = { Text("IP地址") },
            placeholder = { Text("192.168.X.X") },
            supportingText = { Text("手机和电脑需在同一WIFI，在电脑端查看本机 IP") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("端口") },
            supportingText = { Text("默认 5513（可修改）") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  USB configuration section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UsbConfigSection(
    port: String,
    onPortChange: (String) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("端口") },
            supportingText = { Text("默认 5514，需与 adb forward 端口一致") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        // 操作步骤说明卡片
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "USB TCP 传输说明",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "① 使用 USB 数据线连接手机与电脑\n" +
                            "② 开启手机「USB 调试」\n" +
                            "③ 电脑运行接收程序并监听端口 $port\n" +
                            "④ 手机点击连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
