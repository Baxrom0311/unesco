package uz.milhackathon.ishonasizmi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object AnalyzeApi {

    // Backend base URL — single source of truth. Update this when the
    // backend moves off Vercel.
    const val BASE_URL = "https://unesco-cyan.vercel.app"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(content: String): AnalyzeOutcome = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("content", content).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/api/analyze")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(text).optString("error") }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Server xatosi (${response.code})"
                    return@withContext AnalyzeOutcome.Failure(message)
                }
                AnalyzeOutcome.Success(parseResult(text))
            }
        } catch (e: IOException) {
            AnalyzeOutcome.Failure("Tarmoq xatosi. Internet aloqasini tekshiring.")
        } catch (e: Exception) {
            AnalyzeOutcome.Failure("Kutilmagan xatolik: ${e.message}")
        }
    }

    private fun parseResult(json: String): AnalyzeResult {
        val obj = JSONObject(json)
        val signalsArray = obj.optJSONArray("signals")
        val signals = buildList {
            if (signalsArray != null) {
                for (i in 0 until signalsArray.length()) {
                    val s = signalsArray.getJSONObject(i)
                    add(
                        Signal(
                            technique = s.optString("technique"),
                            quote = s.optString("quote"),
                            explanation = s.optString("explanation")
                        )
                    )
                }
            }
        }
        return AnalyzeResult(
            cautionLevel = obj.optString("cautionLevel"),
            summary = obj.optString("summary"),
            signals = signals,
            tip = obj.optString("tip")
        )
    }
}
