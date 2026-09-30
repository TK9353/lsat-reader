package com.taehyeon.lsatreader.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URLEncoder

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

// ====================================================================
// Wikipedia (영문)
// ====================================================================
class WikipediaSource {
    data class Hit(val title: String, val snippet: String)

    suspend fun search(query: String, limit: Int = 20): List<Hit> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srnamespace=0" +
            "&srlimit=$limit&format=json&formatversion=2&srsearch=${enc(query)}"
        val root = AppJson.parseToJsonElement(Http.get(url, ua = Http.API_UA)).obj()
        return root?.get("query").obj()?.get("search").arr()?.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val t = o.str("title") ?: return@mapNotNull null
            Hit(t, Jsoup.parse(o.str("snippet") ?: "").text())
        } ?: emptyList()
    }

    private val stopHeadings = setOf(
        "see also", "references", "notes", "further reading", "external links", "bibliography",
        "sources", "citations", "footnotes", "works cited", "gallery", "notes and references",
    )

    /** 문서 전문(평문) → Article. maxWords 초과분은 문단 단위로 자름 */
    suspend fun article(title: String, topic: Topic, maxWords: Int = 1300): Article? {
        val url = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts%7Cinfo&inprop=url" +
            "&explaintext=1&redirects=1&format=json&formatversion=2&titles=${enc(title)}"
        val root = AppJson.parseToJsonElement(Http.get(url, ua = Http.API_UA)).obj()
        val page = root?.get("query").obj()?.get("pages").arr()?.firstOrNull().obj() ?: return null
        val extract = page.str("extract") ?: return null
        val realTitle = page.str("title") ?: title
        val paras = ArrayList<String>()
        for (line in extract.lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            if (t.startsWith("==")) {
                val h = t.trim('=', ' ').lowercase()
                if (h in stopHeadings) break
                continue
            }
            if (TextUtil.wordCount(t) < 8) continue
            paras.add(t)
        }
        val trimmed = TextUtil.trimParagraphs(paras, maxWords)
        if (TextUtil.wordCount(trimmed.joinToString(" ")) < 180) return null
        return Article(
            id = "wiki:" + TextUtil.hash(realTitle),
            title = realTitle,
            body = trimmed.joinToString("\n\n"),
            source = "Wikipedia",
            url = page.str("fullurl") ?: "https://en.wikipedia.org/wiki/${enc(realTitle.replace(' ', '_'))}",
            topic = topic,
            kind = Kind.WIKI,
        )
    }

    /** 주제별 시드 검색어에서 무작위로 골라 아직 안 본 문서를 반환 */
    suspend fun randomForTopic(topic: Topic, seen: Set<String>): Article? {
        val seed = TopicSeeds.wiki.getValue(topic).random()
        val hits = search(seed, 25).shuffled()
        for (h in hits.take(6)) {
            if ("wiki:" + TextUtil.hash(h.title) in seen) continue
            if (h.title.startsWith("List of") || h.title.contains("(disambiguation)")) continue
            val a = article(h.title, topic) ?: continue
            if (a.id !in seen) return a
        }
        return null
    }
}

// ====================================================================
// The Guardian Content API
// ====================================================================
class GuardianSource(private val keyProvider: () -> String) {
    /** 공용 test 키는 2026-09 점검 시 401 응답 → 개인 키가 있을 때만 사용 */
    val enabled get() = keyProvider().isNotBlank()
    private val key get() = keyProvider()
    private val cache = HashMap<String, Pair<Long, List<Article>>>()
    private val lock = Mutex()

    private fun parse(json: String, topic: Topic): List<Article> {
        val results = AppJson.parseToJsonElement(json).obj()?.get("response").obj()?.get("results").arr()
            ?: return emptyList()
        return results.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val f = o["fields"].obj()
            val html = f.str("body") ?: return@mapNotNull null
            val paras = TextUtil.htmlToParagraphs(html)
            if (TextUtil.wordCount(paras.joinToString(" ")) < 250) return@mapNotNull null
            val section = o.str("sectionName") ?: ""
            Article(
                id = "gdn:" + TextUtil.hash(o.str("id") ?: o.str("webUrl") ?: ""),
                title = o.str("webTitle") ?: "",
                body = TextUtil.trimParagraphs(paras, 2500).joinToString("\n\n"),
                source = "The Guardian" + if (section.isNotBlank()) " · $section" else "",
                url = o.str("webUrl"),
                topic = topic,
                kind = Kind.GUARDIAN,
                author = f.str("byline"),
                published = o.str("webPublicationDate")?.take(10),
            )
        }
    }

    suspend fun search(q: String?, section: String?, topic: Topic, page: Int = 1, orderBy: String = "relevance"): List<Article> {
        if (!enabled) return emptyList()
        val sb = StringBuilder("https://content.guardianapis.com/search?type=article&page-size=20")
        sb.append("&show-fields=body,byline&order-by=").append(orderBy).append("&page=").append(page)
        if (!q.isNullOrBlank()) sb.append("&q=").append(enc(q))
        if (!section.isNullOrBlank()) sb.append("&section=").append(enc(section))
        sb.append("&api-key=").append(enc(key))
        return parse(Http.get(sb.toString(), ua = Http.API_UA), topic)
    }

    suspend fun randomForTopic(topic: Topic, seen: Set<String>): Article? {
        val cfg = TopicSeeds.guardian.getValue(topic).random()
        val cacheKey = "${cfg.first}|${cfg.second}"
        val now = System.currentTimeMillis()
        val list = lock.withLock {
            val c = cache[cacheKey]
            if (c != null && now - c.first < 60 * 60 * 1000 && c.second.any { it.id !in seen }) c.second
            else {
                val page = (1..3).random()
                val fresh = search(cfg.first, cfg.second, topic, page, if (cfg.first == null) "newest" else "relevance")
                cache[cacheKey] = now to fresh
                fresh
            }
        }
        return list.filter { it.id !in seen }.randomOrNull()
    }
}

