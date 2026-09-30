@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.taehyeon.lsatreader.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taehyeon.lsatreader.data.Article
import com.taehyeon.lsatreader.data.DefaultFeeds
import com.taehyeon.lsatreader.data.Kind
import com.taehyeon.lsatreader.data.Topic
import kotlin.math.roundToInt

// =====================================================================
// 피드 (릴스식 세로 넘김)
// =====================================================================
@Composable
fun FeedScreen(vm: AppViewModel, pagerState: PagerState) {
    if (vm.feed.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val status = vm.feedStatus
            if (status != null) {
                Text(status, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.retryFeed() }) { Text("다시 시도") }
            } else {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("글을 불러오는 중…")
            }
        }
        return
    }

    // 체류 시간 측정 → 알고리즘 학습
    LaunchedEffect(pagerState) {
        var last: Article? = null
        var start = SystemClock.elapsedRealtime()
        snapshotFlow { pagerState.settledPage }.collect { idx ->
            val now = SystemClock.elapsedRealtime()
            last?.let { vm.onPageLeft(it, now - start) }
            last = vm.feed.getOrNull(idx)
            start = now
            vm.onPageShown(idx)
        }
    }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { i -> vm.feed.getOrNull(i)?.id ?: "p$i" },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val a = vm.feed.getOrNull(page)
            if (a != null) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 24.dp)
                ) {
                    ArticleView(vm, a)
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "▲ 위로 밀면 다음 글",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(60.dp))
                }
            }
        }
        vm.aiStatus?.let { s ->
            Text(
                s,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp, start = 16.dp, end = 16.dp)
                    .background(Color(0xCC4A2A6A), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (vm.inflight > 0 && pagerState.currentPage >= vm.feed.size - 1) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
        }
    }
}

