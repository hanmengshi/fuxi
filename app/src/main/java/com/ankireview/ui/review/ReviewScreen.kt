package com.ankireview.ui.review

import android.graphics.BitmapFactory
import android.text.method.LinkMovementMethod
import android.util.Base64
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ankireview.sm2.Grade
import com.ankireview.sm2.SM2
import com.ankireview.sm2.SM2Card
import com.ankireview.ui.ReviewUiState
import com.ankireview.ui.ReviewViewModel
import com.ankireview.utils.MarkdownParser
import com.ankireview.utils.SectionType
import io.noties.markwon.Markwon
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
    var lightboxUrl  by remember { mutableStateOf<String?>(null) }

    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .build()
    }

    LaunchedEffect(state.card?.path) { analysisOpen = false; swipeDx = 0f }
    LaunchedEffect(Unit) { viewModel.snack.collect { snackState.showSnackbar(it) } }

    lightboxUrl?.let { url ->
        ImageLightboxDialog(url = url, onDismiss = { lightboxUrl = null })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(state.card?.name?.removeSuffix(".md") ?: "",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goToFolder() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    Text("${state.remaining} 剩", Modifier.padding(end = 16.dp),
                        color = MaterialTheme.colo