// ====================================================================
// RSS / Atom + 본문 추출
// ====================================================================
class RssSource {
    data class Item(
        val title: String,
        val link: String,
        val summary: String,
        val contentHtml: String?,
        val author: String?,
        val date: String?,
        val feed: FeedDef,
    ) {
        val id get() = "rss:" + TextUtil.hash(link)
    }

    private val cache = HashMap<String, Pair<Long, List<Item>>>()
    private val lock = Mutex()

    suspend fun items(feed: FeedDef): List<Item> {
        val now = System.currentTimeMillis()
        lock.withLock {
            cache[feed.url]?.let { if (now - it.first < 60 * 60 * 1000) return it.second }
        }
        val xml = Http.get(feed.url, mapOf("Accept" to "application/rss+xml, application/atom+xml, application/xml, text/xml"))
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val nodes: List<Element> = doc.getElementsByTag("item").ifEmpty { doc.getElementsByTag("entry") }
        val list = nodes.mapNotNull { n ->
            val title = n.getElementsByTag("title").firstOrNull()?.text()?.let { Jsoup.parse(it).text() } ?: return@mapNotNull null
            val linkEl = n.getElementsByTag("link").firstOrNull { it.attr("rel").let { r -> r.isEmpty() || r == "alternate" } }
                ?: n.getElementsByTag("link").firstOrNull()
            val link = (linkEl?.attr("href")?.ifBlank { null } ?: linkEl?.text())?.trim()
            if (link.isNullOrBlank()) return@mapNotNull null
            val content = n.getElementsByTag("content:encoded").firstOrNull()?.text()
                ?: n.getElementsByTag("content").firstOrNull()?.text()
            val summaryRaw = n.getElementsByTag("description").firstOrNull()?.text()
                ?: n.getElementsByTag("summary").firstOrNull()?.text() ?: ""
            val author = n.getElementsByTag("dc:creator").firstOrNull()?.text()
                ?: n.getElementsByTag("author").firstOrNull()?.let { a -> a.getElementsByTag("name").firstOrNull()?.text() ?: a.text() }
            val date = (n.getElementsByTag("pubDate").firstOrNull()?.text()
                ?: n.getElementsByTag("published").firstOrNull()?.text()
                ?: n.getElementsByTag("updated").firstOrNull()?.text())
            Item(title, link, Jsoup.parse(summaryRaw).text(), content, author, date, feed)
        }
        lock.withLock { cache[feed.url] = now to list }
        return list
    }

    /** RSS 본문(content:encoded)이 충분하면 그대로, 아니면 원문 페이지에서 본문 추출 */
    suspend fun toArticle(item: Item, allowSummaryOnly: Boolean = false): Article? {
        var paras: List<String> = item.contentHtml?.let { TextUtil.htmlToParagraphs(it) } ?: emptyList()
        if (TextUtil.wordCount(paras.joinToString(" ")) < 300) {
            val extracted = runCatching { Extractor.extract(Http.get(item.link), item.link) }.getOrNull()
            if (extracted != null) paras = extracted
        }
        val words = TextUtil.wordCount(paras.joinToString(" "))
        if (words < 250) {
            if (!allowSummaryOnly || item.summary.isBlank()) return null
            paras = listOf(item.summary, "[본문을 가져올 수 없는 글입니다(유료 구독 또는 차단). ‘원문’ 버튼으로 열어 보세요.]")
        }
        return Article(
            id = item.id,
            title = item.title,
            body = TextUtil.trimParagraphs(paras, 2500).joinToString("\n\n"),
            source = item.feed.name,
            url = item.link,
            topic = item.feed.topic,
            kind = Kind.RSS,
            author = item.author,
            published = item.date?.take(16),
        )
    }

    suspend fun randomForTopic(topic: Topic, feeds: List<FeedDef>, seen: Set<String>): Article? {
        val candidates = feeds.filter { it.topic == topic }.shuffled()
        for (f in candidates.take(2)) {
            val items = runCatching { items(f) }.getOrNull() ?: continue
            for (item in items.filter { i -> i.id !in seen }.shuffled().take(3)) {
                val a = runCatching { toArticle(item) }.getOrNull()
                if (a != null) return a
            }
        }
        return null
    }

