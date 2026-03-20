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
import kotlinx.coroutines.CoroutineExceptionHandler
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

private const val TAG = "ReviewViewModel"

@HiltViewModel
class ReviewViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase
) : ViewModel() {

    private val _state  = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Login)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _snack  = MutableSharedFlow<String>()
    val snack: SharedFlow<String> = _snack.asSharedFlow()

    // Global exception handler - prevents any coroutine crash from propagating
    private val exHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine error", throwable)
        viewModelScope.launch {
            _state.update { it.copy(isLoading = false) }
            _snack.emit("发生错误：${throwable.message ?: "未知"}")
        }
    }

    private var webDav: WebDavRepository? = null
    private var sessionCards: List<CardEntity> = emptyList()
    private var currentIndex: Int = 0
    private val imageCache = mutableMapOf<String, String>()

    init {
        viewModelScope.launch(exHandler) {
            val prefs = ctx.dataStore.data.first()
            val u = prefs[KEY_USERNAME]    ?: ""
            val p = prefs[KEY_PASSWORD]    ?: ""
            val f = prefs[KEY_FOLDER_PATH] ?: ""
            val d = prefs[KEY_DAILY_LIMIT] ?: 20
            _state.update { it.copy(savedUsername=u, savedPassword=p, savedFolderPath=f, dailyLimit=d) }
            if (u.isNotBlank() && p.isNotBlank()) {
                initWebDav(u, p)
                loadFolder(f)
                _screen.value = Screen.Folder
            }
            db.cardDao().getDueCount(LocalDate.now().toString()).collect { cnt ->
                _state.update { it.copy(dueCount = cnt) }
            }
        }
    }

    fun connect(username: String, password: String, folderPath: String) {
        viewModelScope.launch(exHandler) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                initWebDav(username, password)
                val ok = webDav!!.testConnection()
                if (!ok) throw Exception("账号或密码错误，请检查后重试")
                savePrefs(username, password, folderPath, _state.value.dailyLimit)
                loadFolder(folderPath)
                _screen.value = Screen.Folder
            } catch (e: Exception) {
                Log.e(TAG, "connect failed", e)
                _state.update { it.copy(error = e.message, isLoading = false) }
                _snack.emit("连接失败：${e.message}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch(exHandler) {
            ctx.dataStore.edit { it.clear() }
            webDav = null
            imageCache.clear()
            sessionCards = emptyList()
            currentIndex = 0
            _state.value = ReviewUiState()
            _screen.value = Screen.Login
        }
    }

    private fun initWebDav(username: String, password: String) {
        webDav = WebDavRepository(WebDavRepository.JIANGUOYUN_URL, username, password)
    }

    fun loadFolder(path: String, pushToStack: Boolean = false) {
        viewModelScope.launch(exHandler) {
            _state.update { it.copy(isLoading = true) }
            try {
                val items = withContext(Dispatchers.IO) { webDav!!.listFolder(path) }
                val newStack = if (pushToStack && path != _state.value.currentPath)
                    _state.value.pathStack + _state.value.currentPath
                else _state.value.pathStack
                _state.update { it.copy(folderItems=items, currentPath=path, pathStack=newStack, isLoading=false) }
            } catch (e: Exception) {
                Log.e(TAG, "loadFolder failed", e)
                _state.update { it.copy(isLoading = false) }
                _snack.emit("加载失败：${e.message}")
            }
        }
    }

    fun enterFolder(folder: WebDavItem) = loadFolder(folder.href, pushToStack = true)

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
        val dav = webDav ?: run {
            viewModelScope.launch { _snack.emit("未连接坚果云，请重新登录") }
            return
        }
        if (files.isEmpty()) {
            viewModelScope.launch { _snack.emit("此文件夹没有题目文件") }
            return
        }
        viewModelScope.launch(exHandler) {
            _state.update { it.copy(isLoading = true, selectedFiles = files) }
            try {
                val today = LocalDate.now().toString()
                val limit = _state.value.dailyLimit

                val allCards = withContext(Dispatchers.IO) {
                    val result = mutableListOf<CardEntity>()
                    for (f in files) {
                        val card = db.cardDao().getCard(f.href)
                            ?: CardEntity(path = f.href, name = f.name)
                                .also { db.cardDao().upsertCard(it) }
                        result.add(card)
                    }
                    result
                }

                val due    = allCards.filter { it.due <= today }.shuffled()
                val newC   = allCards.filter { it.reps == 0 && it.due > today }.shuffled()
                val future = allCards.filter { it.reps > 0  && it.due > today }.shuffled()

                val session = mutableListOf<CardEntity>()
                session.addAll(due)
                if (session.size < limit) session.addAll(newC.take(limit - session.size))
                if (session.size < limit) session.addAll(future.take(limit - session.size))
                if (session.isEmpty()) session.addAll(allCards.shuffled())

                // Snapshot the session - immutable from here
                sessionCards = session.take(limit).toList()
                currentIndex = 0

                if (sessionCards.isEmpty()) {
                    _state.update { it.copy(isLoading = false) }
                    _snack.emit("没有可复习的卡片")
                    return@launch
                }

                val hm    = buildHeatmap()
                val today2 = LocalDate.now().toString()
                _state.update { it.copy(
                    remaining = sessionCards.size, done = 0, progress = 0f,
                    heatmap = hm, streak = calcStreak(hm), todayDone = hm[today2] ?: 0
                )}

                // Load first card BEFORE navigating
                val ok = safeLoadCard(dav, 0)
                if (ok) _screen.value = Screen.Review
                else { _state.update { it.copy(isLoading = false) }; _snack.emit("题目加载失败") }

            } catch (e: Exception) {
                Log.e(TAG, "startReview failed", e)
                _state.update { it.copy(isLoading = false) }
                _snack.emit("启动失败：${e.message}")
            }
        }
    }

    // Safe card loader - returns true on success, false on failure, never throws
    private suspend fun safeLoadCard(dav: WebDavRepository, index: Int): Boolean {
        if (index < 0 || index >= sessionCards.size) return false
        val card = sessionCards[index]
        _state.update { it.copy(isLoading = true, card = card, parsedCard = null, imageUrls = emptyMap()) }
        return try {
            val content = withContext(Dispatchers.IO) { dav.readFile(card.path) }
            val parsed  = MarkdownParser.parse(content)
            val urlMap  = safeResolveImages(dav, card.path, parsed)
            val sm2     = SM2Card(card.interval, card.ease, card.reps, card.due, card.lapses)
            val prev    = SM2.preview(sm2)
            _state.update { it.copy(
                card=card, parsedCard=parsed, imageUrls=urlMap, intervals=prev,
                isLoading=false, remaining=sessionCards.size - index
            )}
            true
        } catch (e: Exception) {
            Log.e(TAG, "loadCard[$index] failed: ${card.path}", e)
            _state.update { it.copy(isLoading = false) }
            false
        }
    }

    fun grade(grade: Grade) {
        // Capture everything we need at call time - avoid accessing mutable state later
        val dav     = webDav    ?: run { viewModelScope.launch { _snack.emit("连接已断开") }; return }
        val idx     = currentIndex
        val cards   = sessionCards
        val card    = cards.getOrNull(idx) ?: run { viewModelScope.launch { _screen.value = Screen.Finish }; return }
        val total   = cards.size

        viewModelScope.launch(exHandler) {
            try {
                // 1. Persist grade
                val sm2    = SM2Card(card.interval, card.ease, card.reps, card.due, card.lapses)
                val result = SM2.calculate(sm2, grade)
                val updated = card.copy(
                    interval=result.interval, ease=result.ease,
                    reps=result.reps, due=result.due, lapses=result.lapses
                )
                withContext(Dispatchers.IO) {
                    db.cardDao().upsertCard(updated)
                    val today = LocalDate.now().toString()
                    val hm    = db.cardDao().getHeatmap(today)
                    db.cardDao().upsertHeatmap(HeatmapEntity(today, (hm?.count ?: 0) + 1))
                }

                // 2. Advance index
                val nextIdx = idx + 1
                currentIndex = nextIdx
                val hm    = buildHeatmap()
                val today = LocalDate.now().toString()
                _state.update { it.copy(
                    done     = nextIdx,
                    progress = nextIdx.toFloat() / total.coerceAtLeast(1),
                    remaining= (total - nextIdx).coerceAtLeast(0),
                    heatmap  = hm, todayDone = hm[today] ?: 0, streak = calcStreak(hm)
                )}

                // 3. Done?
                if (nextIdx >= total) {
                    _screen.value = Screen.Finish
                    return@launch
                }

                // 4. Load next card
                val ok = safeLoadCard(dav, nextIdx)
                if (!ok) {
                    // Try one more skip
                    val skipIdx = nextIdx + 1
                    currentIndex = skipIdx
                    if (skipIdx >= total) {
                        _screen.value = Screen.Finish
                    } else {
                        safeLoadCard(dav, skipIdx)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "grade failed", e)
                _state.update { it.copy(isLoading = false) }
                _snack.emit("评分保存失败：${e.message}")
            }
        }
    }

    private suspend fun safeResolveImages(
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
                        val ext  = name.substringAfterLast('.','j').lowercase()
                        val mime = when(ext) { "png"->"image/png";"gif"->"image/gif";"webp"->"image/webp";else->"image/jpeg" }
                        val uri  = "data:$mime;base64,$b64"
                        map[name] = uri; imageCache[name] = uri
                    }
                } catch (_: Exception) { }
            }
        }
        return map
    }

    fun addTag(tag: String) {
        val idx  = currentIndex
        val card = sessionCards.getOrNull(idx) ?: return
        viewModelScope.launch(exHandler) {
            _snack.emit("✅ 已标记：$tag")
            withContext(Dispatchers.IO) {
                try {
                    val dav = webDav ?: return@withContext
                    val raw = dav.readFile(card.path)
                    val conflict = mapOf("难" to "易", "易" to "难")[tag]
                    var content = raw
                    if (content.startsWith("---")) {
                        val end = content.indexOf("\n---", 3)
                        if (end != -1) {
                            var head = content.substring(0, end)
                            if (conflict != null) head = head.replace(Regex("  - \"#?$conflict\"?\n"), "")
                            if (!head.contains("#$tag")) {
                                head = if (head.contains("tags:"))
                                    head.replace(Regex("(tags:[ \\t]*\\r?\\n)"), "$1  - \"#$tag\"\n")
                                else head + "\ntags:\n  - \"#$tag\""
                            }
                            content = head + content.substring(end)
                        }
                    } else content = "---\ntags:\n  - \"#$tag\"\n---\n\n$content"
                    dav.writeFile(card.path, content)
                } catch (e: Exception) { Log.e(TAG, "addTag failed", e) }
            }
        }
    }

    fun setDailyLimit(limit: Int) {
        viewModelScope.launch(exHandler) {
            savePrefs(_state.value.savedUsername, _state.value.savedPassword,
                _state.value.savedFolderPath, limit)
        }
    }

    fun goToFolder()    { _screen.value = Screen.Folder }
    fun restartReview() { currentIndex = 0; sessionCards = emptyList(); startReview(_state.value.selectedFiles) }

    private suspend fun buildHeatmap(): Map<String, Int> {
        val from = LocalDate.now().minusDays(29).toString()
        return withContext(Dispatchers.IO) { db.cardDao().getHeatmapRange(from).associate { it.date to it.count } }
    }

    private fun calcStreak(hm: Map<String, Int>): Int {
        var s = 0
        for (i in 0..364) {
            if ((hm[LocalDate.now().minusDays(i.toLong()).toString()] ?: 0) > 0) s++ else if (i > 0) break
        }
        return s
    }

    private fun savePrefs(u: String, p: String, f: String, d: Int) {
        viewModelScope.launch(exHandler) {
            ctx.dataStore.edit { prefs ->
                prefs[KEY_USERNAME]=u; prefs[KEY_PASSWORD]=p; prefs[KEY_FOLDER_PATH]=f; prefs[KEY_DAILY_LIMIT]=d
            }
            _state.update { it.copy(savedUsername=u, savedPassword=p, savedFolderPath=f, dailyLimit=d) }
        }
    }
}
