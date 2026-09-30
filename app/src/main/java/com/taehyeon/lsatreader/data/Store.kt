package com.taehyeon.lsatreader.data

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** 설정(SharedPreferences) + JSON 파일 저장소. 모든 데이터는 기기 내부에만 저장됨 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("lsat_reader", Context.MODE_PRIVATE)
    private val dir: File = context.filesDir

    var claudeKey: String
        get() = prefs.getString("claude_key", "") ?: ""
        set(v) = prefs.edit().putString("claude_key", v.trim()).apply()

    var claudeModel: String
        get() = prefs.getString("claude_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = prefs.edit().putString("claude_model", v.trim().ifEmpty { DEFAULT_MODEL }).apply()

    var guardianKey: String
        get() = prefs.getString("guardian_key", "") ?: ""
        set(v) = prefs.edit().putString("guardian_key", v.trim()).apply()

    /** 피드에서 AI 지문이 차지하는 비율(0~0.5) */
    var aiRatio: Float
        get() = prefs.getFloat("ai_ratio", 0.2f)
        set(v) = prefs.edit().putFloat("ai_ratio", v).apply()

    var enabledTopics: Set<Topic>
        get() {
            val s = prefs.getStringSet("topics", null) ?: return Topic.entries.toSet()
            return s.mapNotNull { Topic.parse(it) }.toSet().ifEmpty { Topic.entries.toSet() }
        }
        set(v) = prefs.edit().putStringSet("topics", v.map { it.name }.toSet()).apply()

    var enabledKinds: Set<Kind>
        get() {
            val s = prefs.getStringSet("kinds", null) ?: return Kind.entries.toSet()
            return s.mapNotNull { n -> Kind.entries.firstOrNull { it.name == n } }.toSet()
                .ifEmpty { setOf(Kind.WIKI) }
        }
        set(v) = prefs.edit().putStringSet("kinds", v.map { it.name }.toSet()).apply()

    var feedsText: String
        get() = prefs.getString("feeds", null) ?: DefaultFeeds.TEXT
        set(v) = prefs.edit().putString("feeds", v).apply()

    val feeds: List<FeedDef>
        get() = feedsText.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
            val parts = t.split("|").map { it.trim() }
            if (parts.size < 3) return@mapNotNull null
            val topic = Topic.parse(parts[0]) ?: return@mapNotNull null
            FeedDef(topic, parts[1], parts[2])
        }

    // ---------- JSON 파일 ----------
    private fun <T> read(name: String, ser: KSerializer<T>, default: T): T = try {
        val f = File(dir, name)
        if (f.exists()) AppJson.decodeFromString(ser, f.readText()) else default
    } catch (e: Exception) {
        default
    }

    private fun <T> write(name: String, ser: KSerializer<T>, value: T) {
        try {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(AppJson.encodeToString(ser, value))
            tmp.renameTo(File(dir, name))
        } catch (_: Exception) {
        }
    }

    private val weightSer = MapSerializer(String.serializer(), Double.serializer())

    fun loadWeights(): MutableMap<String, Double> = read("weights.json", weightSer, emptyMap()).toMutableMap()
    fun saveWeights(w: Map<String, Double>) = write("weights.json", weightSer, w)

    fun loadSeen(): MutableList<String> = read("seen.json", ListSerializer(String.serializer()), emptyList()).toMutableList()
    fun saveSeen(s: List<String>) = write("seen.json", ListSerializer(String.serializer()), s.takeLast(4000))

    fun loadVocab(): List<VocabEntry> = read("vocab.json", ListSerializer(VocabEntry.serializer()), emptyList())
    fun saveVocab(v: List<VocabEntry>) = write("vocab.json", ListSerializer(VocabEntry.serializer()), v)

    fun loadLiked(): List<Article> = read("liked.json", ListSerializer(Article.serializer()), emptyList())
    fun saveLiked(v: List<Article>) = write("liked.json", ListSerializer(Article.serializer()), v)

    private val qSer = MapSerializer(String.serializer(), ListSerializer(RcQuestion.serializer()))
    fun loadQuestions(): Map<String, List<RcQuestion>> = read("questions.json", qSer, emptyMap())
    fun saveQuestions(v: Map<String, List<RcQuestion>>) = write("questions.json", qSer, v.entries.toList().takeLast(300).associate { it.key to it.value })

    private val rSer = MapSerializer(String.serializer(), RoleResult.serializer())
    fun loadRoles(): Map<String, RoleResult> = read("roles.json", rSer, emptyMap())
    fun saveRoles(v: Map<String, RoleResult>) = write("roles.json", rSer, v.entries.toList().takeLast(300).associate { it.key to it.value })

    fun clearCaches() {
        listOf("seen.json", "questions.json", "roles.json").forEach { File(dir, it).delete() }
    }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-4-5"
    }
}
