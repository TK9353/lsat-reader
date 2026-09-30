package com.taehyeon.lsatreader.data

import kotlinx.serialization.Serializable

@Serializable
enum class Topic(val label: String) {
    POLITICS("정치"),
    ECONOMICS("경제"),
    HISTORY("역사"),
    TECHNOLOGY("기술"),
    SCIENCE("과학"),
    LAW("법"),
    PHILOSOPHY("철학"),
    CULTURE("문화·사회");

    companion object {
        fun parse(s: String): Topic? = entries.firstOrNull { it.name.equals(s.trim(), ignoreCase = true) }
    }
}

/** 글 공급원 종류. 추천 알고리즘이 종류별 선호도도 학습함 */
@Serializable
enum class Kind(val label: String) {
    WIKI("Wikipedia"),
    GUARDIAN("Guardian"),
    RSS("매체·싱크탱크"),
    AI("AI 지문");
}

@Serializable
data class SourceRef(val title: String, val url: String)

@Serializable
data class AiMeta(
    val sources: List<SourceRef>,
    val totalSentences: Int,
    val factualSentences: Int,
    val removedSentences: Int,
    val removedNotes: List<String> = emptyList(),
)

@Serializable
data class Article(
    val id: String,
    val title: String,
    /** 문단은 빈 줄("\n\n")로 구분된 평문 */
    val body: String,
    val source: String,
    val url: String? = null,
    val topic: Topic,
    val kind: Kind,
    val author: String? = null,
    val published: String? = null,
    val aiMeta: AiMeta? = null,
) {
    val isAi: Boolean get() = kind == Kind.AI
    val paragraphs: List<String> get() = body.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    val wordCount: Int get() = TextUtil.wordCount(body)
}

@Serializable
data class RcQuestion(
    val type: String,
    val stem: String,
    val choices: List<String>,
    val answer: Int,
    val explanation: String,
)

@Serializable
data class RoleResult(
    val structureKo: String,
    /** 전역 문장 인덱스 -> 역할(MAIN, SUB, EVIDENCE, COUNTER, BACKGROUND) */
    val roles: Map<Int, String>,
)

@Serializable
data class VocabEntry(
    val word: String,
    val definition: String,
    val context: String,
    val source: String,
    val addedAt: Long,
)

@Serializable
data class FeedDef(val topic: Topic, val name: String, val url: String)

/** 검색 결과 한 줄. article이 있으면 바로 열고, 없으면 loader로 본문을 불러옴 */
class SearchResult(
    val title: String,
    val snippet: String,
    val source: String,
    val kind: Kind,
    val article: Article? = null,
    val loader: (suspend () -> Article?)? = null,
)

data class DictResult(
    val word: String,
    val phonetic: String?,
    val senses: List<Pair<String, String>>,
)
