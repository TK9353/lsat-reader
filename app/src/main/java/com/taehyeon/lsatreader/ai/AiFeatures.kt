package com.taehyeon.lsatreader.ai

import com.taehyeon.lsatreader.data.AiMeta
import com.taehyeon.lsatreader.data.Article
import com.taehyeon.lsatreader.data.Kind
import com.taehyeon.lsatreader.data.RcQuestion
import com.taehyeon.lsatreader.data.RoleResult
import com.taehyeon.lsatreader.data.SourceRef
import com.taehyeon.lsatreader.data.TextUtil
import com.taehyeon.lsatreader.data.arr
import com.taehyeon.lsatreader.data.int
import com.taehyeon.lsatreader.data.obj
import com.taehyeon.lsatreader.data.str

class AiFeatures(private val claude: ClaudeClient) {

    val available get() = claude.hasKey

    // ------------------------------------------------------------------
    // 1) 근거 기반 LSAT 지문 생성 + 2단계 사실 검증
    // ------------------------------------------------------------------
    data class GenResult(val article: Article?, val reason: String)

    private data class Sent(val text: String, val quote: String, val para: Int)

    suspend fun generateVerifiedPassage(source: Article): GenResult {
        val src = source.body.take(30000)
        val normSrc = TextUtil.normalize(src)
        val srcGrams = TextUtil.ngrams(normSrc, 4)

        // --- 1단계: 생성 (문장마다 원문 인용 첨부) ---
        val genSystem = """
You write passages in the style of the LSAT Reading Comprehension section.
You are strictly grounded: you may use ONLY information contained in the SOURCE TEXT the user provides.
Never add outside knowledge, even if you believe it is true.
""".trimIndent()
        val genUser = """
SOURCE TITLE: ${source.title}
SOURCE TEXT:
<<<
$src
>>>

Task: Write ONE passage in the style of LSAT Reading Comprehension, 420-520 words, 3-5 paragraphs, based ONLY on the SOURCE TEXT.

Rules:
1. Every factual assertion (names, dates, numbers, events, causal claims, what any person or group believed or argued) must be directly supported by the SOURCE TEXT.
2. For EVERY sentence provide "q": a verbatim quotation of 8-40 consecutive words copied character-for-character from the SOURCE TEXT that supports the sentence. If a sentence contains no factual assertion (pure transition or framing), set "q" to "".
3. Style: dense, formal academic register typical of LSAT RC. Organize around a central thesis or interpretive question, and include at least one competing viewpoint, qualification, or complication drawn from the source. Vary sentence structure; use precise vocabulary.
4. No headings, lists, or bullet points. Title: short and descriptive.

Output JSON schema:
{"title": "string", "paragraphs": [[{"s": "sentence", "q": "verbatim quote or empty"}]]}
""".trimIndent()

        val gen = claude.completeJson(genSystem, genUser, 6000)
        val title = gen.str("title") ?: source.title
        val sents = ArrayList<Sent>()
        gen["paragraphs"].arr()?.forEachIndexed { pi, p ->
            p.arr()?.forEach { s ->
                val o = s.obj() ?: return@forEach
                val text = o.str("s")?.trim().orEmpty()
                if (text.isNotEmpty()) sents.add(Sent(text, o.str("q")?.trim().orEmpty(), pi))
            }
        }
        if (sents.size < 8) return GenResult(null, "생성 결과가 너무 짧음")

        // 로컬 검증: 제시한 인용문이 원문에 실제로 존재하는가
        val quoteOk = sents.map { it.quote.isEmpty() || TextUtil.quoteInSource(it.quote, normSrc, srcGrams) }

        // --- 2단계: 독립 검증 호출 ---
        val verifySystem = """
You are a meticulous fact-checker. You judge each sentence ONLY against the SOURCE TEXT provided, never against your own knowledge.
""".trimIndent()
        val listing = sents.mapIndexed { i, s -> "[$i] ${s.text}" }.joinToString("\n")
        val verifyUser = """
SOURCE TEXT:
<<<
$src
>>>

SENTENCES:
$listing

For each sentence decide:
- "SUPPORTED": every factual assertion in it is supported by the SOURCE TEXT (paraphrase is fine).
- "UNSUPPORTED": it contains any factual detail (number, date, name, event, causal link, attribution of a view to someone, superlative, generalization) that the SOURCE TEXT does not support, or it distorts the source.
- "NONFACTUAL": it makes no factual assertion (pure transition/framing).
Be strict: if in doubt, choose UNSUPPORTED.

Output JSON: {"results": [{"i": 0, "v": "SUPPORTED", "why": "short reason if UNSUPPORTED"}]}
""".trimIndent()
        val ver = claude.completeJson(verifySystem, verifyUser, 4000)
        val verdicts = HashMap<Int, Pair<String, String>>()
        ver["results"].arr()?.forEach { r ->
            val o = r.obj() ?: return@forEach
            val i = o.int("i") ?: return@forEach
            verdicts[i] = (o.str("v") ?: "").uppercase() to (o.str("why") ?: "")
        }

        val kept = ArrayList<Sent>()
        val notes = ArrayList<String>()
        var factual = 0
        sents.forEachIndexed { i, s ->
            val (v, why) = verdicts[i] ?: ("MISSING" to "검증 결과 누락")
            val keep = when (v) {
                "SUPPORTED" -> { factual++; quoteOk[i] }
                "NONFACTUAL" -> quoteOk[i]
                else -> { factual++; false }
            }
            if (keep) kept.add(s) else {
                val reason = when {
                    v == "SUPPORTED" && !quoteOk[i] -> "인용문이 원문에서 확인되지 않음"
                    else -> why.ifBlank { v }
                }
                notes.add("“${s.text.take(80)}…” → $reason")
            }
        }
        val removed = sents.size - kept.size
        if (removed.toDouble() / sents.size > 0.2) {
            return GenResult(null, "검증 탈락 문장 비율 초과(${removed}/${sents.size}) → 폐기")
        }
        val paras = kept.groupBy { it.para }.toSortedMap().values.map { ps -> ps.joinToString(" ") { it.text } }
        val body = paras.joinToString("\n\n")
        if (TextUtil.wordCount(body) < 280) return GenResult(null, "검증 후 분량 부족 → 폐기")

        val article = Article(
            id = "ai:" + TextUtil.hash(title + System.currentTimeMillis()),
            title = title,
            body = body,
            source = "AI 재구성 · 근거: ${source.source}",
            url = source.url,
            topic = source.topic,
            kind = Kind.AI,
            aiMeta = AiMeta(
                sources = listOf(SourceRef(source.title, source.url ?: "")),
                totalSentences = sents.size,
                factualSentences = factual,
                removedSentences = removed,
                removedNotes = notes,
            ),
        )
        return GenResult(article, "ok")
    }