    /** 이미 불러온(캐시된) RSS 항목 중 검색어와 맞는 것 (검색어 단어 절반 이상 포함) */
    suspend fun searchCached(queries: List<String>): List<Item> {
        val all = lock.withLock { cache.values.flatMap { it.second } }
        val qs = queries.map { q -> q.lowercase().split(Regex("\\W+")).filter { it.length > 3 } }.filter { it.isNotEmpty() }
        if (qs.isEmpty()) return emptyList()
        return all.filter { i ->
            val t = (i.title + " " + i.summary).lowercase()
            qs.any { words -> words.count { t.contains(it) } * 2 >= words.size }
        }.distinctBy { it.link }
    }

    /** 사용자가 입력한 URL을 바로 열기 */
    suspend fun fromUrl(url: String): Article? {
        val html = Http.get(url)
        val title = Jsoup.parse(html).title().ifBlank { url }
        val paras = Extractor.extract(html, url) ?: return null
        if (TextUtil.wordCount(paras.joinToString(" ")) < 80) return null
        val host = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: "웹"
        return Article(
            id = "url:" + TextUtil.hash(url),
            title = title,
            body = TextUtil.trimParagraphs(paras, 4000).joinToString("\n\n"),
            source = host,
            url = url,
            topic = Topic.CULTURE,
            kind = Kind.RSS,
        )
    }
}

// ====================================================================
// HTML 본문 추출 (읽기 모드와 같은 방식)
// ====================================================================
object Extractor {
    fun extract(html: String, baseUrl: String): List<String>? {
        val doc = Jsoup.parse(html, baseUrl)
        doc.select(
            "script, style, noscript, nav, header, footer, aside, form, figure, figcaption, iframe, svg, " +
                "button, [role=navigation], [role=complementary], [aria-hidden=true], .newsletter, .related, .share, .advert, .ad"
        ).remove()
        val root: Element = doc.selectFirst("article") ?: run {
            val scores = HashMap<Element, Int>()
            for (p in doc.select("p")) {
                val len = p.text().length
                if (len < 40) continue
                val parent = p.parent() ?: continue
                scores[parent] = (scores[parent] ?: 0) + len
                parent.parent()?.let { gp -> scores[gp] = (scores[gp] ?: 0) + len / 2 }
            }
            scores.maxByOrNull { it.value }?.key
        } ?: return null
        val paras = root.select("p, h2, h3")
            .map { it.text().trim() }
            .filter { it.length > 25 && !TextUtil.isBoilerplate(it) }
            .distinct()
        return paras.ifEmpty { null }
    }
}

// ====================================================================
// 영영사전 (Free Dictionary API)
// ====================================================================
object Dictionary {
    suspend fun lookup(word: String): DictResult? {
        val w = word.lowercase().trim()
        val primary = runCatching { freeDictionary(w) }
        primary.getOrNull()?.let { return it }
        // 1차 사전 서버 오류/미등재 시 Wiktionary로 대체
        val wk = runCatching { wiktionary(w) }
        wk.getOrNull()?.let { return it }
        primary.exceptionOrNull()?.let { if (it !is HttpException || it.code != 404) throw it }
        return null
    }

    private suspend fun freeDictionary(w: String): DictResult? {
        val json = try {
            Http.get("https://api.dictionaryapi.dev/api/v2/entries/en/${enc(w)}", ua = Http.API_UA)
        } catch (e: HttpException) {
            if (e.code == 404) return null else throw e
        }
        val entries = AppJson.parseToJsonElement(json).arr() ?: return null
        val first = entries.firstOrNull().obj() ?: return null
        val phonetic = first.str("phonetic")
            ?: first["phonetics"].arr()?.mapNotNull { it.obj().str("text") }?.firstOrNull()
        val senses = ArrayList<Pair<String, String>>()
        for (e in entries) {
            for (m in e.obj()?.get("meanings").arr() ?: continue) {
                val pos = m.obj().str("partOfSpeech") ?: ""
                m.obj()?.get("definitions").arr()?.take(2)?.forEach { d ->
                    d.obj().str("definition")?.let { senses.add(pos to it) }
                }
            }
        }
        if (senses.isEmpty()) return null
        return DictResult(first.str("word") ?: w, phonetic, senses.take(6))
    }

    private suspend fun wiktionary(w: String): DictResult? {
        val json = try {
            Http.get("https://en.wiktionary.org/api/rest_v1/page/definition/${enc(w)}", ua = Http.API_UA)
        } catch (e: HttpException) {
            if (e.code == 404) return null else throw e
        }
        val en = AppJson.parseToJsonElement(json).obj()?.get("en").arr() ?: return null
        val senses = ArrayList<Pair<String, String>>()
        for (entry in en) {
            val pos = entry.obj().str("partOfSpeech")?.lowercase() ?: ""
            entry.obj()?.get("definitions").arr()?.take(2)?.forEach { d ->
                val text = Jsoup.parse(d.obj().str("definition") ?: "").text().trim()
                if (text.isNotEmpty()) senses.add(pos to text)
            }
        }
        if (senses.isEmpty()) return null
        return DictResult(w, null, senses.take(6))
    }
}
