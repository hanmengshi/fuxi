package com.ankireview.ui.folder

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankireview.ui.ReviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(viewModel: ReviewViewModel) {
    val state   by viewModel.state.collectAsState()
    val mdFiles  = state.folderItems.filter { it.isMdFile }
    val folders  = state.folderItems.filter { it.isDirectory }
    var showSettings by remember { mutableStateOf(false) }

    // Back press: go up folder tree, or do nothing if at root
    BackHandler(enabled = viewModel.canNavigateUp()) {
        viewModel.navigateUp()
    }

    // Settings dialog for daily limit
    if (showSettings) {
        DailyLimitDialog(
            current = state.dailyLimit,
            onConfirm = { limit ->
                viewModel.setDailyLimit(limit)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("选择题目文件夹", fontWeight = FontWeight.Bold)
                        val displayPath = state.currentPath.ifBlank { "根目录" }
                        Text(displayPath, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (viewModel.canNavigateUp()) {
                        // Show back arrow when inside a subfolder
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, "返回上级")
                        }
                    } else {
                        // Show logout at root
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.Logout, "退出登录")
                        }
                    }
                },
                actions = {
                    // Daily limit settings
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "设置每日数量")
                    }
                    IconButton(onClick = { viewModel.refreshFolder() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            if (mdFiles.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startReview(mdFiles) },
                    icon    = { Icon(Icons.Default.PlayArrow, null) },
                    text    = { Text("开始复习（${mdFiles.size} 题）") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->

        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {

            // Daily limit chip
            item {
                Row(
                    Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { showSettings = true },
                        label   = { Text("每日复习：${state.dailyLimit} 题") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) }
                    )
                }
            }

            // Current folder .md files - click to start review
            if (mdFiles.isNotEmpty()) {
                item {
                    Text("此文件夹（${mdFiles.size} 个题目）",
                        Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold)
                }
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { viewModel.startReview(mdFiles) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("▶", fontSize = 22.sp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("开始复习此文件夹",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${mdFiles.size} 个题目 · 每日最多 ${state.dailyLimit} 题",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.PlayArrow, null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Subfolders - click to enter
            if (folders.isNotEmpty()) {
                item {
                    Text("子文件夹",
                        Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold)
                }
                items(folders) { folder ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { viewModel.enterFolder(folder) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📂", fontSize = 26.sp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text("点击进入", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (folders.isEmpty() && mdFiles.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📭", fontSize = 48.sp)
                            Text("此文件夹没有 .md 文件",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyLimitDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(5, 10, 15, 20)
    var selected by remember { mutableIntStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每日复习数量", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择每次复习会话最多复习的题目数量：",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                options.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = opt }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == opt,
                            onClick  = { selected = opt }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("$opt 题", fontSize = 16.sp,
                            fontWeight = if (selected == opt) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("确定", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
