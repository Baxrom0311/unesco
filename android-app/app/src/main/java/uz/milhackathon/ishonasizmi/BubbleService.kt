package uz.milhackathon.ishonasizmi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager

    private var bubbleView: ImageView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var cardView: FrameLayout? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
        addBubble()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val channelId = "bubble_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Ishonasizmi? xizmati",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ishonasizmi? fon rejimida ishlayapti")
            .setContentText("Matnni nusxalab, suzuvchi tugmani bosing")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        ServiceCompat.startForeground(
            this,
            1,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    // ---------- Suzuvchi tugma ----------

    private fun addBubble() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        bubbleParams.gravity = Gravity.TOP or Gravity.START
        bubbleParams.x = 0
        bubbleParams.y = 300

        val bubble = ImageView(this)
        bubble.setImageResource(R.drawable.ic_bubble)
        val sizePx = (56 * resources.displayMetrics.density).toInt()
        bubble.layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var downTime = 0L
        val clickThresholdMs = 400L
        val moveThresholdPx = 16f

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    bubbleParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubble, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val movedDist = hypot(
                        (event.rawX - initialTouchX).toDouble(),
                        (event.rawY - initialTouchY).toDouble()
                    )
                    val elapsed = System.currentTimeMillis() - downTime
                    if (movedDist < moveThresholdPx && elapsed < clickThresholdMs) {
                        onBubbleTapped()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, bubbleParams)
        bubbleView = bubble
    }

    private fun onBubbleTapped() {
        if (cardView != null) {
            removeCard()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()

        if (clipText.isNullOrBlank()) {
            Toast.makeText(this, "Avval matnni nusxalang (copy)", Toast.LENGTH_SHORT).show()
            return
        }

        showCard(clipText)
    }

    // ---------- Natija kartasi (alohida oyna EMAS, joriy ilova ustida karta, to'liq native) ----------

    private fun showCard(text: String) {
        val density = resources.displayMetrics.density

        val container = FrameLayout(this)
        container.setBackgroundColor(Color.WHITE)
        container.elevation = 24f * density

        val scroll = ScrollView(this)
        scroll.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(scroll)

        val progress = ProgressBar(this)
        val progressParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        progressParams.gravity = Gravity.CENTER
        container.addView(progress, progressParams)

        val closeButton = TextView(this)
        closeButton.text = "✕"
        closeButton.textSize = 16f
        closeButton.setTextColor(Color.WHITE)
        closeButton.gravity = Gravity.CENTER
        closeButton.setBackgroundColor(Color.parseColor("#18181B"))
        val closeSizePx = (30 * density).toInt()
        val closeParams = FrameLayout.LayoutParams(closeSizePx, closeSizePx)
        closeParams.gravity = Gravity.TOP or Gravity.END
        closeParams.topMargin = (10 * density).toInt()
        closeParams.rightMargin = (10 * density).toInt()
        closeButton.setOnClickListener { removeCard() }
        container.addView(closeButton, closeParams)

        container.isFocusableInTouchMode = true
        container.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                removeCard()
                true
            } else {
                false
            }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val metrics = resources.displayMetrics
        val marginPx = (16 * density).toInt()
        val widthPx = metrics.widthPixels - marginPx * 2
        val heightPx = (metrics.heightPixels * 0.65).toInt()

        val cardParams = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        cardParams.gravity = Gravity.CENTER

        windowManager.addView(container, cardParams)
        container.requestFocus()
        cardView = container

        serviceScope.launch {
            when (val outcome = AnalyzeApi.analyze(text)) {
                is AnalyzeOutcome.Success -> {
                    progress.visibility = android.view.View.GONE
                    val resultView = AnalysisResultView(this@BubbleService)
                    resultView.render(text, outcome.result)
                    scroll.addView(resultView)
                }
                is AnalyzeOutcome.Failure -> {
                    progress.visibility = android.view.View.GONE
                    val errorView = TextView(this@BubbleService)
                    errorView.text = outcome.message
                    errorView.setTextColor(Color.parseColor("#DC2626"))
                    errorView.setPadding(
                        (16 * density).toInt(),
                        (16 * density).toInt(),
                        (16 * density).toInt(),
                        (16 * density).toInt()
                    )
                    scroll.addView(errorView)
                }
            }
        }
    }

    private fun removeCard() {
        cardView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // allaqachon olib tashlangan
            }
        }
        cardView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        removeCard()
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // allaqachon olib tashlangan
            }
        }
        bubbleView = null
    }
}
