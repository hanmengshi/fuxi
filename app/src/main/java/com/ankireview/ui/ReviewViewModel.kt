package com.ankireview.ui

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ankireview.api.WebDavItem
import com.ankireview.api.WebDavRepository
import com.ankireview.data.AppDatabase
import com.ankireview.data.CardEntity
import com.ankireview.data.HeatmapEntity
import com.ankireview.sm2.Grade
import com.ankireview.sm2.SM2
import com.ankireview.sm2.SM2Card
import com.ankireview.utils.MarkdownParser
import com.ankireview.utils.ParsedCard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("anki_prefs")
val KEY_USERNAME    = stringPreferencesKey("username")
val KEY_PASSWORD    = stringPreferencesKey("password")
val KEY_FOLDER_PATH = stringPreferencesKey("folder_path")
val KEY_DAILY_LIMIT = intPreferencesKey("daily_limit")

sealed class Screen {
    object Login  : Screen()
    object Folder : Screen()
    object Review : Screen()
    object Finish : Screen()
}

data class ReviewUiState(
    val isLoading: Boolean              = false,
    val error: String?                  = null,
    val savedUsername: String           = "",
    val savedPassword: String           = "",
    val savedFolderPath: String         = "",
    val dailyLimit: Int                 = 20,
    val folderItems: List<WebDavItem>   = emptyList(),
    val currentPath: String             = "",
    val pathStack: List<String>         = emptyList(),
    val selectedFiles: List<WebDavItem> = emptyList(),
    val card: CardEntity?               = null,
    val parsedCard: ParsedCard?         = null,
    val imageUrls: Map<String, String>  = emptyMap(),
    val intervals: Map<Grade, Int>      = emptyMap(),
    val remaining: Int                  = 0,
    val done: Int                       = 0,
    val progress: Float                 = 0f,
    val heatmap: Map<String, Int>       = emptyMap(),
    val dueCount: Int                   = 0,
    val streak: Int                     = 0,
    val todayDone: Int                  = 0
)

private const val TAG = "ReviewVM"

