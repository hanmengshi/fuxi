package com.ankireview.ui.folder

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
import com.ankireview.api.WebDavItem
import com.ankireview.ui.ReviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(viewModel: ReviewViewModel) {
    val state   by viewModel.state.collectAsState()
    val mdFiles  = state.folderItems.filter { it.isMdFile }
    val folders  = state.folderItems.filter { it.isDirectory }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("选择题目文件夹", fontWeight = FontWeight.Bold)
                        if (state.currentPath.isNotBlank())
                            Text(
                                state.currentPath,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.disconnect() }) {
                        Icon(Icons.Default.Logout, "退出登录")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadFolder(state.currentPath) }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            if (mdFiles.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.selectAndStartReview(mdFiles, state.currentPath)
                    },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    text = { Text("开始复习（${mdFiles.size} 题）") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
            // Current folder md files
            if (mdFiles.isNotEmpty()) {
                item {
                    Text(
                        "此文件夹（${mdFiles.size} 个题目）",
                        modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable {
                                viewModel.selectAndStartReview(mdFiles, state.currentPath)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("▶", fontSize = 22.sp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "开始复习此文件夹",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                                )
                                Text(
                                    "${mdFiles.size} 个 .md 题目文件",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.PlayArrow, null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Subfolders
            if (folders.isNotEmpty()) {
                item {
                    Text(
                        "子文件夹",
                        modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(folders) { folder ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { viewModel.loadFolder(folder.href) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📂", fontSize = 26.sp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(
                                    "点击进入",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (folders.isEmpty() && mdFiles.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📭", fontSize = 48.sp)
                            Text(
                                "此文件夹没有 .md 文件",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "请检查文件夹路径是否正确",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
