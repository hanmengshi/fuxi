package com.ankireview.ui.review

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ankireview.sm2.Grade
import com.ankireview.ui.ReviewUiState
import com.ankireview.ui.ReviewViewModel
import com.ankireview.utils.MarkdownParser
import com.ankireview.utils.SectionType
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(viewModel: ReviewViewModel) {
    val state        by viewModel.state.collectAsState()
    val context      = LocalContext.current
    var analysisOpen by remember { mutableStateOf(false) }
    var swipeDx      by remember { mutableStateOf(0f) }
    val THRESHOLD    = 180f
    val snackState   = remember { SnackbarHostState() }

    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(JLatexMathPlugin.create(42f))
            .build()
    }

    LaunchedEffect(state.card?.path) { analysisOpen = false; swipeDx = 0f }
    LaunchedEffect(Unit) { viewModel.snack.collect { snackState.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.card?.name?.removeSuffix(".md") ?: "",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goToFolder() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    Text(
                        "${state.remaining} 剩",
                        modifier = Modifier.padding(end = 16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = state.progress,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

                HeatmapStrip(state.heatmap)
                StatsRow(state.done, state.dueCount, state.streak)

                if (state.isLoading) {
                    Box(
                        Modifier.fillMaxWidth().padding(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("加载题目…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    return@Column
                }

                val parsed = state.parsedCard ?: return@Column

                // Question card with swipe gesture
                Box(
                    Modifier.padding(16.dp).pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeDx < -THRESHOLD) viewModel.grade(Grade.AGAIN)
                                else if (swipeDx > THRESHOLD) viewModel.grade(Grade.EASY)
                                swipeDx = 0f
                            },
                            onDragCancel = { swipeDx = 0f },
                            onHorizontalDrag = { _, d -> swipeDx += d }
                        )
                    }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rotate((swipeDx / 30f).coerceIn(-8f, 8f))
                            .offset(x = (swipeDx / 4).dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(4.dp),
                        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(0.5f))
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            // Swipe hint overlays
                            if (abs(swipeDx) > 40f) {
                                val alpha = (abs(swipeDx) / THRESHOLD).coerceIn(0f, 1f)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (swipeDx < 0) {
                                        Card(
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFFB3261E).copy(alpha = alpha)
                                            )
                                        ) {
                                            Text(
                                                "← 重来",
                                                modifier = Modifier.padding(10.dp, 5.dp),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.size(1.dp))
                                    }
                                    if (swipeDx > 0) {
                                        Card(
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFF1565C0).copy(alpha = alpha)
                                            )
                                        ) {
                                            Text(
                                                "简单 →",
                                                modifier = Modifier.padding(10.dp, 5.dp),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            MarkdownView(
                                markdown = MarkdownParser.normalizeImages(
                                    parsed.question, state.imageUrls
                                ),
                                markwon  = markwon,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Grade buttons
                GradeButtons(state, viewModel)

                // Tag chips
                Row(
                    modifier = Modifier
                        .padding(16.dp, 4.dp, 16.dp, 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("难" to "🔥", "易" to "🍃", "易错" to "⚠️", "重点" to "⭐")
                        .forEach { (tag, icon) ->
                            OutlinedButton(
                                onClick = { viewModel.addTag(tag) },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(44.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Text("$icon $tag", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                }

                // Analysis toggle
                if (parsed.analysis.isNotEmpty()) {
                    val rotation by animateFloatAsState(
                        if (analysisOpen) 180f else 0f, label = "arr"
                    )
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 4.dp)
                            .clickable { analysisOpen = !analysisOpen },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp, 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "💡 查看详细解析",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ExpandMore, null,
                                modifier = Modifier.rotate(rotation),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (analysisOpen) {
                        parsed.analysis.forEach { section ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (section.type == SectionType.SUMMARY)
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.width(3.dp).height(15.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            section.title,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp, letterSpacing = 0.8.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    if (section.type == SectionType.KNOWLEDGE_POINTS) {
                                        section.body.split('\n')
                                            .map { it.trimStart('-', '*', ' ') }
                                            .filter { it.isNotBlank() }
                                            .forEach { kp ->
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = {
                                                        Text(kp, fontWeight = FontWeight.Bold)
                                                    },
                                                    modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                                                )
                                            }
                                    } else {
                                        MarkdownView(
                                            markdown = MarkdownParser.normalizeImages(
                                                section.body, state.imageUrls
                                            ),
                                            markwon  = markwon,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeButtons(state: ReviewUiState, viewModel: ReviewViewModel) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Triple(Grade.AGAIN, Color(0xFFB3261E), "🔴"),
            Triple(Grade.HARD,  Color(0xFFE65C00), "🟠"),
            Triple(Grade.GOOD,  Color(0xFF2E7D32), "🟢"),
            Triple(Grade.EASY,  Color(0xFF1565C0), "🔵"),
        ).forEach { (grade, color, emoji) ->
            Button(
                onClick  = { viewModel.grade(grade) },
                modifier = Modifier
                    .weight(if (grade == Grade.GOOD) 1.2f else 1f)
                    .height(72.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = color),
                enabled  = !state.isLoading
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$emoji ${grade.label}",
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (grade == Grade.AGAIN) "重来"
                        else "${state.intervals[grade] ?: "—"}天",
                        fontSize = 10.sp, color = Color.White.copy(0.85f)
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownView(markdown: String, markwon: Markwon, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                textSize = 17f
                setLineSpacing(4f, 1.2f)
            }
        },
        update = { tv -> markwon.setMarkdown(tv, markdown) }
    )
}

@Composable
private fun HeatmapStrip(heatmap: Map<String, Int>) {
    val today = java.time.LocalDate.now()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp, 10.dp)
    ) {
        Text(
            "近30天活跃度",
            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (29 downTo 0).forEach { i ->
                val date  = today.minusDays(i.toLong()).toString()
                val count = heatmap[date] ?: 0
                val color = when {
                    count == 0  -> MaterialTheme.colorScheme.surfaceVariant
                    count <= 5  -> Color(0xFF78E08F)
                    count <= 10 -> Color(0xFF38ADA9)
                    else        -> Color(0xFF079992)
                }
                Box(
                    Modifier
                        .weight(1f).aspectRatio(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                        .then(
                            if (i == 0) Modifier.border(
                                1.5.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(2.dp)
                            ) else Modifier
                        )
                )
            }
        }
    }
}

@Composable
private fun StatsRow(done: Int, due: Int, streak: Int) {
    Row(
        modifier = Modifier
            .padding(14.dp, 10.dp, 14.dp, 0.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(
            "$done"    to "已完成",
            "$due"     to "今日到期",
            "${streak}天" to "连续复习"
        ).forEach { (num, label) ->
            Card(
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(14.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        num,
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
