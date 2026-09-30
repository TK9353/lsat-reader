package com.taehyeon.lsatreader.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taehyeon.lsatreader.ai.AiFeatures
import com.taehyeon.lsatreader.ai.ClaudeClient
import com.taehyeon.lsatreader.data.Article
import com.taehyeon.lsatreader.data.DictResult
import com.taehyeon.lsatreader.data.Dictionary
import com.taehyeon.lsatreader.data.GuardianSource
import com.taehyeon.lsatreader.data.Kind
import com.taehyeon.lsatreader.data.RcQuestion
import com.taehyeon.lsatreader.data.RoleResult
import com.taehyeon.lsatreader.data.RssSource
import com.taehyeon.lsatreader.data.SearchResult
import com.taehyeon.lsatreader.data.Store
import com.taehyeon.lsatreader.data.Topic
import com.taehyeon.lsatreader.data.VocabEntry
import com.taehyeon.lsatreader.data.WikipediaSource
import com.taehyeon.lsatreader.feed.Recommender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

data class WordState(
    val word: String,
    val sentence: String,
    val source: String,
    val dict: DictResult? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val aiMeaning: String? = null,
    val aiLoading: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val claude = ClaudeClient({ store.claudeKey }, { store.claudeModel })
    val ai = AiFeatures(claude)
    private val wiki = WikipediaSource()
    private val guardian = GuardianSource { store.guardianKey }
    private val rss = RssSource()
    val rec = Recommender(store)

    private val seen: MutableList<String> = store.loadSeen()
    private val seenSet: MutableSet<String> = HashSet(seen)

    // ---------------- 피드 ----------------
    val feed = mutableStateListOf<Article>()
    var currentIndex by mutableIntStateOf(0)
        private set
    var feedStatus by mutableStateOf<String?>(null)
    var aiStatus by mutableStateOf<String?>(null)
    var inflight by mutableIntStateOf(0)
        private set
    private var failures = 0
    private var lastError: String? = null
    private var aiJob: Job? = null
    private var nextAiAt = 2

    // ---------------- 분석 ----------------
    val questions = mutableStateMapOf<String, List<RcQuestion>>().apply { putAll(store.loadQuestions()) }
    val answers = mutableStateMapOf<String, Int>()
    val roles = mutableStateMapOf<String, RoleResult>().apply { putAll(store.loadRoles()) }
    val highlightOn = mutableStateMapOf<String, Boolean>()
    val busy = mutableStateMapOf<String, Boolean>()
    val errors = mutableStateMapOf<String, String>()

    // ---------------- 보관함 ----------------
    val vocab = mutableStateListOf<VocabEntry>().apply { addAll(store.loadVocab()) }
    val liked = mutableStateListOf<Article>().apply { addAll(store.loadLiked()) }

    // ---------------- 읽기 화면 / 팝업 ----------------
    var reader by mutableStateOf<Article?>(null)
    private var readerOpenedAt = 0L
    var overlayLoading by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var wordPopup by mutableStateOf<WordState?>(null)

    // ---------------- 검색 ----------------
    var searchText by mutableStateOf("")
    val results = mutableStateListOf<SearchResult>()
    var searching by mutableStateOf(false)
    var searchInfo by mutableStateOf<String?>(null)

    // ---------------- 설정 ----------------
    val models = mutableStateListOf<String>()
    var weightsVersion by mutableIntStateOf(0)

    init {
        ensureAhead(0)
    }

    // =====================================================================
    // 피드
    // =====================================================================
    private fun ensureAhead(index: Int) {
        val need = index + AHEAD - feed.size - inflight
        if (failures >= 3) return
        repeat(need.coerceIn(0, 3)) { launchFetch() }
        maybeGenerateAi()
    }

    private fun launchFetch() {
        inflight++
        viewModelScope.launch {
            try {
                val a = nextArticle()
                if (a != null && feed.none { it.id == a.id }) {
                    feed.add(a)
                    failures = 0
                    feedStatus = null
                } else if (a == null) {
                    failures++
                    if (feed.size <= currentIndex + 1) {
                        feedStatus = "글을 불러오지 못했습니다. 네트워크 상태를 확인하세요." +
                            (lastError?.let { "\n($it)" } ?: "")
                    }
                }
            } finally {
                inflight--
                if (failures < 3 && feed.size < currentIndex + AHEAD && inflight == 0) ensureAhead(currentIndex)
            }
        }
    }

    private suspend fun nextArticle(): Article? {
        val kinds = store.enabledKinds.filter { it != Kind.AI }.ifEmpty { listOf(Kind.WIKI) }
        repeat(4) {
            val topic = rec.pickTopic(store.enabledTopics)
            val kind = rec.pickKind(kinds)
            val exclude: Set<String> = seenSet + feed.map { it.id }
            val feeds = store.feeds
            val a = runCatching {
                withContext(Dispatchers.IO) {
                    when (kind) {
                        Kind.WIKI -> wiki.randomForTopic(topic, exclude)
                        Kind.GUARDIAN -> guardian.randomForTopic(topic, exclude)
                        Kind.RSS -> rss.randomForTopic(topic, feeds, exclude)
                        Kind.AI -> null
                    }
                }
            }.onFailure { lastError = "${kind.label}: ${it.message?.take(120)}" }.getOrNull()
            if (a != null && a.id !in exclude) return a
        }
        return null
    }

    private fun aiGap(): Int {
        val r = store.aiRatio
        if (r <= 0.01f) return Int.MAX_VALUE
        return (1f / r).roundToInt().coerceAtLeast(2)
    }

    private fun maybeGenerateAi() {
        if (!ai.available || Kind.AI !in store.enabledKinds || store.aiRatio <= 0.01f) return
        if (aiJob?.isActive == true || currentIndex < nextAiAt) return
        aiJob = viewModelScope.launch {
            aiStatus = "AI 지문 생성·검증 중…"
            try {
                val topic = rec.pickTopic(store.enabledTopics)
                val src = groundingSource(topic) ?: error("근거 자료를 찾지 못함")
                val r = withContext(Dispatchers.IO) { ai.generateVerifiedPassage(src) }
                val art = r.article
                if (art != null) {
                    val pos = (currentIndex + 2).coerceAtMost(feed.size)
                    feed.add(pos, art)
                    aiStatus = null
                } else {
                    flashAiStatus("AI 지문 폐기: ${r.reason}")
                }
            } catch (e: Exception) {
                flashAiStatus("AI 오류: ${e.message?.take(150)}")
            } finally {
                nextAiAt = currentIndex + aiGap()
            }
        }
    }

    private fun flashAiStatus(s: String) {
        aiStatus = s
        viewModelScope.launch {
            delay(6000)
            if (aiStatus == s) aiStatus = null
        }
    }

    private suspend fun groundingSource(topic: Topic): Article? = withContext(Dispatchers.IO) {
        val exclude = HashSet(seenSet)
        val first: Article? = if (Random.nextDouble() < 0.7) {
            runCatching { wiki.randomForTopic(topic, exclude) }.getOrNull()
        } else {
            runCatching { guardian.randomForTopic(topic, exclude) }.getOrNull()
                ?: runCatching { rss.randomForTopic(topic, store.feeds, exclude) }.getOrNull()
        }
        first?.takeIf { it.wordCount >= 500 } ?: runCatching { wiki.randomForTopic(topic, exclude) }.getOrNull()
    }

    fun onPageShown(index: Int) {
        currentIndex = index
        val a = feed.getOrNull(index) ?: return
        markSeen(a.id)
        ensureAhead(index)
    }

    fun onPageLeft(a: Article, dwellMs: Long) {
        rec.onDwell(a, dwellMs)
        weightsVersion++
    }

    fun retryFeed() {
        failures = 0
        feedStatus = null
        ensureAhead(currentIndex)
    }

    fun refreshFeed() {
        feed.clear()
        currentIndex = 0
        failures = 0
        feedStatus = null
        nextAiAt = 2
        ensureAhead(0)
    }

    private fun markSeen(id: String) {
        if (seenSet.add(id)) {
            seen.add(id)
            val snapshot = seen.toList()
            viewModelScope.launch(Dispatchers.IO) { store.saveSeen(snapshot) }
        }
    }

    // =====================================================================
    // 좋아요 / 단어장
    // =====================================================================
    fun isLiked(id: String) = liked.any { it.id == id }

    fun toggleLike(a: Article) {
        val on = !isLiked(a.id)
        if (on) liked.add(0, a) else liked.removeAll { it.id == a.id }
        rec.onLike(a, on)
        weightsVersion++
        val snap = liked.toList()
        viewModelScope.launch(Dispatchers.IO) { store.saveLiked(snap) }
    }

    fun onWordTap(word: String, sentence: String, source: String) {
        val st = WordState(word, sentence, source)
        wordPopup = st
        viewModelScope.launch {
            try {
                val d = withContext(Dispatchers.IO) { lookupWithStemming(word) }
                if (wordPopup?.word == word) {
                    wordPopup = wordPopup?.copy(dict = d, loading = false, error = if (d == null) "사전에서 찾지 못했습니다." else null)
                }
            } catch (e: Exception) {
                if (wordPopup?.word == word) wordPopup = wordPopup?.copy(loading = false, error = "사전 오류: ${e.message?.take(100)}")
            }
        }
    }

    private suspend fun lookupWithStemming(word: String): DictResult? {
        val w = word.lowercase().replace('’', '\'').removeSuffix("'s")
        val candidates = linkedSetOf(w)
        if (w.endsWith("ies")) candidates.add(w.dropLast(3) + "y")
        if (w.endsWith("es")) candidates.add(w.dropLast(2))
        if (w.endsWith("s")) candidates.add(w.dropLast(1))
        if (w.endsWith("ied")) candidates.add(w.dropLast(3) + "y")
        if (w.endsWith("ed")) { candidates.add(w.dropLast(2)); candidates.add(w.dropLast(1)) }
        if (w.endsWith("ing")) { candidates.add(w.dropLast(3)); candidates.add(w.dropLast(3) + "e") }
        if (w.endsWith("ly")) candidates.add(w.dropLast(2))
        for (c in candidates) {
            if (c.length < 2) continue
            val r = Dictionary.lookup(c)
            if (r != null) return r
        }
        return null
    }

    fun askAiMeaning() {
        val st = wordPopup ?: return
        if (!ai.available) { message = "설정에서 Claude API 키를 입력하세요."; return }
        wordPopup = st.copy(aiLoading = true)
        viewModelScope.launch {
            val text = try {
                withContext(Dispatchers.IO) { ai.meaningInContext(st.word, st.sentence) }
            } catch (e: Exception) {
                "오류: ${e.message?.take(120)}"
            }
            if (wordPopup?.word == st.word) wordPopup = wordPopup?.copy(aiMeaning = text, aiLoading = false)
        }
    }

    fun saveWord() {
        val st = wordPopup ?: return
        val def = buildString {
            st.dict?.senses?.take(3)?.forEach { (pos, d) -> append("[$pos] $d\n") }
            st.aiMeaning?.let { append(it) }
        }.trim().ifEmpty { "(뜻 없음)" }
        vocab.removeAll { it.word.equals(st.word, ignoreCase = true) }
        vocab.add(0, VocabEntry(st.word, def, st.sentence, st.source, System.currentTimeMillis()))
        persistVocab()
        message = "단어장에 저장: ${st.word}"
        wordPopup = null
    }

    fun deleteWord(v: VocabEntry) {
        vocab.remove(v)
        persistVocab()
    }

    private fun persistVocab() {
        val snap = vocab.toList()
        viewModelScope.launch(Dispatchers.IO) { store.saveVocab(snap) }
    }

    // =====================================================================
    // RC 문제 / 논지 구조
    // =====================================================================
    fun loadQuestions(a: Article, force: Boolean = false) {
        val k = "q:" + a.id
        if (busy[k] == true) return
        if (!force && questions.containsKey(a.id)) return
        if (!ai.available) { errors[k] = "설정에서 Claude API 키를 입력하세요."; return }
        busy[k] = true
        errors.remove(k)
        viewModelScope.launch {
            try {
                val q = withContext(Dispatchers.IO) { ai.questions(a) }
                if (q.isEmpty()) errors[k] = "문제 생성에 실패했습니다. 다시 시도하세요."
                else {
                    questions[a.id] = q
                    answers.keys.filter { it.startsWith(a.id + "#") }.forEach { answers.remove(it) }
                    val snap = questions.toMap()
                    withContext(Dispatchers.IO) { store.saveQuestions(snap) }
                }
            } catch (e: Exception) {
                errors[k] = "오류: ${e.message?.take(200)}"
            } finally {
                busy[k] = false
            }
        }
    }

    fun toggleHighlight(a: Article) {
        if (roles.containsKey(a.id)) {
            highlightOn[a.id] = highlightOn[a.id] != true
            return
        }
        val k = "r:" + a.id
        if (busy[k] == true) return
        if (!ai.available) { message = "설정에서 Claude API 키를 입력하세요."; return }
        busy[k] = true
        errors.remove(k)
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { ai.roles(a) }
                roles[a.id] = r
                highlightOn[a.id] = true
                val snap = roles.toMap()
                withContext(Dispatchers.IO) { store.saveRoles(snap) }
            } catch (e: Exception) {
                errors[k] = "논지 분석 오류: ${e.message?.take(200)}"
            } finally {
                busy[k] = false
            }
        }
    }

    // =====================================================================
    // 읽기 화면
    // =====================================================================
    fun openReader(a: Article) {
        reader = a
        readerOpenedAt = System.currentTimeMillis()
        markSeen(a.id)
    }

    fun closeReader() {
        reader = null
    }

    /** 임의의 글을 근거로 AI 지문 생성 */
    fun generateFrom(a: Article) {
        if (!ai.available) { message = "설정에서 Claude API 키를 입력하세요."; return }
        if (overlayLoading != null) return
        overlayLoading = "AI 지문 생성 → 문장별 사실 검증 중 (30~90초)"
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { ai.generateVerifiedPassage(a) }
                val art = r.article
                if (art != null) openReader(art) else message = "AI 지문 폐기: ${r.reason}"
            } catch (e: Exception) {
                message = "AI 오류: ${e.message?.take(150)}"
            } finally {
                overlayLoading = null
            }
        }
    }

    // =====================================================================
    // 검색
    // =====================================================================
    fun search() {
        val text = searchText.trim()
        if (text.isEmpty() || searching) return
        searching = true
        results.clear()
        searchInfo = null
        viewModelScope.launch {
            try {
                if (text.startsWith("http://") || text.startsWith("https://")) {
                    val a = withContext(Dispatchers.IO) { runCatching { rss.fromUrl(text) }.getOrNull() }
                    if (a != null) openReader(a) else searchInfo = "해당 URL에서 본문을 추출하지 못했습니다(유료벽·차단 가능)."
                    return@launch
                }
                val hasKorean = text.any { it in '가'..'힣' }
                val wordy = text.split(Regex("\\s+")).size >= 4
                val queries = if (ai.available && (hasKorean || wordy)) {
                    runCatching { withContext(Dispatchers.IO) { ai.expandQuery(text) } }.getOrNull()?.ifEmpty { null } ?: listOf(text)
                } else listOf(text)
                searchInfo = "검색어: " + queries.joinToString(" / ")
                val topic = Topic.CULTURE
                val found = coroutineScope {
                    val g = async(Dispatchers.IO) {
                        queries.take(2).flatMap { q -> runCatching { guardian.search(q, null, topic).take(6) }.getOrDefault(emptyList()) }
                    }
                    val w = async(Dispatchers.IO) {
                        queries.take(2).flatMap { q -> runCatching { wiki.search(q, 6) }.getOrDefault(emptyList()) }
                    }
                    val r = async(Dispatchers.IO) { runCatching { rss.searchCached(queries) }.getOrDefault(emptyList()) }
                    Triple(g.await(), w.await(), r.await())
                }
                val gs = found.first.distinctBy { it.id }.map {
                    SearchResult(it.title, it.paragraphs.firstOrNull()?.take(220) ?: "", it.source, Kind.GUARDIAN, article = it)
                }
                val ws = found.second.distinctBy { it.title }.map { h ->
                    SearchResult(h.title, h.snippet, "Wikipedia", Kind.WIKI, loader = { wiki.article(h.title, topic, 3000) })
                }
                val rs = found.third.take(8).map { item ->
                    SearchResult(item.title, item.summary.take(220), item.feed.name, Kind.RSS, loader = { rss.toArticle(item, allowSummaryOnly = true) })
                }
                // 섞어서 보여주기
                val merged = ArrayList<SearchResult>()
                val lists = listOf(ws, gs, rs)
                var i = 0
                while (lists.any { i < it.size }) {
                    lists.forEach { l -> if (i < l.size) merged.add(l[i]) }
                    i++
                }
                results.addAll(merged)
                if (merged.isEmpty()) searchInfo = (searchInfo ?: "") + "\n결과 없음"
            } catch (e: Exception) {
                searchInfo = "검색 오류: ${e.message?.take(150)}"
            } finally {
                searching = false
            }
        }
    }

    fun openResult(r: SearchResult) {
        r.article?.let { openReader(it); return }
        val loader = r.loader ?: return
        if (overlayLoading != null) return
        overlayLoading = "본문 불러오는 중…"
        viewModelScope.launch {
            try {
                val a = withContext(Dispatchers.IO) { loader() }
                if (a != null) openReader(a) else message = "본문을 가져오지 못했습니다."
            } catch (e: Exception) {
                message = "오류: ${e.message?.take(150)}"
            } finally {
                overlayLoading = null
            }
        }
    }

    // =====================================================================
    // 설정
    // =====================================================================
    fun fetchModels() {
        if (!ai.available) { message = "먼저 API 키를 저장하세요."; return }
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { claude.listModels() }
                models.clear(); models.addAll(list)
                if (list.isEmpty()) message = "모델 목록이 비어 있습니다."
            } catch (e: Exception) {
                message = "모델 목록 오류: ${e.message?.take(150)}"
            }
        }
    }

    fun resetAlgorithm() {
        rec.reset()
        weightsVersion++
        message = "알고리즘 학습값을 초기화했습니다."
    }

    fun clearCaches() {
        store.clearCaches()
        seen.clear(); seenSet.clear()
        questions.clear(); roles.clear(); answers.clear(); highlightOn.clear()
        message = "캐시를 비웠습니다."
    }

    companion object {
        const val AHEAD = 4
    }
}
