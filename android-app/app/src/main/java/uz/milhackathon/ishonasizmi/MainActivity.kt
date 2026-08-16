package uz.milhackathon.ishonasizmi

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var controlPanel: View
    private lateinit var statusText: TextView
    private lateinit var toggleBubbleButton: Button

    private var bubbleRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        controlPanel = findViewById(R.id.controlPanel)
        statusText = findViewById(R.id.statusText)
        toggleBubbleButton = findViewById(R.id.toggleBubbleButton)
        val openAppButton: Button = findViewById(R.id.openAppButton)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        toggleBubbleButton.setOnClickListener { onToggleBubbleClicked() }
        openAppButton.setOnClickListener { showWebView(BASE_URL) }

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
            // Matn boshqa ilovada belgilanganda (select), "qo'shimcha imkoniyatlar"
            // panelidan bizning ilova tanlansa, shu yerga tushadi
            intent?.action == Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) {
            val encoded = URLEncoder.encode(text, "UTF-8")
            showWebView("$BASE_URL/?q=$encoded")
        }
    }

    private fun showWebView(url: String) {
        controlPanel.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(url)
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
            startService(Intent(this, BubbleService::class.java))
            bubbleRunning = true
        }
        updateStatus()
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

    companion object {
        private const val BASE_URL = "https://unesco-cyan.vercel.app"
    }
}
