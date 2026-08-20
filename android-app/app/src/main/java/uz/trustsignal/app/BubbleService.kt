package uz.trustsignal.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.hypot

class BubbleService : Service() {

    companion object {
        /** MainActivity holatni shu orqali o'qiydi — xizmat o'lsa ham to'g'ri qoladi. */
        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager

    private var bubbleView: ImageView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var cardView: FrameLayout? = null
    private var analysisJob: Job? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val density: Float
        get() = resources.displayMetrics.density

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
        // Ruxsat keyinchalik olib qo'yilgan holda (masalan tizim xizmatni qayta
        // tiriltirsa) crash bo'lmasligi uchun tekshiramiz
        if (!android.provider.Settings.canDrawOverlays(this)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            addBubble()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        // v2: eski kanal MIN darajada yaratilgan edi; kanal sozlamalari
        // o'zgartirib bo'lmaydi, shuning uchun yangi id
        val channelId = "bubble_channel_v2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_LOW (MIN emas): Samsung One UI MIN darajali xizmatlarni
            // xotira tozalashda birinchi bo'lib o'ldiradi
            val channel = NotificationChannel(
                channelId,
                "Trust Signal xizmati",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val openAppIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Trust Signal fon rejimida ishlayapti")
            .setContentText("Matnni nusxalab, suzuvchi tugmani bosing")
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .build()
        ServiceCompat.startForeground(
            this,
            1,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    // ---------- Suzuvchi tugma ----------

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun addBubble() {
        val prefs = getSharedPreferences("bubble", Context.MODE_PRIVATE)

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        bubbleParams.gravity = Gravity.TOP or Gravity.START
        // Oxirgi qoldirilgan joyni eslab qolamiz
        bubbleParams.x = prefs.getInt("x", 0)
        bubbleParams.y = prefs.getInt("y", 300)

        val bubble = ImageView(this)
        bubble.setImageResource(R.drawable.ic_bubble)
        bubble.elevation = 8f * density
        val sizePx = dp(56)
        bubble.layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragging = false
        val clickThresholdMs = 400L
        // Qurilma zichligiga mos tizim chegarasi — 16px kabi qattiq qiymat
        // yuqori zichlikli ekranlarda oddiy bosishni "sudrash" deb xato o'qiydi
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop

        bubble.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    bubble.alpha = 0.75f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moved = hypot(
                        (event.rawX - initialTouchX).toDouble(),
                        (event.rawY - initialTouchY).toDouble()
                    )
                    if (!dragging && moved > touchSlop) dragging = true
                    if (dragging) {
                        bubbleParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        if (bubble.isAttachedToWindow) {
                            windowManager.updateViewLayout(bubble, bubbleParams)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    bubble.alpha = 1f
                    val elapsed = event.eventTime - event.downTime
                    if (event.action == MotionEvent.ACTION_UP && !dragging && elapsed < clickThresholdMs) {
                        v.performClick()
                        onBubbleTapped()
                    } else if (dragging) {
                        prefs.edit().putInt("x", bubbleParams.x).putInt("y", bubbleParams.y).apply()
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
        showCard()
    }

    // ---------- Shisha natija kartasi ----------

    /**
     * Oyna fokus olganda xabar beruvchi ildiz view. Android 10+ da clipboard
     * faqat fokusdagi oynaga ochiladi, shu sabab nusxalangan matn FAQAT shu
     * callback ichida o'qiladi (oldin o'qish har doim rad etilardi). Callback
     * har fokus qaytishida chaqiriladi — foydalanuvchi karta ochiq turganda
     * boshqa ilovadan matn nusxalab qaytsa ham ushlaymiz.
     */
    private inner class CardRoot(context: Context) : FrameLayout(context) {
        var onFocusGained: (() -> Unit)? = null

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus) {
                onFocusGained?.invoke()
            }
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                removeCard()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private fun c(id: Int): Int = androidx.core.content.ContextCompat.getColor(this, id)

    private fun glassBackground(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun showCard() {
        val root = CardRoot(this)
        val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        root.background = glassBackground(
            fill = c(R.color.overlayCardBg),
            stroke = c(R.color.glassStroke),
            radiusDp = 24
        )
        root.clipToOutline = true
        root.elevation = 24f * density

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(16), dp(14), dp(16), dp(16))
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Sarlavha qatori
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "Trust Signal"
        title.textSize = 15f
        title.setTextColor(c(R.color.textPrimary))
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        header.addView(
            title,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val closeButton = ImageView(this)
        closeButton.setImageResource(R.drawable.ic_close)
        closeButton.imageTintList = android.content.res.ColorStateList.valueOf(c(R.color.textSecondary))
        closeButton.setPadding(dp(7), dp(7), dp(7), dp(7))
        closeButton.background = glassBackground(
            fill = c(R.color.chipBg),
            stroke = c(R.color.chipBg),
            radiusDp = 999
        )
        closeButton.setOnClickListener { removeCard() }
        header.addView(closeButton, LinearLayout.LayoutParams(dp(32), dp(32)))
        column.addView(header)

        // Kontent maydoni: natija / progress / qo'lda kiritish
        val contentArea = FrameLayout(this)
        column.addView(
            contentArea,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(10)
            }
        )

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        contentArea.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val progress = ProgressBar(this)
        progress.indeterminateTintList = android.content.res.ColorStateList.valueOf(c(R.color.textPrimary))
        val progressParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        progressParams.gravity = Gravity.CENTER
        contentArea.addView(progress, progressParams)

        val screen = resources.displayMetrics
        val cardParams = WindowManager.LayoutParams(
            screen.widthPixels - dp(28),
            (screen.heightPixels * 0.62).toInt(),
            overlayType(),
            // FLAG_NOT_FOCUSABLE YO'Q — oyna fokus oladi, shunda clipboard o'qish mumkin
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        )
        cardParams.gravity = Gravity.CENTER
        cardParams.dimAmount = 0.45f
        cardParams.softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        if (blurAvailable) {
            cardParams.flags = cardParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            cardParams.blurBehindRadius = dp(24)
        }

        var settled = false
        var manualShown = false
        val showManualOnce: () -> Unit = {
            if (!manualShown && !settled && cardView === root) {
                manualShown = true
                showManualState(scroll, progress) { settled = true }
            }
        }
        val tryReadAndStart: () -> Unit = tryRead@{
            if (settled || cardView !== root) return@tryRead
            val clipText = readClipboard()
            android.util.Log.d("TrustSignal", "card focus: clip length=${clipText?.length ?: -1}")
            if (clipText != null && clipText.length >= 3) {
                settled = true
                startAnalysis(clipText, scroll, progress)
            } else if (!manualShown) {
                // Samsung'da fokus bilan poyga bo'lishi mumkin — bir marta qayta urinamiz
                root.postDelayed({
                    if (settled || cardView !== root) return@postDelayed
                    val retry = readClipboard()
                    android.util.Log.d("TrustSignal", "card retry: clip length=${retry?.length ?: -1}")
                    if (retry != null && retry.length >= 3) {
                        settled = true
                        startAnalysis(retry, scroll, progress)
                    } else {
                        showManualOnce()
                    }
                }, 250)
            }
        }
        // Har fokus olishda uriniladi — karta ochiq turganda boshqa ilovadan
        // matn nusxalab qaytilsa, avtomatik tahlil boshlanadi
        root.onFocusGained = tryReadAndStart
        // Fokus kelmasa ham (ba'zi qurilmalarda) qotib qolmaslik uchun zaxira
        root.postDelayed({ tryReadAndStart() }, 800)

        root.alpha = 0f
        root.scaleX = 0.95f
        root.scaleY = 0.95f
        root.translationY = dp(14).toFloat()
        try {
            windowManager.addView(root, cardParams)
        } catch (e: Exception) {
            return
        }
        root.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(280L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()
        cardView = root
    }

    private fun readClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun startAnalysis(text: String, scroll: ScrollView, progress: ProgressBar) {
        scroll.removeAllViews()
        progress.visibility = View.VISIBLE
        analysisJob?.cancel()
        analysisJob = serviceScope.launch {
            val outcome = AnalyzeApi.analyze(text)
            if (cardView == null || !scroll.isAttachedToWindow) return@launch
            progress.visibility = View.GONE
            when (outcome) {
                is AnalyzeOutcome.Success -> {
                    val resultView = AnalysisResultView(this@BubbleService)
                    resultView.render(text, outcome.result)
                    scroll.removeAllViews()
                    scroll.addView(resultView)
                }
                is AnalyzeOutcome.Failure -> {
                    val errorBox = LinearLayout(this@BubbleService)
                    errorBox.orientation = LinearLayout.VERTICAL

                    val errorView = TextView(this@BubbleService)
                    errorView.text = outcome.message
                    errorView.textSize = 13f
                    errorView.setTextColor(c(R.color.error))
                    errorView.background = glassBackground(
                        fill = c(R.color.errorBg),
                        stroke = c(R.color.error),
                        radiusDp = 14
                    )
                    errorView.setPadding(dp(12), dp(10), dp(12), dp(10))
                    errorBox.addView(errorView)

                    val retry = Button(this@BubbleService)
                    retry.text = "Qayta urinish"
                    retry.setTextColor(c(R.color.accentFg))
                    retry.textSize = 13f
                    retry.stateListAnimator = null
                    retry.background = glassBackground(
                        fill = c(R.color.accent),
                        stroke = c(R.color.accent),
                        radiusDp = 12
                    )
                    retry.setOnClickListener { startAnalysis(text, scroll, progress) }
                    errorBox.addView(
                        retry,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(10) }
                    )

                    scroll.removeAllViews()
                    scroll.addView(errorBox)
                }
            }
        }
    }

    /** Nusxalangan matn topilmasa: karta ichida qo'lda kiritish/joylash holati. */
    private fun showManualState(scroll: ScrollView, progress: ProgressBar, onSettled: () -> Unit) {
        progress.visibility = View.GONE
        scroll.removeAllViews()

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL

        val message = TextView(this)
        message.text = "Nusxalangan matn topilmadi. Xabarni bosib turib «Nusxalash»ni tanlang — karta ochiq bo'lsa, o'zi tahlil boshlaydi. Yoki matnni shu yerga yozing/joylashtiring:"
        message.textSize = 13f
        message.setTextColor(c(R.color.textSecondary))
        message.setLineSpacing(dp(2).toFloat(), 1f)
        box.addView(message)

        val input = EditText(this)
        input.hint = "Tekshiriladigan matn..."
        input.setHintTextColor(c(R.color.textTertiary))
        input.setTextColor(c(R.color.textPrimary))
        input.textSize = 14f
        input.minLines = 4
        input.gravity = Gravity.TOP or Gravity.START
        input.background = glassBackground(
            fill = c(R.color.fieldBg),
            stroke = c(R.color.fieldBg),
            radiusDp = 16
        )
        input.setPadding(dp(12), dp(10), dp(12), dp(10))
        box.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        // Tugmalar qatori: "Buferdan olish" (bir bosishda paste) + "Tahlil qilish"
        val buttonRow = LinearLayout(this)
        buttonRow.orientation = LinearLayout.HORIZONTAL

        val pasteButton = Button(this)
        pasteButton.text = "Buferdan olish"
        pasteButton.minimumHeight = dp(46)
        pasteButton.setTextColor(c(R.color.textPrimary))
        pasteButton.textSize = 13f
        pasteButton.stateListAnimator = null
        pasteButton.background = glassBackground(
            fill = c(R.color.chipBg),
            stroke = c(R.color.chipBg),
            radiusDp = 23
        )
        pasteButton.setOnClickListener {
            // Tugma bosilganda oynamiz aniq fokusda — o'qish ruxsat etiladi
            val clip = readClipboard()
            android.util.Log.d("TrustSignal", "paste tap: clip length=${clip?.length ?: -1}")
            if (clip != null && clip.length >= 3) {
                onSettled()
                startAnalysis(clip, scroll, progress)
            } else {
                input.hint = "Buferda matn yo'q — avval nusxalang"
            }
        }
        buttonRow.addView(
            pasteButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) }
        )

        val analyze = Button(this)
        analyze.text = "Tahlil qilish"
        analyze.setTextColor(c(R.color.accentFg))
        analyze.setTypeface(analyze.typeface, android.graphics.Typeface.BOLD)
        analyze.textSize = 13f
        analyze.stateListAnimator = null
        analyze.background = glassBackground(
            fill = c(R.color.accent),
            stroke = c(R.color.accent),
            radiusDp = 12
        )
        analyze.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.length >= 3) {
                onSettled()
                startAnalysis(text, scroll, progress)
            }
        }
        buttonRow.addView(analyze, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))

        box.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        scroll.addView(box)
    }

    private fun removeCard() {
        analysisJob?.cancel()
        analysisJob = null
        val card = cardView ?: return
        cardView = null
        card.animate()
            .alpha(0f)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .translationY(dp(10).toFloat())
            .setDuration(180L)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                try {
                    windowManager.removeView(card)
                } catch (e: IllegalArgumentException) {
                    // allaqachon olib tashlangan
                }
            }
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
        cardView?.let {
            it.animate().cancel()
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
            }
        }
        cardView = null
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
