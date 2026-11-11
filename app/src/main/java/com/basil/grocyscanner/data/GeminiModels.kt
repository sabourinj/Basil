package com.basil.grocyscanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeminiModelOption(val id: String, val label: String)

// Lists the Gemini models worth offering in Settings: stable Flash models that
// can generate text, newest first.
object GeminiModels {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Variants that aren't plain text models, or aren't stable
    private val EXCLUDED = listOf("preview", "exp", "tts", "image", "live", "audio", "embedding", "thinking", "latest")
    private val VERSION = Regex("""gemini-(\d+(?:\.\d+)?)""")

    // "gemini-3.10-flash" -> 3, then 10 (compared as numbers, so 3.10 beats 3.8)
    private fun versionPart(id: String, index: Int): Int =
        VERSION.find(id)?.groupValues?.get(1)?.split(".")?.getOrNull(index)?.toIntOrNull() ?: 0

    val latestOption = GeminiModelOption(GeminiConfig.LATEST_FLASH_ALIAS, "Latest Flash (auto)")

    suspend fun fetchFlashModels(apiKey: String): List<GeminiModelOption> = withContext(Dispatchers.IO) {
        // Key in a header, not the URL, so it never shows up in logs.
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000")
            .header("x-goog-api-key", apiKey)
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
        val models = JSONObject(body).optJSONArray("models") ?: return@withContext emptyList()

        (0 until models.length())
            .map { models.getJSONObject(it) }
            .filter { m ->
                val methods = m.optJSONArray("supportedGenerationMethods")
                val generates = methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                val id = m.optString("name").removePrefix("models/")
                generates && id.startsWith("gemini-") && "flash" in id && EXCLUDED.none { it in id }
            }
            .map { m ->
                val id = m.optString("name").removePrefix("models/")
                GeminiModelOption(id, m.optString("displayName").ifBlank { id })
            }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<GeminiModelOption> { versionPart(it.id, 0) }
                    .thenByDescending { versionPart(it.id, 1) }
                    .thenBy { "lite" in it.id } // Flash before Flash-Lite
                    .thenBy { it.id }
            )
    }
}
