package com.taehyeon.lsatreader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, msg: String) : IOException(msg)

object Http {
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"
    const val API_UA = "LsatReader/1.0 (personal study app; Android)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val slow: OkHttpClient = client.newBuilder()
        .readTimeout(240, TimeUnit.SECONDS)
        .callTimeout(260, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), ua: String = BROWSER_UA): String =
        withContext(Dispatchers.IO) {
            val rb = Request.Builder().url(url)
                .header("User-Agent", ua)
                .header("Accept-Language", "en-US,en;q=0.9")
            headers.forEach { (k, v) -> rb.header(k, v) }
            client.newCall(rb.build()).execute().use { r ->
                val body = r.body?.string() ?: ""
                if (!r.isSuccessful) throw HttpException(r.code, "HTTP ${r.code}: ${body.take(300)}")
                body
            }
        }

    suspend fun postJson(url: String, json: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val rb = Request.Builder().url(url)
                .post(json.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", API_UA)
            headers.forEach { (k, v) -> rb.header(k, v) }
            slow.newCall(rb.build()).execute().use { r ->
                val body = r.body?.string() ?: ""
                if (!r.isSuccessful) throw HttpException(r.code, "HTTP ${r.code}: ${body.take(500)}")
                body
            }
        }
}
