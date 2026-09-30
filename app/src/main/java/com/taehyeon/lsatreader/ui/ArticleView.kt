@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.taehyeon.lsatreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taehyeon.lsatreader.data.AiMeta
import com.taehyeon.lsatreader.data.Article
import com.taehyeon.lsatreader.data.RcQuestion
import com.taehyeon.lsatreader.data.RoleResult
import com.taehyeon.lsatreader.data.TextUtil
import kotlin.math.roundToInt

@Composable
fun ArticleView(vm: AppViewModel, a: Article, allowGenerate: Boolean = true) {
    val uri = LocalUriHandler.current
    var showQuestions by remember(a.id) { mutableStateOf(false) }
    var showAiDetail by remember(a.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        // 메타 정보
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(a.topic.label, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Tag(a.kind.label, if (a.isAi) Color(0xFF8E44AD) else MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.weight(1f))
            val min = (a.wordCount / 230.0).roundToInt().coerceAtLeast(1)
            Text("${a.wordCount} words · 약 ${min}분", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            a.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(a.author, a.source, a.published).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        a.aiMeta?.let { meta ->
            Spacer(Modifier.height(8.dp))
            AiBadge(meta, showAiDetail) { showAiDetail = !showAiDetail }
        }

        // 논지 구조
        val hl = vm.highlightOn[a.id] == true
        val roleRes = vm.roles[a.id]
        if (hl && roleRes != null) {
            Spacer(Modifier.height(10.dp))
            StructureCard(roleRes)
        }
        vm.errors["r:" + a.id]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Spacer(Modifier.height(14.dp))
        ArticleBody(a, if (hl) roleRes else null) { w, s -> vm.onWordTap(w, s, a.title) }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            val liked = vm.isLiked(a.id)
            IconButton(onClick = { vm.toggleLike(a) }) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "좋아요",
                    tint = if (liked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { vm.loadQuestions(a); showQuestions = true }) { Text("RC 문제") }
            OutlinedButton(onClick = { vm.toggleHighlight(a) }) {
                if (vm.busy["r:" + a.id] == true) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (hl) "하이라이트 끄기" else "논지 구조")
            }
            a.url?.takeIf { it.isNotBlank() }?.let { u ->
                TextButton(onClick = { runCatching { uri.openUri(u) } }) { Text("원문") }
            }
            if (allowGenerate && !a.isAi) {
                TextButton(onClick = { vm.generateFrom(a) }) { Text("AI 지문으로 재구성") }
            }
        }
    }

    if (showQuestions) {
        QuestionsSheet(vm, a) { showQuestions = false }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun AiBadge(meta: AiMeta, expanded: Boolean, onToggle: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x228E44AD), RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(10.dp)
    ) {
        Text(
            "AI 재구성 지문 · 문장별 검증: 사실 문장 ${meta.factualSentences - meta.removedSentences.coerceAtMost(meta.factualSentences)}/${meta.factualSentences} 통과, " +
                "${meta.removedSentences}문장 삭제" + if (expanded) "" else "  (자세히)",
            style = MaterialTheme.typography.labelMedium,
        )
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                "방식: ① 근거 문서 안의 정보만으로 생성하고 문장마다 원문 인용 첨부 → ② 인용문이 원문에 실제로 있는지 앱에서 대조 → " +
                    "③ 별도 AI 호출이 근거 문서 기준으로 문장별 판정 → 미지지 문장 삭제(20% 초과 시 지문 폐기).\n" +
                    "한계: 근거 문서 자체의 오류는 걸러내지 못함.",
                style = MaterialTheme.typography.bodySmall,
            )
            meta.sources.forEach { s ->
                Text(
                    "근거: ${s.title}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { if (s.url.isNotBlank()) runCatching { uri.openUri(s.url) } },
                )
            }
            if (meta.removedNotes.isNotEmpty()) {
                Text("삭제된 문장:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                meta.removedNotes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun StructureCard(r: RoleResult) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        if (r.structureKo.isNotBlank()) Text(r.structureKo, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoleColors.labels.forEach { (k, label) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(RoleColors.map.getValue(k), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun ArticleBody(a: Article, roles: RoleResult?, onWord: (String, String) -> Unit) {
    val paras = remember(a.id, a.body) { TextUtil.sentencesByParagraph(a.body) }
    val starts = remember(paras) { paras.runningFold(0) { acc, p -> acc + p.size } }
    paras.forEachIndexed { i, sents ->
        TappableParagraph(sents, starts[i], roles, onWord)
        Spacer(Modifier.height(14.dp))
    }
}

private class ParaText(val text: AnnotatedString, val ranges: List<Pair<IntRange, String>>)

@Composable
private fun TappableParagraph(
    sents: List<String>,
    startIdx: Int,
    roles: RoleResult?,
    onWord: (String, String) -> Unit,
) {
    val pt = remember(sents, roles) {
        val ranges = ArrayList<Pair<IntRange, String>>()
        val text = buildAnnotatedString {
            sents.forEachIndexed { i, s ->
                if (i > 0) append(" ")
                val st = length
                val role = roles?.roles?.get(startIdx + i)
                val bg = role?.let { RoleColors.map[it] }
                if (bg != null) {
                    withStyle(SpanStyle(background = bg, fontWeight = if (role == "MAIN") FontWeight.SemiBold else null)) { append(s) }
                } else append(s)
                ranges.add(st until length to s)
            }
        }
        ParaText(text, ranges)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        pt.text,
        style = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            lineHeight = 29.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(pt) {
            detectTapGestures { pos ->
                val l = layout ?: return@detectTapGestures
                val off = l.getOffsetForPosition(pos)
                val w = wordAt(pt.text.text, off) ?: return@detectTapGestures
                val s = pt.ranges.firstOrNull { off in it.first }?.second ?: ""
                onWord(w, s)
            }
        },
    )
}

private fun isWordChar(c: Char) = c.isLetter() || c == '-' || c == '\'' || c == '’'

fun wordAt(t: String, off: Int): String? {
    if (t.isEmpty()) return null
    var i = off.coerceIn(0, t.length - 1)
    if (!t[i].isLetter()) {
        if (i > 0 && t[i - 1].isLetter()) i-- else return null
    }
    var s = i
    while (s > 0 && isWordChar(t[s - 1])) s--
    var e = i
    while (e < t.length && isWordChar(t[e])) e++
    return t.substring(s, e).trim('-', '\'', '’').takeIf { it.length > 1 }
}

// ---------------------------------------------------------------------
// RC 문제 시트
// ---------------------------------------------------------------------
@Composable
fun QuestionsSheet(vm: AppViewModel, a: Article, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        val k = "q:" + a.id
        val qs = vm.questions[a.id]
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            when {
                vm.busy[k] == true -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("LSAT RC 문제 생성 중… (20~40초)")
                }
                vm.errors[k] != null -> Column(Modifier.padding(16.dp)) {
                    Text(vm.errors[k] ?: "", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.loadQuestions(a, force = true) }) { Text("다시 시도") }
                }
                qs != null -> LazyColumn(Modifier.fillMaxWidth()) {
                    item {
                        Text("RC 문제 · ${a.title}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(qs.size) { i -> QuestionCard(vm, a.id, i, qs[i]) }
                    item {
                        TextButton(onClick = { vm.loadQuestions(a, force = true) }) { Text("새 문제 만들기") }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(vm: AppViewModel, articleId: String, i: Int, q: RcQuestion) {
    val key = "$articleId#$i"
    val picked = vm.answers[key]
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("${i + 1}. [${q.type}]", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(q.stem, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        q.choices.forEachIndexed { ci, c ->
            val letter = ('A' + ci)
            val bg = when {
                picked == null -> Color.Transparent
                ci == q.answer -> Color(0x5543A047)
                ci == picked -> Color(0x55E53935)
                else -> Color.Transparent
            }
            Text(
                "($letter) $c",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(6.dp))
                    .clickable(enabled = picked == null) { vm.answers[key] = ci }
                    .padding(8.dp),
            )
        }
        if (picked != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                (if (picked == q.answer) "정답입니다. " else "오답. 정답은 (${'A' + q.answer}). ") + q.explanation,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(8.dp),
            )
        }
        HorizontalDivider(Modifier.padding(top = 10.dp))
    }
}
