package com.qqbot.keywordbot.ui.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.qqbot.keywordbot.data.entity.KeywordReply
import com.qqbot.keywordbot.data.entity.MatchType
import com.qqbot.keywordbot.data.entity.Persona
import com.qqbot.keywordbot.data.entity.ReplyType

/**
 * 关键词管理页：规则列表 + 新增/编辑表单
 */
@Composable
fun KeywordScreen(viewModel: KeywordViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsState()
    val editingRule by viewModel.editingRule.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Text(
                "关键词回复规则",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "共 ${rules.size} 条规则",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    KeywordRuleCard(
                        rule = rule,
                        onEdit = { viewModel.startEdit(rule) },
                        onDelete = { viewModel.deleteRule(rule.id) },
                        onToggle = { viewModel.toggleEnabled(rule) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.startCreate() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "新增规则")
        }
    }

    // 编辑对话框
    editingRule?.let { rule ->
        KeywordEditDialog(
            rule = rule,
            personas = viewModel.personas.collectAsState().value,
            onDismiss = { viewModel.dismissEdit() },
            onSave = { viewModel.saveRule(it) }
        )
    }
}

@Composable
private fun KeywordRuleCard(
    rule: KeywordReply,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rule.keyword,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagChip(rule.matchType.name, MaterialTheme.colorScheme.primary)
                        TagChip(rule.replyType.name, MaterialTheme.colorScheme.tertiary)
                    }
                }
                Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
            }

            Spacer(modifier = Modifier.height(12.dp))

            rule.replyText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "回复: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
internal fun TagChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeywordEditDialog(
    rule: KeywordReply,
    personas: List<Persona>,
    onDismiss: () -> Unit,
    onSave: (KeywordReply) -> Unit
) {
    var keyword by remember { mutableStateOf(rule.keyword) }
    var matchType by remember { mutableStateOf(rule.matchType) }
    var replyType by remember { mutableStateOf(rule.replyType) }
    var replyText by remember { mutableStateOf(rule.replyText ?: "") }
    var replyImage by remember { mutableStateOf(rule.replyImage ?: "") }
    var personaId by remember { mutableStateOf(rule.personaId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (rule.id == 0L) "新增规则" else "编辑规则",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("关键词") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                EnumDropdown(
                    label = "匹配方式",
                    selected = matchType.name,
                    options = MatchType.entries.map { it.name },
                    onSelect = { matchType = MatchType.valueOf(it) }
                )

                EnumDropdown(
                    label = "回复类型",
                    selected = replyType.name,
                    options = ReplyType.entries.map { it.name },
                    onSelect = { replyType = ReplyType.valueOf(it) }
                )

                if (replyType == ReplyType.TEXT || replyType == ReplyType.TEXT_IMAGE) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("回复文本") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (replyType == ReplyType.IMAGE || replyType == ReplyType.TEXT_IMAGE) {
                    OutlinedTextField(
                        value = replyImage,
                        onValueChange = { replyImage = it },
                        label = { Text("图片路径/URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (replyType == ReplyType.AI) {
                    if (personas.isNotEmpty()) {
                        EnumDropdown(
                            label = "选择人设",
                            selected = personaId?.let { id -> personas.find { it.id == id }?.name } ?: "请选择",
                            options = personas.map { it.name },
                            onSelect = { name -> personaId = personas.find { it.name == name }?.id }
                        )
                    } else {
                        Text(
                            "暂无可用人设，请先在「人设」页面创建",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    rule.copy(
                        keyword = keyword,
                        matchType = matchType,
                        replyType = replyType,
                        replyText = replyText.ifBlank { null },
                        replyImage = replyImage.ifBlank { null },
                        personaId = personaId
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