    // ------------------------------------------------------------------
    // 2) RC 유형 문제
    // ------------------------------------------------------------------
    suspend fun questions(a: Article): List<RcQuestion> {
        val passage = TextUtil.trimParagraphs(a.paragraphs, 1400).mapIndexed { i, p -> "(${i + 1}) $p" }.joinToString("\n\n")
        val system = "You are an experienced LSAT Reading Comprehension question writer."
        val user = """
PASSAGE (paragraph numbers in parentheses):
<<<
$passage
>>>

Write 4 LSAT Reading Comprehension questions answerable solely from the passage:
1. Main Point
2. Primary Purpose or Author's Attitude
3. Inference ("most strongly supported by the passage")
4. Function / Structure (role of a paragraph, sentence, or phrase)
If the passage contains a clear argument, you may replace #3 or #4 with Strengthen/Weaken or Analogy.
Each question: exactly 5 answer choices (no letter prefixes), one unambiguously correct answer, attractive LSAT-style distractors (too broad, too narrow, extreme, out of scope, reversed).
"explanation" MUST be written in Korean: why the correct answer is right, and why the most tempting wrong choice is wrong.

Output JSON: {"questions":[{"type":"Main Point","stem":"...","choices":["...","...","...","...","..."],"answer":0,"explanation":"..."}]}
""".trimIndent()
        val o = claude.completeJson(system, user, 5000)
        return o["questions"].arr()?.mapNotNull { q ->
            val x = q.obj() ?: return@mapNotNull null
            val choices = x["choices"].arr()?.mapNotNull { c -> (c as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: return@mapNotNull null
            val ans = x.int("answer") ?: return@mapNotNull null
            if (choices.size < 4 || ans !in choices.indices) return@mapNotNull null
            RcQuestion(x.str("type") ?: "", x.str("stem") ?: "", choices, ans, x.str("explanation") ?: "")
        } ?: emptyList()
    }

    // ------------------------------------------------------------------
    // 3) 논지 구조 하이라이트
    // ------------------------------------------------------------------
    suspend fun roles(a: Article): RoleResult {
        val sents = TextUtil.sentencesByParagraph(a.body).flatten()
        val limited = sents.take(140)
        val listing = limited.mapIndexed { i, s -> "[$i] $s" }.joinToString("\n")
        val system = "You analyze argument structure the way an LSAT tutor does."
        val user = """
SENTENCES:
$listing

Label each sentence's role:
- MAIN: the passage's main conclusion / thesis (usually 1, at most 2)
- SUB: intermediate conclusion supporting the main point
- EVIDENCE: premise, data, example, or reasoning offered in support
- COUNTER: opposing view, objection, or concession
- BACKGROUND: context or neutral description
Also write "structure_ko": 2-3 Korean sentences describing how the argument is organized (e.g., which paragraph introduces the opposing view, where the author's position appears).

Output JSON: {"structure_ko":"...","roles":[{"i":0,"r":"BACKGROUND"}]}
""".trimIndent()
        val o = claude.completeJson(system, user, 5000)
        val map = HashMap<Int, String>()
        o["roles"].arr()?.forEach { r ->
            val x = r.obj() ?: return@forEach
            val i = x.int("i") ?: return@forEach
            map[i] = (x.str("r") ?: "BACKGROUND").uppercase()
        }
        return RoleResult(o.str("structure_ko") ?: "", map)
    }

    // ------------------------------------------------------------------
    // 4) 문맥 속 단어 뜻 (한국어)
    // ------------------------------------------------------------------
    suspend fun meaningInContext(word: String, sentence: String): String {
        val system = "You are a concise English tutor for a Korean LSAT student."
        val user = """
Word: "$word"
Sentence: "$sentence"
In Korean, in 2-3 short lines: (1) the meaning of the word in THIS sentence, (2) a natural Korean paraphrase of the sentence. Plain text only.
""".trimIndent()
        return claude.complete(system, user, 400).trim()
    }

    // ------------------------------------------------------------------
    // 5) 검색 문장 → 영어 검색어
    // ------------------------------------------------------------------
    suspend fun expandQuery(text: String): List<String> {
        val system = "You turn a reader's interest (Korean or English) into English search queries."
        val user = """
Interest: "$text"
Return 1-3 short English search queries (2-5 words each) suited to Wikipedia and news search.
Output JSON: {"queries":["...","..."]}
""".trimIndent()
        val o = claude.completeJson(system, user, 300)
        return o["queries"].arr()?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.filter { it.isNotBlank() }
            ?: emptyList()
    }
}
