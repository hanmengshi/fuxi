package com.ankireview.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ankireview.ui.folder.FolderScreen
import com.ankireview.ui.login.LoginScreen
import com.ankireview.ui.review.ReviewScreen

@Composable
fun AppNavHost(viewModel: ReviewViewModel = hiltViewModel()) {
    val screen by viewModel.screen.collectAsState()
    when (screen) {
        is Screen.Login  -> LoginScreen(viewModel)
        is Screen.Folder -> FolderScreen(viewModel)
        is Screen.Review -> ReviewScreen(viewModel)
        is Screen.Finish -> FinishScreen(viewModel)
    }
}

@Composable
fun FinishScreen(viewModel: ReviewViewModel) {
    val state by viewModel.state.collectAsState()
    val scale by rememberInfiniteTransition(label = "p").animateFloat(
        1f, 1.1f,
        infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse), label = "p"
    )
    Column(
        Modifier.fillMaxSize().padding(32.dp).windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎉", fontSize = (72 * scale).sp)
        Spacer(Modifier.height(20.dp))
        Text("今日任务完成！", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "今天复习了 ${state.todayDone} 张卡片\n连续复习 ${state.streak} 天 🔥",
            fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, lineHeight = 26.sp
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = { viewModel.restartReview() },
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 24.dp),
            shape = RoundedCornerShape(26.dp)
        ) { Text("🔄 再来一轮", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { viewModel.goToFolder() },
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 24.dp),
            shape = RoundedCornerShape(26.dp)
        ) { Text("📂 换个文件夹", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
}
