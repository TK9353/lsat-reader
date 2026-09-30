package com.taehyeon.lsatreader.ai

import com.taehyeon.lsatreader.data.AppJson
import com.taehyeon.lsatreader.data.Http
import com.taehyeon.lsatreader.data.TextUtil
import com.taehyeon.lsatreader.data.arr
import com.taehyeon.lsatreader.data.obj
import com.taehyeon.lsatreader.data.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class ClaudeClient(
    private val keyProvider: () -> String,
    private val modelProvider: () -> String,
) {
    val hasKey: Boolean get() = keyProvider().isNotBlank()

    private fun headers() = mapOf(
        "x-api-key" to keyProvider(),
        "anthropic-version" to "2023-06-01",
    )

    suspend fun complete(system: String, user: String, maxTokens: Int = 4096): String {
        require(hasKey) { "설정에서 Claude API 키를 입력하세요." }
        val body = buildJsonObject {
            put("model", modelProvider())
            put("max_tokens", maxTokens)
            put("system", system)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
        }
        val resp = Http.postJson("https://api.anthropic.com/v1/messages", body.toString(), headers())
        val root = AppJson.parseToJsonElement(resp).obj()
        return root?.get("content").arr()
            ?.mapNotNull { it.obj() }
            ?.filter { it.str("type") == "text" }
            ?.joinToString("") { it.str("text") ?: "" }
            ?: error("빈 응답")
    }

    /** JSON 객체 응답. 파싱 실패 시 1회 재시도 */
    suspend fun completeJson(system: String, user: String, maxTokens: Int = 4096): JsonObject {
        val sys = "$system\n\nRespond with a single valid JSON object only. No markdown fences, no commentary."
        return try {
            TextUtil.extractJsonObject(complete(sys, user, maxTokens))
        } catch (e: IllegalArgumentException) {
            // SerializationException 도 IllegalArgumentException 의 하위 타입
            TextUtil.extractJsonObject(complete(sys, user, maxTokens))
        }
    }

    suspend fun listModels(): List<String> {
        val json = Http.get("https://api.anthropic.com/v1/models?limit=100", headers(), ua = Http.API_UA)
        return AppJson.parseToJsonElement(json).obj()?.get("data").arr()
            ?.mapNotNull { it.obj().str("id") } ?: emptyList()
    }
}
