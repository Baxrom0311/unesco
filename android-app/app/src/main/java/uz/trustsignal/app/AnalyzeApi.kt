package uz.trustsignal.app

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AnalyzeApi {

    // Backend base URL — single source of truth.
    const val BASE_URL = "https://signal.boos.uz"

    // Backend ham xuddi shu chegarani qo'yadi — behuda so'rov yubormaslik uchun
    // mijoz tomonda oldindan tekshiramiz.
    const val MAX_CHARS = 8000

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Gemini ba'zan 20-30 soniya o'ylashi mumkin — timeout keng qo'yilgan
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(content: String): AnalyzeOutcome {
        val trimmed = content.trim()
        if (trimmed.length < 3) {
            return AnalyzeOutcome.Failure("Iltimos, tahlil qilish uchun matn kiriting (kamida 3 ta belgi)")
        }
        if (trimmed.length > MAX_CHARS) {
            return AnalyzeOutcome.Failure(
                "Matn juda uzun (${trimmed.length} belgi). Iltimos, $MAX_CHARS belgidan kamroq matn kiriting"
            )
        }

        val body = JSONObject().put("content", trimmed).toString()
        return post("$BASE_URL/api/analyze", body)
    }

    /** Skrinshot/rasm tahlili — JPEG baytlarini yuboradi. */
    suspend fun analyzeImage(jpegBytes: ByteArray): AnalyzeOutcome {
        if (jpegBytes.size > 5_500_000) {
            return AnalyzeOutcome.Failure("Rasm juda katta. Kichikroq rasm tanlang.")
        }
        val b64 = android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP)
        val body = JSONObject()
            .put("imageBase64", b64)
            .put("mimeType", "image/jpeg")
            .toString()
        return post("$BASE_URL/api/analyze-image", body)
    }

    /** Ovozli xabar tahlili — transkript + belgilar qaytadi. */
    suspend fun analyzeAudio(audioBytes: ByteArray, mimeType: String): AnalyzeOutcome {
        if (audioBytes.size > 12_000_000) {
            return AnalyzeOutcome.Failure("Audio juda katta (12MB gacha). Qisqaroq xabar yuboring.")
        }
        val b64 = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
        val body = JSONObject()
            .put("audioBase64", b64)
            .put("mimeType", mimeType)
            .toString()
        return post("$BASE_URL/api/analyze-audio", body)
    }

    private suspend fun post(url: String, jsonBody: String): AnalyzeOutcome {
        return try {
            val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            val (code, text) = executeForBody(request)
            if (code !in 200..299) {
                // FastAPI xatolari {"detail": "..."} ko'rinishida; detail massiv
                // bo'lsa (422 validatsiya) uni ko'rsatmaymiz — umumiy xabar beramiz
                val message = runCatching {
                    (JSONObject(text).opt("detail") as? String)?.takeIf { it.isNotBlank() }
                }.getOrNull()
                    ?: "Serverda xatolik yuz berdi ($code). Birozdan so'ng qayta urinib ko'ring."
                AnalyzeOutcome.Failure(message)
            } else {
                AnalyzeOutcome.Success(parseResult(text))
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: IOException) {
            AnalyzeOutcome.Failure("Tarmoq xatosi. Internet aloqasini tekshirib, qayta urinib ko'ring.")
        } catch (e: Exception) {
            AnalyzeOutcome.Failure("Kutilmagan xatolik yuz berdi. Qayta urinib ko'ring.")
        }
    }

    /**
     * OkHttp so'rovini coroutine bekor qilinishi bilan bog'laydi: karta yoki
     * ekran yopilsa so'rov ham darhol uziladi (execute() bilan esa 60 soniyagacha
     * fonda davom etardi). Javob tanasi OkHttp oqimida o'qiladi.
     */
    private suspend fun executeForBody(request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use { it.code to (it.body?.string().orEmpty()) }
                    }
                    if (cont.isActive) {
                        result.fold(
                            onSuccess = { cont.resume(it) },
                            onFailure = { cont.resumeWithException(it) }
                        )
                    }
                }
            })
            cont.invokeOnCancellation { runCatching { call.cancel() } }
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
        val stepsArray = obj.optJSONArray("checkSteps")
        val steps = buildList {
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    stepsArray.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
        return AnalyzeResult(
            cautionLevel = obj.optString("cautionLevel"),
            summary = obj.optString("summary"),
            signals = signals,
            tip = obj.optString("tip"),
            extractedText = obj.optString("extractedText"),
            checkSteps = steps
        )
    }
}
