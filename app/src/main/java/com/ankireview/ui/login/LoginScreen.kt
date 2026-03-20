package com.ankireview.ui.login

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankireview.ui.ReviewViewModel

@Composable
fun LoginScreen(viewModel: ReviewViewModel) {
    val state by viewModel.state.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var folder   by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    // Fill in saved credentials once DataStore loads
    LaunchedEffect(state.savedUsername) {
        if (username.isEmpty() && state.savedUsername.isNotEmpty())
            username = state.savedUsername
    }
    LaunchedEffect(state.savedPassword) {
        if (password.isEmpty() && state.savedPassword.isNotEmpty())
            password = state.savedPassword
    }
    LaunchedEffect(state.savedFolderPath) {
        if (folder.isEmpty() && state.savedFolderPath.isNotEmpty())
            folder = state.savedFolderPath
    }

    val floatY by rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOut), RepeatMode.Reverse),
        label = "float"
    )

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(
                listOf(Color(0xFF1a1a2e), Color(0xFF16213e), Color(0xFF0f3460))
            ))
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🧠", fontSize = 72.sp, modifier = Modifier.offset(y = floatY.dp))
            Spacer(Modifier.height(12.dp))
            Text("题目复习", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("连接坚果云，随时随地复习题目",
                fontSize = 14.sp, color = Color.White.copy(0.65f),
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))

            Card(Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.09f)),
                border = BorderStroke(1.dp, Color.White.copy(0.15f))
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // Username
                    Column {
                        Text("坚果云账号（邮箱）", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color.White.copy(0.55f))
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = username, onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("your@email.com", color = Color.White.copy(0.3f)) },
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = Color.White.copy(0.5f)) },
                            shape = RoundedCornerShape(12.dp), colors = fieldColors(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    }

                    // Password
                    Column {
                        Text("应用密码（非登录密码）", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color.White.copy(0.55f))
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("坚果云应用密码", color = Color.White.copy(0.3f)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color.White.copy(0.5f)) },
                            trailingIcon = {
                                IconButton(onClick = { showPass = !showPass }) {
                                    Icon(
                                        if (showPass) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        null, tint = Color.White.copy(0.5f)
                                    )
                                }
                            },
                            visualTransformation = if (showPass) VisualTransformation.None
                                                   else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp), colors = fieldColors(), singleLine = true
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("请在坚果云网页 账户信息 - 安全选项 - 第三方应用管理 中生成应用密码",
                            fontSize = 11.sp, color = Color.White.copy(0.4f), lineHeight = 16.sp)
                    }

                    // Folder
                    Column {
                        Text("题目文件夹路径（留空则为根目录）",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color.White.copy(0.55f))
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = folder, onValueChange = { folder = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("例如：72数学", color = Color.White.copy(0.3f)) },
                            leadingIcon = { Icon(Icons.Default.Folder, null, tint = Color.White.copy(0.5f)) },
                            shape = RoundedCornerShape(12.dp), colors = fieldColors(), singleLine = true
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = { viewModel.connect(username.trim(), password.trim(), folder.trim()) },
                        enabled = username.isNotBlank() && password.isNotBlank() && !state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("连接中...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudSync, null)
                            Spacer(Modifier.width(8.dp))
                            Text("连接坚果云", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    state.error?.let { err ->
                        Text(err, color = Color(0xFFFFB4AB), fontSize = 13.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.07f)),
                border = BorderStroke(1.dp, Color.White.copy(0.12f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("如何获取应用密码", fontWeight = FontWeight.Bold,
                        color = Color.White.copy(0.85f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    listOf(
                        "打开 jianguoyun.com 登录账号",
                        "右上角头像 - 账户信息",
                        "左侧菜单 - 安全选项",
                        "第三方应用管理 - 添加应用密码",
                        "名称填写题目复习 - 生成 - 复制密码"
                    ).forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Surface(RoundedCornerShape(10.dp), color = Color(0xFF6750A4).copy(0.5f),
                                modifier = Modifier.size(20.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${i+1}", fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(step, fontSize = 12.sp, color = Color.White.copy(0.55f), lineHeight = 18.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor        = Color.White,
    unfocusedTextColor      = Color.White,
    focusedBorderColor      = Color(0xFF6750A4),
    unfocusedBorderColor    = Color.White.copy(0.25f),
    cursorColor             = Color.White,
    focusedContainerColor   = Color.White.copy(0.08f),
    unfocusedContainerColor = Color.White.copy(0.06f)
)
