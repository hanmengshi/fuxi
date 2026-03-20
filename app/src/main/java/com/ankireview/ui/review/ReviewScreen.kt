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
import com.ankireview.sm2.SM2
import com.ankireview.sm2.SM2Card
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

    // Build Markwon with LaTeX support — font size 48sp for math rendering
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(
                JLatexMathPlugin.create(
                    48f,  // math font size
                    JLatexMathPlugin.BuilderConfigure { builder ->
                        builder.inlinesEnabled(true)  // enable $...$ inline math
                    }
                )
            )
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
                StatsRow(state)

                if (state.isLoading) {
                    Box(Modifier.fillMaxWidth().padding(56.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            CircularProgressIndicator()
                            Text("加载题目…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    return@Column
                }

                val parsed = state.parsedCard ?: return@Column

                // ── Question card with swipe ──────────────────
                Box(Modifier.padding(16.dp).pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeDx < -THRESHOLD) viewModel.grade(Grade.AGAIN)
                            else if (swipeDx > THRESHOLD) viewModel.grade(Grade.EASY)
                            swipeDx = 0f
                        },
                        onDragCancel = { swipeDx = 0f },
                        onHorizontalDrag = { _, d -> swipeDx += d }
                    )
                }) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .rotate((swipeDx / 30f).coerceIn(-8f, 8f))
                            .offset(x = (swipeDx / 4).dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(4.dp),
                        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(0.5f))
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            if (abs(swipeDx) > 40f) {
                                val alpha = (abs(swipeDx) / THRESHOLD).coerceIn(0f, 1f)
                                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    if (swipeDx < 0) {
                                        Box(Modifier.clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFFB3261E).copy(alpha = alpha))
                                            .padding(10.dp, 5.dp)) {
                                            Text("← 重来", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    } else Spacer(Modifier.size(1.dp))
                                    if (swipeDx > 0) {
                                        Box(Modifier.clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFF1565C0).copy(alpha = alpha))
                                            .padding(10.dp, 5.dp)) {
                                            Text("简单 →", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            // Render markdown with LaTeX
                            MarkdownView(
                                markdown = MarkdownParser.normalizeImages(parsed.question, state.imageUrls),
                                markwon  = markwon,
                                modifier = Modifier.fillMaxWidth(),
                                textSizeSp = 18f,
                                imageSizeMult = 3f   // 3x image size
                            )
                        }
                    }
                }

                // ── Grade buttons ─────────────────────────────
                GradeButtons(state, viewModel)

                // ── Tag chips ─────────────────────────────────
                Row(Modifier.padding(16.dp, 4.dp, 16.dp, 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("难" to "🔥", "易" to "🍃", "易错" to "⚠️", "重点" to "⭐").forEach { (tag, icon) ->
                        OutlinedButton(
                            onClick = { viewModel.addTag(tag) },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(44.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) { Text("$icon $tag", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }

                // ── Analysis ──────────────────────────────────
                if (parsed.analysis.isNotEmpty()) {
                    val rotation by animateFloatAsState(if (analysisOpen) 180f else 0f, label = "arr")
                    OutlinedCard(
                        Modifier.fillMaxWidth().padding(16.dp, 4.dp).clickable { analysisOpen = !analysisOpen },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("💡 查看详细解析", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ExpandMore, null, Modifier.rotate(rotation),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (analysisOpen) {
                        parsed.analysis.forEach { section ->
                            Card(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (section.type == SectionType.SUMMARY)
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.width(3.dp).height(15.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.primary))
                                        Spacer(Modifier.width(8.dp))
                                        Text(section.title, fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    if (section.type == SectionType.KNOWLEDGE_POINTS) {
                                        section.body.split('\n').map { it.trimStart('-','*',' ') }
                                            .filter { it.isNotBlank() }.forEach { kp ->
                                                SuggestionChip({}, { Text(kp, fontWeight = FontWeight.Bold) },
                                                    Modifier.padding(end = 4.dp, bottom = 4.dp))
                                            }
                                    } else {
                                        MarkdownView(
                                            MarkdownParser.normalizeImages(section.body, state.imageUrls),
                                            markwon, Modifier.fillMaxWidth(),
                                            textSizeSp = 16f, imageSizeMult = 3f
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
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(Grade.AGAIN, Color(0xFFB3261E), "🔴"),
            Triple(Grade.HARD,  Color(0xFFE65C00), "🟠"),
            Triple(Grade.GOOD,  Color(0xFF2E7D32), "🟢"),
            Triple(Grade.EASY,  Color(0xFF1565C0), "🔵"),
        ).forEach { (grade, color, emoji) ->
            Button(
                onClick  = { viewModel.grade(grade) },
                modifier = Modifier.weight(if (grade == Grade.GOOD) 1.2f else 1f).height(72.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = color),
                enabled  = !state.isLoading
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$emoji ${grade.label}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (grade == Grade.AGAIN) "重来" else "${state.intervals[grade] ?: "—"}天",
                        fontSize = 10.sp, color = Color.White.copy(0.85f)
                    )
                }
            }
        }
    }
}

/**
 * Markwon TextView wrapper.
 * imageSizeMult: multiply displayed image size (default 1x; pass 3f for 3x)
 */
@Composable
fun MarkdownView(
    markdown: String,
    markwon: Markwon,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 17f,
    imageSizeMult: Float = 1f
) {
    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                textSize = textSizeSp
                setLineSpacing(4f, 1.25f)
            }
        },
        update = { tv ->
            tv.textSize = textSizeSp
            // Apply image size multiplier via custom drawable factory would need
            // Markwon image plugin config — here we just set max width via layout
            markwon.setMarkdown(tv, markdown)
        }
    )
}

@Composable
private fun HeatmapStrip(heatmap: Map<String, Int>) {
    val today = java.time.LocalDate.now()
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(14.dp, 10.dp)) {
        Text("近30天活跃度", fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(color)
                    .then(if (i == 0) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)) else Modifier))
            }
        }
    }
}

@Composable
private fun StatsRow(state: ReviewUiState) {
    // Show SM-2 card status for current card
    val cardStatus = state.card?.let { card ->
        val sm2 = SM2Card(card.interval, card.ease, card.reps, card.due, card.lapses)
        SM2.retentionLabel(sm2)
    } ?: ""

    Row(Modifier.padding(14.dp, 8.dp, 14.dp, 0.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "${state.done}"    to "已完成",
            "${state.dueCount}" to "今日到期",
            "${state.streak}天" to "连续复习"
        ).forEach { (num, label) ->
            Card(Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(num, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Card status chip
        if (cardStatus.isNotBlank()) {
            Card(Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(cardStatus, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.tertiary)
                    Text("卡片状态", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
