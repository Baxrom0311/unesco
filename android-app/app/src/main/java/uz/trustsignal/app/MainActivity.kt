package uz.trustsignal.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        // Ekran qayta yaratilganda (masalan aylantirish, shrift o'zgarishi)
        // oxirgi natija yo'qolmasligi uchun jarayon xotirasida saqlaymiz
        private var lastContent: String? = null
        private var lastResult: AnalyzeResult? = null
    }

    private var analysisJob: kotlinx.coroutines.Job? = null
    private var defaultInputHint: CharSequence? = null

    private lateinit var statusText: TextView
    private lateinit var toggleBubbleButton: Button
    private lateinit var contentInput: EditText
    private lateinit var analyzeButton: Button
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var resultContainer: FrameLayout

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleBubbleButton = findViewById(R.id.toggleBubbleButton)
        contentInput = findViewById(R.id.contentInput)
        analyzeButton = findViewById(R.id.analyzeButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        errorText = findViewById(R.id.errorText)
        resultContainer = findViewById(R.id.resultContainer)

        defaultInputHint = contentInput.hint
        runEntranceAnimation()

        toggleBubbleButton.setOnClickListener { onToggleBubbleClicked() }
        analyzeButton.setOnClickListener {
            hideKeyboard()
            runAnalysis(contentInput.text.toString())
        }

        // Jarayon qayta tiklanganda (process death) tahlil ikkinchi marta avto-ishga tushmasin
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        } else {
            // Ekran qayta yaratilgan — oxirgi natijani qayta chizamiz
            val content = lastContent
            val result = lastResult
            if (content != null && result != null) {
                val view = AnalysisResultView(this)
                view.render(content, result)
                resultContainer.addView(view)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun streamUri(intent: Intent): android.net.Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun handleIncomingIntent(intent: Intent?) {
        // Skrinshot/rasm ulashilgan bo'lsa — vizual tahlil
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            streamUri(intent)?.let {
                runImageAnalysis(it)
                return
            }
        }

        // Ovozli xabar ulashilgan bo'lsa — transkript + tahlil
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
            streamUri(intent)?.let {
                runAudioAnalysis(it, intent.type)
                return
            }
        }

        val text = when {
            // Ba'zi ilovalar "text/plain; charset=utf-8" kabi mime yuboradi,
            // EXTRA_TEXT esa String emas CharSequence bo'lishi mumkin
            intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true ->
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            intent?.action == Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) {
            contentInput.setText(text)
            runAnalysis(text)
        }
    }

    private fun runImageAnalysis(uri: android.net.Uri) {
        errorText.visibility = View.GONE
        resultContainer.removeAllViews()
        loadingIndicator.visibility = View.VISIBLE
        analyzeButton.isEnabled = false
        contentInput.setText("")
        contentInput.hint = "Skrinshot tahlil qilinmoqda..."

        analysisJob?.cancel()
        analysisJob = lifecycleScope.launch {
            val jpeg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                loadScaledJpeg(uri)
            }
            if (jpeg == null) {
                showError("Rasmni o'qib bo'lmadi. Boshqa rasm bilan urinib ko'ring.")
                return@launch
            }
            when (val outcome = AnalyzeApi.analyzeImage(jpeg)) {
                is AnalyzeOutcome.Success -> {
                    val content = outcome.result.extractedText
                    lastContent = content
                    lastResult = outcome.result
                    resultContainer.removeAllViews()
                    val view = AnalysisResultView(this@MainActivity)
                    view.render(content, outcome.result)
                    resultContainer.addView(view)
                    animateIn(view)
                }
                is AnalyzeOutcome.Failure -> showError(outcome.message)
            }
            contentInput.hint = defaultInputHint
            loadingIndicator.visibility = View.GONE
            analyzeButton.isEnabled = true
        }
    }

    private fun runAudioAnalysis(uri: android.net.Uri, intentMime: String?) {
        errorText.visibility = View.GONE
        resultContainer.removeAllViews()
        loadingIndicator.visibility = View.VISIBLE
        analyzeButton.isEnabled = false
        contentInput.setText("")
        contentInput.hint = "Ovozli xabar tinglanmoqda va tahlil qilinmoqda..."

        analysisJob?.cancel()
        analysisJob = lifecycleScope.launch {
            val maxBytes = 12_000_000
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    // Oqim bo'ylab, limit bilan o'qiymiz — katta fayl (masalan
                    // audio deb ulashilgan video) RAMni to'ldirib yubormasin
                    contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            if (out.size() > maxBytes) break
                        }
                        out.toByteArray()
                    }
                }.onFailure {
                    android.util.Log.w("TrustSignal", "audio o'qishda xato: $uri", it)
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                showError("Audioni o'qib bo'lmadi. Boshqa fayl bilan urinib ko'ring.")
                return@launch
            }
            if (bytes.size > maxBytes) {
                showError("Audio juda katta (12MB gacha). Qisqaroq xabar yuboring.")
                return@launch
            }
            // "audio/*" kabi umumiy tur kelsa, aniq turini resolver'dan olamiz
            val rawMime = intentMime?.takeIf { it != "audio/*" } ?: contentResolver.getType(uri)
            val mime = normalizeAudioMime(rawMime)
            when (val outcome = AnalyzeApi.analyzeAudio(bytes, mime)) {
                is AnalyzeOutcome.Success -> {
                    val content = outcome.result.extractedText
                    lastContent = content
                    lastResult = outcome.result
                    resultContainer.removeAllViews()
                    val view = AnalysisResultView(this@MainActivity)
                    view.render(content, outcome.result)
                    resultContainer.addView(view)
                    animateIn(view)
                }
                is AnalyzeOutcome.Failure -> showError(outcome.message)
            }
            contentInput.hint = defaultInputHint
            loadingIndicator.visibility = View.GONE
            analyzeButton.isEnabled = true
        }
    }

    /** Har xil ilovalar yuboradigan mime variantlarini server biladigan ko'rinishga keltiradi. */
    private fun normalizeAudioMime(raw: String?): String {
        // "audio/ogg; codecs=opus" kabi parametrlarni olib tashlaymiz
        val cleaned = raw?.substringBefore(';')?.trim()?.lowercase()
        return when {
            cleaned.isNullOrBlank() || cleaned == "audio/*" || !cleaned.startsWith("audio/") -> "audio/mpeg"
            cleaned == "audio/x-wav" -> "audio/wav"
            cleaned == "audio/mp4a-latm" -> "audio/mp4"
            cleaned == "audio/m4a" -> "audio/x-m4a"
            else -> cleaned
        }
    }

    /** Rasmni trafik tejash uchun eng ko'pi 1280px va JPEG(80) ga kichraytiradi. */
    private fun loadScaledJpeg(uri: android.net.Uri): ByteArray? = runCatching {
        val maxDim = 1280
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) {
            sample *= 2
        }
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        } ?: return@runCatching null

        val scale = minOf(1f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
        val finalBitmap = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        val out = java.io.ByteArrayOutputStream()
        finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        out.toByteArray()
    }.onFailure {
        android.util.Log.w("TrustSignal", "rasm o'qishda xato: $uri", it)
    }.getOrNull()

    private fun runAnalysis(text: String) {
        val trimmed = text.trim()
        if (trimmed.length < 3) {
            showError("Iltimos, tahlil qilish uchun matn kiriting")
            return
        }

        errorText.visibility = View.GONE
        resultContainer.removeAllViews()
        loadingIndicator.visibility = View.VISIBLE
        analyzeButton.isEnabled = false

        // Oldingi so'rov tugamagan bo'lsa bekor qilamiz — ikkita natija
        // ustma-ust tushib qolmasligi uchun
        analysisJob?.cancel()
        analysisJob = lifecycleScope.launch {
            when (val outcome = AnalyzeApi.analyze(trimmed)) {
                is AnalyzeOutcome.Success -> {
                    // Maqola (URL) rejimida server sahifa matnini qaytaradi —
                    // highlight'lar unda ishlashi uchun o'shani ko'rsatamiz
                    val content = outcome.result.extractedText.ifBlank { trimmed }
                    lastContent = content
                    lastResult = outcome.result
                    resultContainer.removeAllViews()
                    val view = AnalysisResultView(this@MainActivity)
                    view.render(content, outcome.result)
                    resultContainer.addView(view)
                    animateIn(view)
                }
                is AnalyzeOutcome.Failure -> showError(outcome.message)
            }
            loadingIndicator.visibility = View.GONE
            analyzeButton.isEnabled = true
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        animateIn(errorText)
        loadingIndicator.visibility = View.GONE
        analyzeButton.isEnabled = true
        // Rasm/audio jarayonida o'zgartirilgan hint qotib qolmasin
        defaultInputHint?.let { contentInput.hint = it }
    }

    /** Kirishda elementlar pog'onali ravishda pastdan suzib chiqadi. */
    private fun runEntranceAnimation() {
        val column = findViewById<android.view.ViewGroup>(R.id.contentColumn)
        val rise = 26f * resources.displayMetrics.density
        for (i in 0 until column.childCount) {
            val v = column.getChildAt(i)
            v.alpha = 0f
            v.translationY = rise
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(60L + i * 55L)
                .setDuration(430L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                .start()
        }
    }

    /** Natija kartasi silliq suzib chiqadi. */
    private fun animateIn(view: View) {
        view.alpha = 0f
        view.translationY = 20f * resources.displayMetrics.density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(340L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(contentInput.windowToken, 0)
    }

    private fun onToggleBubbleClicked() {
        if (!hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        if (BubbleService.isRunning) {
            stopService(Intent(this, BubbleService::class.java))
        } else {
            requestNotificationPermissionIfNeeded()
            ContextCompat.startForegroundService(this, Intent(this, BubbleService::class.java))
        }
        // Xizmat holati asinxron o'zgaradi — biroz kutib yangilaymiz
        statusText.postDelayed({ updateStatus() }, 300)
        updateStatus()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun updateStatus() {
        if (!hasOverlayPermission()) {
            statusText.text = "Holat: ruxsat kerak"
            toggleBubbleButton.text = "Ruxsat berish"
            return
        }
        statusText.text = if (BubbleService.isRunning) {
            "Holat: yoqilgan"
        } else {
            "Holat: o'chirilgan"
        }
        toggleBubbleButton.text = if (BubbleService.isRunning) "O'chirish" else "Yoqish"
    }
}
