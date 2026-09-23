package com.qqbot.keywordbot.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.qqbot.keywordbot.napcat.NapCatManager
import com.qqbot.keywordbot.napcat.OneBotClient

/**
 * 首页：NapCat 连接管理 + 扫码登录 + AI 设置 + 运行日志
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val napCatState by viewModel.napCatState.collectAsState()
    val wsState by viewModel.wsState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val qrCodeUrl by viewModel.qrCodeUrl.collectAsState()
    val openAiKey by viewModel.openAiApiKey.collectAsState()
    val qqDebReady by viewModel.qqDebReady.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }

    // QQ.deb 文件选择器
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importQqDeb(it) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    "QQ 关键词自动回复",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "手机端 QQ 机器人管理控制台",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            StatusOverviewCard(napCatState = napCatState, wsState = wsState)
        }

        // QQ.deb 上传卡片
        item {
            QqDebCard(
                ready = qqDebReady,
                onUpload = { filePicker.launch("*/*") },
                onDelete = { viewModel.deleteQqDeb() }
            )
        }

        // 扫码登录二维码
        qrCodeUrl?.let { url ->
            item {
                QrCodeCard(url = url, onDismiss = { viewModel.clearQrCode() })
            }
        }

        item {
            ActionButtonsRow(
                napCatState = napCatState,
                onStart = { viewModel.startNapCat() },
                onStop = { viewModel.stopNapCat() },
                onConnect = { viewModel.connectOneBot() }
            )
        }

        // AI 设置入口
        item {
            SettingsCard(
                apiKeyConfigured = openAiKey.isNotBlank(),
                onClick = { showSettingsDialog = true }
            )
        }

        item {
            Text(
                "运行日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        items(logs) { line ->
            LogLineItem(line)
        }
    }

    if (showSettingsDialog) {
        AiSettingsDialog(
            initialApiKey = openAiKey,
            onDismiss = { showSettingsDialog = false },
            onSave = { key, url ->
                viewModel.saveOpenAiSettings(key, url)
                showSettingsDialog = false
            }
        )
    }
}

@Composable
private fun QrCodeCard(url: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "扫码登录 QQ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            AsyncImage(
                model = url,
                contentDescription = "登录二维码",
                modifier = Modifier.size(200.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "请使用手机 QQ 扫描二维码登录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SettingsCard(apiKeyConfigured: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("AI 接口配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (apiKeyConfigured) "已配置 OpenAI API Key" else "未配置 API Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (apiKeyConfigured) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
            TextButton(onClick = onClick) { Text("设置") }
        }
    }
}

@Composable
private fun AiSettingsDialog(
    initialApiKey: String,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, baseUrl: String) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var baseUrl by remember { mutableStateOf("https://api.openai.com/v1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 接口设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "支持 OpenAI 兼容协议（如 DeepSeek、通义千问等）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(apiKey, baseUrl) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun QqDebCard(ready: Boolean, onUpload: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ready)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Upload,
                contentDescription = null,
                tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "QQ 安装包",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (ready) "已导入，将优先使用" else "未导入（首次启动需联网下载）",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            if (ready) {
                TextButton(onClick = onDelete) { Text("删除") }
            } else {
                Button(onClick = onUpload, shape = RoundedCornerShape(10.dp)) {
                    Text("上传 QQ.deb")
                }
            }
        }
    }
}

@Composable
private fun StatusOverviewCard(
    napCatState: NapCatManager.NapCatState,
    wsState: OneBotClient.ConnectionState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("服务状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            StatusRow(label = "NapCat 容器", state = napCatState.name, stateColor = napCatState.toColor())
            Spacer(modifier = Modifier.height(12.dp))
            StatusRow(label = "OneBot WS 连接", state = wsState.name, stateColor = wsState.toColor())
        }
    }
}

@Composable
private fun StatusRow(label: String, state: String, stateColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(stateColor))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Surface(color = stateColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
            Text(state, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge, color = stateColor, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionButtonsRow(
    napCatState: NapCatManager.NapCatState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onConnect: () -> Unit
) {
    val isRunning = napCatState == NapCatManager.NapCatState.RUNNING
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStart, enabled = !isRunning, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("启动 NapCat")
            }
            OutlinedButton(onClick = onStop, enabled = isRunning, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("停止")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onConnect, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("连接 OneBot WS")
            }
        }
    }
}

@Composable
private fun LogLineItem(line: String) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)) {
        Text(line, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
    }
}

private fun NapCatManager.NapCatState.toColor(): Color = when (this) {
    NapCatManager.NapCatState.RUNNING -> Color(0xFF10B981)
    NapCatManager.NapCatState.STARTING -> Color(0xFFF59E0B)
    NapCatManager.NapCatState.ERROR -> Color(0xFFEF4444)
    NapCatManager.NapCatState.STOPPED -> Color(0xFF64748B)
}

private fun OneBotClient.ConnectionState.toColor(): Color = when (this) {
    OneBotClient.ConnectionState.CONNECTED -> Color(0xFF10B981)
    OneBotClient.ConnectionState.CONNECTING -> Color(0xFFF59E0B)
    OneBotClient.ConnectionState.FAILED -> Color(0xFFEF4444)
    OneBotClient.ConnectionState.DISCONNECTED -> Color(0xFF64748B)
}
