package com.taehyeon.lsatreader.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup
import java.security.MessageDigest

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
fun JsonObject?.str(k: String): String? = (this?.get(k) as? JsonPrimitive)?.contentOrNull
fun JsonObject?.int(k: String): Int? = (this?.get(k) as? JsonPrimitive)?.intOrNull

object TextUtil {
    private val wordRegex = Regex("[A-Za-z0-9’'\\-]+")

    fun wordCount(s: String): Int = wordRegex.findAll(s).count()

    fun hash(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return d.take(8).joinToString("") { "%02x".format(it) }
    }

    /** 문장 분리: 마침표/물음표/느낌표(+닫는 따옴표·괄호) 뒤 공백 + 대문자/따옴표 시작 */
    private val sentenceSplit = Regex("(?<=[.!?][\"”’)\\]]?)\\s+(?=[\"“‘(\\[]?[A-Z0-9])")
    private val abbrev = Regex("(?i)(\\b(mr|mrs|ms|dr|prof|sr|jr|st|vs|etc|inc|ltd|co|corp|gov|sen|rep|gen|u\\.s|e\\.g|i\\.e|no|vol|fig|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)\\.|\\b[A-Z]\\.)$")

    fun splitSentences(paragraph: String): List<String> {
        val raw = paragraph.split(sentenceSplit)
        val out = ArrayList<String>()
        var buf = ""
        for (part in raw) {
            buf = if (buf.isEmpty()) part else "$buf $part"
            if (!abbrev.containsMatchIn(buf.trimEnd())) {
                out.add(buf.trim()); buf = ""
            }
        }
        if (buf.isNotBlank()) out.add(buf.trim())
        return out.filter { it.isNotBlank() }
    }

    /** 문단별 문장 목록 */
    fun sentencesByParagraph(body: String): List<List<String>> =
        body.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }.map { splitSentences(it) }

    fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[“”\"‘’'`]"), "")
            .replace(Regex("[–—-]"), " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** 인용문이 원문에 실제 존재하는지(정규화 후 포함 또는 4-gram 85% 이상 일치) */
    fun quoteInSource(quote: String, normalizedSource: String, sourceGrams: Set<String>): Boolean {
        val q = normalize(quote)
        if (q.split(" ").size < 3) return false
        if (normalizedSource.contains(q)) return true
        val grams = ngrams(q, 4)
        if (grams.isEmpty()) return false
        val hit = grams.count { it in sourceGrams }
        return hit.toDouble() / grams.size >= 0.85
    }

    fun ngrams(normalized: String, n: Int): Set<String> {
        val w = normalized.split(" ").filter { it.isNotEmpty() }
        if (w.size < n) return emptySet()
        return (0..w.size - n).map { w.subList(it, it + n).joinToString(" ") }.toSet()
    }

    /** 앞에서부터 maxWords 단어까지 문단 단위로 자름 */
    fun trimParagraphs(paras: List<String>, maxWords: Int): List<String> {
        val out = ArrayList<String>()
        var n = 0
        for (p in paras) {
            val w = wordCount(p)
            if (n > 0 && n + w > maxWords) break
            out.add(p); n += w
        }
        return out
    }

    /** HTML 조각을 문단 리스트로 */
    fun htmlToParagraphs(html: String): List<String> {
        val doc = Jsoup.parseBodyFragment(html)
        val els = doc.select("p, h2, h3, blockquote > p, li")
        val paras = els.map { it.text().trim() }.filter { it.length > 1 }
        val cleaned = paras.filter { !isBoilerplate(it) }.distinct()
        if (cleaned.isNotEmpty()) return cleaned
        return doc.text().split(Regex("\\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private val boiler = Regex(
        "(?i)^(advertisement|sign up|subscribe|read more|related:|follow us|share this|this article (is republished|was originally)|copyright|©|photo:|image:|click here|newsletter)"
    )

    fun isBoilerplate(p: String): Boolean = boiler.containsMatchIn(p.trim())

    /** 응답 텍스트에서 첫 JSON 객체를 추출 */
    fun extractJsonObject(text: String): JsonObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "JSON 응답 없음" }
        return AppJson.parseToJsonElement(text.substring(start, end + 1)) as JsonObject
    }
}
