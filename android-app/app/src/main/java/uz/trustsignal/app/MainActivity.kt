package uz.milhackathon.ishonasizmi

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

    private lateinit var statusText: TextView
    private lateinit var toggleBubbleButton: Button
    private lateinit var contentInput: EditText
    private lateinit var analyzeButton: Button
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var resultContainer: FrameLayout

    private var bubbleRunning = false

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

        toggleBubbleButton.setOnClickListener { onToggleBubbleClicked() }
        analyzeButton.setOnClickListener { runAnalysis(contentInput.text.toString()) }

        handleIncomingIntent(intent)
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

    private fun handleIncomingIntent(intent: Intent?) {
        val text = when {
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" ->
                intent.getStringExtra(Intent.EXTRA_TEXT)
            intent?.action == Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) {
            contentInput.setText(text)
            runAnalysis(text)
        }
    }

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

        lifecycleScope.launch {
            when (val outcome = AnalyzeApi.analyze(trimmed)) {
                is AnalyzeOutcome.Success -> {
                    val view = AnalysisResultView(this@MainActivity)
                    view.render(trimmed, outcome.result)
                    resultContainer.addView(view)
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
        loadingIndicator.visibility = View.GONE
        analyzeButton.isEnabled = true
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

        if (bubbleRunning) {
            stopService(Intent(this, BubbleService::class.java))
            bubbleRunning = false
        } else {
            requestNotificationPermissionIfNeeded()
            ContextCompat.startForegroundService(this, Intent(this, BubbleService::class.java))
            bubbleRunning = true
        }
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
            statusText.text = "Holat: ruxsat berilmagan (\"boshqa ilovalar ustida chizish\")"
            toggleBubbleButton.text = "Ruxsat berish"
            return
        }
        statusText.text = if (bubbleRunning) {
            "Holat: suzuvchi tugma YOQILGAN"
        } else {
            "Holat: suzuvchi tugma o'chirilgan"
        }
        toggleBubbleButton.text = if (bubbleRunning) "O'chirish" else "Suzuvchi tugmani yoqish"
    }
}