@HiltViewModel
class ReviewViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase
) : ViewModel() {

    private val _state  = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Login)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _snack  = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snack: SharedFlow<String> = _snack.asSharedFlow()

    private var webDav: WebDavRepository? = null
    private var sessionCards: List<CardEntity> = emptyList()
    private var currentIndex: Int = 0
    private val imageCache = mutableMapOf<String, String>()

    // Simple flag - volatile ensures visibility across threads
    @Volatile private var grading = false

    init {
        // Use separate launches so one failure doesn't kill the other
        viewModelScope.launch {
            try {
                val prefs = ctx.dataStore.data.first()
                val u = prefs[KEY_USERNAME]    ?: ""
                val p = prefs[KEY_PASSWORD]    ?: ""
                val f = prefs[KEY_FOLDER_PATH] ?: ""
                val d = prefs[KEY_DAILY_LIMIT] ?: 20
                _state.update { it.copy(savedUsername=u,savedPassword=p,savedFolderPath=f,dailyLimit=d) }
                if (u.isNotBlank() && p.isNotBlank()) {
                    initWebDav(u, p)
                    loadFolder(f)
                    _screen.value = Screen.Folder
                }
            } catch (t: Throwable) {
                Log.e(TAG, "init error", t)
            }
        }
        viewModelScope.launch {
            try {
                db.cardDao().getDueCount(LocalDate.now().toString()).collect { cnt ->
                    _state.update { it.copy(dueCount = cnt) }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "dueCount error", t)
            }
        }
    }

    fun connect(username: String, password: String, folderPath: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                initWebDav(username, password)
                val ok = webDav!!.testConnection()
                if (!ok) throw Exception("账号或密码错误")
                savePrefs(username, password, folderPath, _state.value.dailyLimit)
                loadFolder(folderPath)
                _screen.value = Screen.Folder
            } catch (t: Throwable) {
                Log.e(TAG, "connect error", t)
                _state.update { it.copy(error = t.message, isLoading = false) }
                _snack.tryEmit("连接失败：${t.message}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try { ctx.dataStore.edit { it.clear() } } catch (_: Throwable) {}
            webDav = null
            imageCache.clear()
            sessionCards = emptyList()
            currentIndex = 0
            grading = false
            _state.value = ReviewUiState()
            _screen.value = Screen.Login
        }
    }

    private fun initWebDav(u: String, p: String) {
        webDav = WebDavRepository(WebDavRepository.JIANGUOYUN_URL, u, p)
    }

    fun loadFolder(path: String, pushToStack: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val items = withContext(Dispatchers.IO) { webDav!!.listFolder(path) }
                val stack = if (pushToStack && path != _state.value.currentPath)
                    _state.value.pathStack + _state.value.currentPath
                else _state.value.pathStack
                _state.update { it.copy(folderItems=items, currentPath=path, pathStack=stack, isLoading=false) }
            } catch (t: Throwable) {
                Log.e(TAG, "loadFolder error", t)
                _state.update { it.copy(isLoading = false) }
                _snack.tryEmit("加载失败：${t.message}")
            }
        }
    }

    fun enterFolder(f: WebDavItem) = loadFolder(f.href, true)

    fun navigateUp(): Boolean {
        val stack = _state.value.pathStack
        return if (stack.isNotEmpty()) {
            val prev = stack.last()
            _state.update { it.copy(pathStack = stack.dropLast(1)) }
            loadFolder(prev)
            true
        } else false
    }

    fun canNavigateUp() = _state.value.pathStack.isNotEmpty()
    fun refreshFolder()  = loadFolder(_state.value.currentPath)

    fun startReview(files: List<WebDavItem>) {
        val dav = webDav ?: run { _snack.tryEmit("未连接，请重新登录"); return }
        if (files.isEmpty()) { _snack.tryEmit("没有题目文件"); return }
        grading = false
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, selectedFiles = files) }
            try {
                val today = LocalDate.now().toString()
                val limit = _state.value.dailyLimit

                val allCards = withContext(Dispatchers.IO) {
                    files.map { f ->
                        try { db.cardDao().getCard(f.href) } catch (_: Throwable) { null }
                            ?: CardEntity(path = f.href, name = f.name).also {
                                try { db.cardDao().upsertCard(it) } catch (_: Throwable) {}
                            }
                    }
                }

                val due    = allCards.filter { it.due <= today }.shuffled()
                val newC   = allCards.filter { it.reps == 0 && it.due > today }.shuffled()
                val future = allCards.filter { it.reps > 0  && it.due > today }.shuffled()
                val session = mutableListOf<CardEntity>().apply {
                    addAll(due)
                    if (size < limit) addAll(newC.take(limit - size))
                    if (size < limit) addAll(future.take(limit - size))
                    if (isEmpty()) addAll(allCards.shuffled())
                }

                sessionCards = session.take(limit)
                currentIndex = 0

                if (sessionCards.isEmpty()) {
                    _state.update { it.copy(isLoading = false) }
                    _snack.tryEmit("没有可复习的卡片")
                    return@launch
                }

                val hm = buildHeatmap()
                val t2 = LocalDate.now().toString()
                _state.update { it.copy(
                    remaining = sessionCards.size, done = 0, progress = 0f,
                    heatmap = hm, streak = calcStreak(hm), todayDone = hm[t2] ?: 0
                )}

                val ok = loadCardAt(dav, 0)
                if (ok) _screen.value = Screen.Review
                else {
                    _state.update { it.copy(isLoading = false) }
                    _snack.tryEmit("题目加载失败")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "startReview error", t)
                _state.update { it.copy(isLoading = false) }
                _snack.tryEmit("启动失败：${t.message}")
            }
        }
    }

    // ── grade() — the key function ────────────────
    // Rules:
    //  1. Only ONE plain viewModelScope.launch { } — no extra Job or Dispatcher args
    //  2. Every single operation wrapped in its own try-catch
    //  3. grading flag reset in finally
    fun grade(g: Grade) {
        if (grading) return
        val dav   = webDav                       ?: return
        val idx   = currentIndex
        val cards = sessionCards
        val card  = cards.getOrNull(idx)         ?: run { _screen.value = Screen.Finish; return }
        val total = cards.size
        grading = true

        viewModelScope.launch {
            try {
                // Step 1: save result to DB (IO thread, non-fatal if fails)
                withContext(Dispatchers.IO) {
                    try {
                        val sm2 = SM2Card(card.interval, card.ease, card.reps, card.due, card.lapses)
                        val res = SM2.calculate(sm2, g)
                        val upd = card.copy(
                            interval = res.interval, ease   = res.ease,
                            reps     = res.reps,     due    = res.due,
                            lapses   = res.lapses
                        )
                        db.cardDao().upsertCard(upd)
                        val today = LocalDate.now().toString()
                        val hm    = db.cardDao().getHeatmap(today)
                        db.cardDao().upsertHeatmap(HeatmapEntity(today, (hm?.count ?: 0) + 1))
                    } catch (t: Throwable) {
                        Log.e(TAG, "DB write error (non-fatal)", t)
                    }
                }

                // Step 2: advance index and update UI (Main thread)
                val nextIdx = idx + 1
                currentIndex = nextIdx

                val hm    = buildHeatmap()
                val today = LocalDate.now().toString()
                _state.update { it.copy(
                    done      = nextIdx,
                    progress  = nextIdx.toFloat() / total.coerceAtLeast(1),
                    remaining = (total - nextIdx).coerceAtLeast(0),
                    heatmap   = hm,
                    todayDone = hm[today] ?: 0,
                    streak    = calcStreak(hm)
                )}

                // Step 3: check if finished
                if (nextIdx >= total) {
                    _screen.value = Screen.Finish
                    return@launch
                }

                // Step 4: load next card
                val loaded = loadCardAt(dav, nextIdx)
                if (!loaded) {
                    // Skip one more if current failed to load
                    val skip = nextIdx + 1
                    currentIndex = skip
                    if (skip >= total) {
                        _screen.value = Screen.Finish
                    } else {
                        loadCardAt(dav, skip)
                    }
                }

            } catch (t: Throwable) {
                Log.e(TAG, "grade() error", t)
                _state.update { it.copy(isLoading = false) }
                _snack.tryEmit("错误：${t.javaClass.simpleName}")
            } finally {
                grading = false
            }
        }
    }

    private suspend fun loadCardAt(dav: WebDavRepository, index: Int): Boolean {
        if (index < 0 || index >= sessionCards.size) return false
        val card = sessionCards[index]
        _state.update { it.copy(isLoading = true, card = card, parsedCard = null, imageUrls = emptyMap()) }
        return try {
            val raw    = withContext(Dispatchers.IO) { dav.readFile(card.path) }
            val parsed = try { MarkdownParser.parse(raw) } catch (t: Throwable) {
                Log.e(TAG, "parse error", t)
                ParsedCard(raw, emptyList())
            }
            val urls = try { loadImages(dav, card.path, parsed) } catch (t: Throwable) {
                Log.e(TAG, "image error", t)
                emptyMap()
            }
            val sm2  = SM2Card(card.interval, card.ease, card.reps, card.due, card.lapses)
            val prev = try { SM2.preview(sm2) } catch (_: Throwable) { emptyMap() }
            _state.update { it.copy(
                card       = card,
                parsedCard = parsed,
                imageUrls  = urls,
                intervals  = prev,
                isLoading  = false,
                remaining  = sessionCards.size - index
            )}
            true
        } catch (t: Throwable) {
            Log.e(TAG, "loadCardAt[$index] error", t)
            _state.update { it.copy(isLoading = false) }
            false
        }
    }

    private suspend fun loadImages(
        dav: WebDavRepository, cardPath: String, parsed: ParsedCard
    ): Map<String, String> {
        val names = (MarkdownParser.extractObsidianImages(parsed.question) +
            parsed.analysis.flatMap { MarkdownParser.extractObsidianImages(it.body) }).distinct()
        if (names.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        withContext(Dispatchers.IO) {
            for (name in names) {
                val cached = imageCache[name]
                if (cached != null) { map[name] = cached; continue }
                try {
                    val bytes = dav.readFileBytes(dav.resolveImagePath(cardPath, name))
                    if (bytes.isNotEmpty()) {
                        val b64  = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val ext  = name.substringAfterLast(".").lowercase()
                        val mime = when (ext) { "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"; else -> "image/jpeg" }
                        val uri  = "data:$mime;base64,$b64"
                        map[name] = uri
                        imageCache[name] = uri
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "skip image $name: ${t.message}")
                }
            }
        }
        return map
    }

    fun addTag(tag: String) {
        val card = sessionCards.getOrNull(currentIndex) ?: return
        viewModelScope.launch {
            _snack.tryEmit("✅ 已标记：$tag")
            withContext(Dispatchers.IO) {
                try {
                    val dav = webDav ?: return@withContext
                    val raw = dav.readFile(card.path)
                    val conflict = mapOf("难" to "易", "易" to "难")[tag]
                    var c = raw
                    if (c.startsWith("---")) {
                        val end = c.indexOf("\n---", 3)
                        if (end != -1) {
                            var head = c.substring(0, end)
                            if (conflict != null) head = head.replace(Regex("  - \"#?$conflict\"?\n"), "")
                            if (!head.contains("#$tag")) {
                                head = if (head.contains("tags:"))
                                    head.replace(Regex("(tags:[ \\t]*\\r?\\n)"), "$1  - \"#$tag\"\n")
                                else head + "\ntags:\n  - \"#$tag\""
                            }
                            c = head + c.substring(end)
                        }
                    } else {
                        c = "---\ntags:\n  - \"#$tag\"\n---\n\n$c"
                    }
                    dav.writeFile(card.path, c)
                } catch (t: Throwable) {
                    Log.e(TAG, "addTag error", t)
                }
            }
        }
    }

    fun setDailyLimit(limit: Int) {
        viewModelScope.launch {
            savePrefs(
                _state.value.savedUsername, _state.value.savedPassword,
                _state.value.savedFolderPath, limit
            )
        }
    }

    fun goToFolder()    { _screen.value = Screen.Folder }
    fun restartReview() {
        grading = false
        currentIndex = 0
        sessionCards = emptyList()
        startReview(_state.value.selectedFiles)
    }

    private suspend fun buildHeatmap(): Map<String, Int> {
        return try {
            val from = LocalDate.now().minusDays(29).toString()
            withContext(Dispatchers.IO) {
                db.cardDao().getHeatmapRange(from).associate { it.date to it.count }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "buildHeatmap error", t)
            emptyMap()
        }
    }

    private fun calcStreak(hm: Map<String, Int>): Int {
        var s = 0
        for (i in 0..364) {
            val d = LocalDate.now().minusDays(i.toLong()).toString()
            if ((hm[d] ?: 0) > 0) s++ else if (i > 0) break
        }
        return s
    }

    private suspend fun savePrefs(u: String, p: String, f: String, d: Int) {
        try {
            ctx.dataStore.edit { prefs ->
                prefs[KEY_USERNAME]    = u
                prefs[KEY_PASSWORD]    = p
                prefs[KEY_FOLDER_PATH] = f
                prefs[KEY_DAILY_LIMIT] = d
            }
        } catch (t: Throwable) {
            Log.e(TAG, "savePrefs error", t)
        }
        _state.update { it.copy(savedUsername=u, savedPassword=p, savedFolderPath=f, dailyLimit=d) }
    }
}