// =====================================================================
// 검색
// =====================================================================
@Composable
fun SearchScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = vm.searchText,
            onValueChange = { vm.searchText = it },
            label = { Text("키워드 · 문장 · URL") },
            placeholder = { Text("예: 연방대법원이 선례를 뒤집는 기준") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            trailingIcon = {
                IconButton(onClick = { vm.search() }) { Icon(Icons.Filled.Search, contentDescription = "검색") }
            },
        )
        Text(
            "한국어 문장은 AI가 영어 검색어로 바꿔 검색합니다(API 키 필요). 기사 URL을 붙여 넣으면 본문을 바로 엽니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.searchInfo?.let { Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp)) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.results) { r ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.openResult(r) }
                        .padding(vertical = 10.dp)
                ) {
                    Text("${r.kind.label} · ${r.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(r.title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif))
                    if (r.snippet.isNotBlank()) {
                        Text(r.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

// =====================================================================
// 보관함 (단어장 / 좋아요한 글)
// =====================================================================
@Composable
fun LibraryScreen(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("단어장 ${vm.vocab.size}") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("좋아요한 글 ${vm.liked.size}") })
        }
        if (tab == 0) {
            if (vm.vocab.isEmpty()) Text("본문에서 단어를 탭하면 뜻을 보고 저장할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.vocab, key = { it.word + it.addedAt }) { v ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(v.word, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(v.definition, style = MaterialTheme.typography.bodySmall)
                            if (v.context.isNotBlank()) {
                                Text(
                                    "“${v.context}”",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        IconButton(onClick = { vm.deleteWord(v) }) { Icon(Icons.Filled.Delete, contentDescription = "삭제") }
                    }
                    HorizontalDivider()
                }
            }
        } else {
            if (vm.liked.isEmpty()) Text("피드에서 ♥를 누른 글이 여기에 저장됩니다.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.liked, key = { it.id }) { a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { vm.openReader(a) }) {
                            Text("${a.topic.label} · ${a.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(a.title, style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif))
                        }
                        IconButton(onClick = { vm.toggleLike(a) }) { Icon(Icons.Filled.Delete, contentDescription = "삭제") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// =====================================================================
// 읽기 화면 (검색 결과·보관함·AI 재구성)
// =====================================================================
@Composable
fun ReaderScreen(vm: AppViewModel, a: Article) {
    BackHandler { vm.closeReader() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeReader() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
                Text("읽기", style = MaterialTheme.typography.titleMedium)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 60.dp)
            ) {
                ArticleView(vm, a)
            }
        }
    }
}

// =====================================================================
// 단어 팝업
// =====================================================================
@Composable
fun WordSheet(vm: AppViewModel, st: WordState) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { vm.wordPopup = null }, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(st.dict?.word ?: st.word, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                st.dict?.phonetic?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                st.loading -> CircularProgressIndicator()
                st.error != null -> Text(st.error, color = MaterialTheme.colorScheme.error)
                else -> st.dict?.senses?.forEach { (pos, d) ->
                    Text("[$pos] $d", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            if (st.sentence.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "“${st.sentence}”",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                st.aiLoading -> CircularProgressIndicator()
                st.aiMeaning != null -> Text(
                    st.aiMeaning,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                )
                else -> OutlinedButton(onClick = { vm.askAiMeaning() }) { Text("문맥 속 뜻 (AI·한국어)") }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.saveWord() }, modifier = Modifier.fillMaxWidth()) { Text("단어장에 저장") }
        }
    }
}

// =====================================================================
// 설정
// =====================================================================
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.store
    var claudeKey by remember { mutableStateOf(s.claudeKey) }
    var model by remember { mutableStateOf(s.claudeModel) }
    var guardianKey by remember { mutableStateOf(s.guardianKey) }
    var ratio by remember { mutableFloatStateOf(s.aiRatio) }
    var topics by remember { mutableStateOf(s.enabledTopics) }
    var kinds by remember { mutableStateOf(s.enabledKinds) }
    var feeds by remember { mutableStateOf(s.feedsText) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Section("API 키")
        OutlinedTextField(
            value = claudeKey, onValueChange = { claudeKey = it },
            label = { Text("Claude API 키 (sk-ant-…)") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            label = { Text("Claude 모델 ID") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 4.dp)) {
            TextButton(onClick = { s.claudeKey = claudeKey; vm.fetchModels() }) { Text("사용 가능한 모델 불러오기") }
        }
        if (vm.models.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.models.forEach { m -> FilterChip(selected = m == model, onClick = { model = m }, label = { Text(m) }) }
            }
        }
        OutlinedTextField(
            value = guardianKey, onValueChange = { guardianKey = it },
            label = { Text("Guardian API 키 (선택, 없으면 Guardian 기사 제외)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Button(
            onClick = {
                s.claudeKey = claudeKey; s.claudeModel = model; s.guardianKey = guardianKey
                vm.message = "저장했습니다."
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("키 저장") }

        Section("피드 구성")
        val gap = if (ratio <= 0.01f) "끔" else "약 ${(1f / ratio).roundToInt()}개 중 1개"
        Text("AI 지문 비율: $gap", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = ratio, onValueChange = { ratio = it },
            onValueChangeFinished = { s.aiRatio = ratio },
            valueRange = 0f..0.5f, steps = 9,
        )
        Text("주제", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Topic.entries.forEach { t ->
                FilterChip(
                    selected = t in topics,
                    onClick = {
                        val n = if (t in topics) topics - t else topics + t
                        if (n.isNotEmpty()) { topics = n; s.enabledTopics = n }
                    },
                    label = { Text(t.label) },
                )
            }
        }
        Text("공급원", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Kind.entries.forEach { k ->
                FilterChip(
                    selected = k in kinds,
                    onClick = {
                        val n = if (k in kinds) kinds - k else kinds + k
                        if (n.any { it != Kind.AI }) { kinds = n; s.enabledKinds = n }
                    },
                    label = { Text(k.label) },
                )
            }
        }
        OutlinedButton(onClick = { vm.refreshFeed(); vm.message = "피드를 새로 구성합니다." }, modifier = Modifier.padding(top = 6.dp)) {
            Text("피드 새로고침")
        }

        Section("알고리즘 학습 현황")
        Text(
            "체류 시간(예상 읽기시간 대비)과 ♥로 가중치를 조정하고, 15%는 무작위로 탐색합니다.",
            style = MaterialTheme.typography.bodySmall,
        )
        @Suppress("UNUSED_VARIABLE") val v = vm.weightsVersion
        Topic.entries.forEach { t ->
            val w = vm.rec.weight(t)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(t.label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { (w / 5.0).toFloat() }, modifier = Modifier.weight(1f))
                Text(" %.2f".format(w), style = MaterialTheme.typography.bodySmall)
            }
        }
        Kind.entries.forEach { k ->
            val w = vm.rec.weight(k)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(k.label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                LinearProgressIndicator(progress = { (w / 5.0).toFloat() }, modifier = Modifier.weight(1f))
                Text(" %.2f".format(w), style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = { vm.resetAlgorithm() }) { Text("학습값 초기화") }

        Section("RSS 목록 (주제 | 이름 | URL)")
        OutlinedTextField(
            value = feeds, onValueChange = { feeds = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Text(
            "주제: " + Topic.entries.joinToString(", ") { it.name },
            style = MaterialTheme.typography.labelSmall,
        )
        Row {
            TextButton(onClick = { s.feedsText = feeds; vm.message = "RSS 목록 저장" }) { Text("저장") }
            TextButton(onClick = { feeds = DefaultFeeds.TEXT; s.feedsText = feeds }) { Text("기본값 복원") }
        }

        Section("기타")
        TextButton(onClick = { vm.clearCaches() }) { Text("읽은 기록·문제·분석 캐시 비우기") }
        Text(
            "모든 데이터와 키는 이 기기 안에만 저장됩니다. 기사는 이 기기에서 원문 페이지를 직접 받아 본문만 보여 주며, " +
                "유료 구독 기사는 우회하지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(18.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
}
