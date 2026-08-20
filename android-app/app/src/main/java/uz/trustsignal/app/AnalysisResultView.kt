package uz.trustsignal.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Renders an AnalyzeResult natively (no WebView): caution badge, summary,
 * the original text with matched phrases highlighted inline, the signal
 * list, check steps, and the closing tip. All colors come from color
 * resources (values / values-night), so the view follows the system
 * light/dark mode automatically. Reused by MainActivity and the floating
 * bubble's result card.
 */
class AnalysisResultView(context: Context) : LinearLayout(context) {

    private fun c(id: Int): Int = ContextCompat.getColor(context, id)

    init {
        orientation = VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = glassBox(26)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun box(fill: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), c(R.color.glassStroke))
            cornerRadius = dp(radiusDp).toFloat()
        }

    /** Shisha karta: tepadan yorug'lik tushgan gradient + hairline hoshiya. */
    private fun glassBox(radiusDp: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(c(R.color.glassTop), c(R.color.glassFill))
        ).apply {
            setStroke(dp(1), c(R.color.glassStroke))
            cornerRadius = dp(radiusDp).toFloat()
        }

    /** Vector ikonkani bo'yab, TextView boshiga qo'yish uchun tayyorlaydi. */
    private fun icon(id: Int, tint: Int, sizeDp: Int = 18): android.graphics.drawable.Drawable? =
        ContextCompat.getDrawable(context, id)?.mutate()?.apply {
            setTint(tint)
            setBounds(0, 0, dp(sizeDp), dp(sizeDp))
        }

    private fun TextView.withIcon(id: Int, tint: Int, sizeDp: Int = 18) {
        setCompoundDrawablesRelative(icon(id, tint, sizeDp), null, null, null)
        compoundDrawablePadding = dp(8)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
    }

    fun render(content: String, result: AnalyzeResult) {
        removeAllViews()

        addView(badge(result.cautionLevel))

        addView(
            textView(result.summary, sizeSp = 15f, color = c(R.color.textPrimary), bold = true).apply {
                setPadding(0, dp(10), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            }
        )

        if (result.signals.isNotEmpty()) {
            addView(sectionTitle("Matn ichida qayerda:"))
            addView(
                TextView(context).apply {
                    text = buildHighlightedSpannable(context, content, result.signals)
                    textSize = 14f
                    setTextColor(c(R.color.textSecondary))
                    background = box(c(R.color.fieldBg), 16)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setLineSpacing(dp(3).toFloat(), 1f)
                }
            )

            addView(sectionTitle("Topilgan belgilar:"))
            result.signals.forEachIndexed { i, signal ->
                addView(signalCard(i + 1, signal))
            }
        } else {
            addView(
                textView(
                    "Aniq ishontirish/manipulyatsiya belgisi topilmadi. Baribir manbani mustaqil tekshiring.",
                    sizeSp = 13f,
                    color = c(R.color.textSecondary)
                ).apply { setPadding(0, dp(8), 0, 0) }
            )
        }

        addView(tipBox(result.tip))
        if (result.checkSteps.isNotEmpty()) {
            addView(checkStepsBox(result.checkSteps))
        }
        addView(shareButton(result))
    }

    /** SIFT uslubidagi "o'zingiz tekshiring" qadamlari. */
    private fun checkStepsBox(steps: List<String>): View {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            background = box(c(R.color.fieldBg), 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            layoutParams = lp
        }
        container.addView(
            textView("O'zingiz tekshirish uchun qadamlar", sizeSp = 13f, color = c(R.color.textPrimary), bold = true).apply {
                withIcon(R.drawable.ic_search_check, c(R.color.accent))
            }
        )
        steps.forEachIndexed { i, step ->
            container.addView(
                textView("${i + 1}. $step", sizeSp = 13f, color = c(R.color.textSecondary)).apply {
                    setPadding(0, dp(4), 0, 0)
                    setLineSpacing(dp(2).toFloat(), 1f)
                }
            )
        }
        return container
    }

    private fun badgeLabel(cautionLevel: String): String = when (cautionLevel) {
        "belgi_topilmadi" -> "Aniq belgi topilmadi"
        "kop_belgi" -> "Ko'plab ehtiyot belgisi bor"
        else -> "Bir nechta ehtiyot belgisi bor"
    }

    /** Natijani guruh/suhbatga ogohlantirish sifatida ulashish tugmasi. */
    private fun shareButton(result: AnalyzeResult): View {
        val button = TextView(context).apply {
            text = "Ogohlantirishni ulashish"
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(c(R.color.textPrimary))
            gravity = Gravity.CENTER
            background = box(c(R.color.fieldBg), 16)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            layoutParams = lp
        }
        button.setOnClickListener {
            val message = buildString {
                appendLine("Trust Signal tahlili")
                appendLine("Natija: ${badgeLabel(result.cautionLevel)}")
                appendLine()
                appendLine(result.summary)
                if (result.signals.isNotEmpty()) {
                    appendLine()
                    appendLine("Topilgan belgilar:")
                    result.signals.forEachIndexed { i, s ->
                        val prefix = if (s.isPhishing) "🎣" else "⚠️"
                        appendLine("$prefix ${i + 1}. ${s.technique}")
                    }
                }
                appendLine()
                appendLine("💡 ${result.tip}")
                appendLine()
                append("(Trust Signal matn uslubini tahlil qiladi — bu fakt rost/yolg'onligi haqida hukm emas.)")
            }
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, message)
            }
            val chooser = android.content.Intent.createChooser(send, "Ogohlantirishni ulashish")
            if (context !is android.app.Activity) {
                // Suzuvchi karta (service) kontekstidan ochilganda kerak
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooser) }
        }
        return button
    }

    private fun badge(cautionLevel: String): TextView {
        val label = badgeLabel(cautionLevel)
        val (bg, fg) = when (cautionLevel) {
            "belgi_topilmadi" -> c(R.color.badgeOkBg) to c(R.color.badgeOkFg)
            "kop_belgi" -> c(R.color.badgeDangerBg) to c(R.color.badgeDangerFg)
            else -> c(R.color.badgeWarnBg) to c(R.color.badgeWarnFg)
        }
        return TextView(context).apply {
            text = "\u25CF  $label"
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(fg)
            background = box(bg, 999)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
    }

    private fun sectionTitle(text: String): TextView =
        textView(text, sizeSp = 12f, color = c(R.color.textTertiary), bold = true).apply {
            setPadding(0, dp(14), 0, dp(6))
        }

    private fun signalCard(index: Int, signal: Signal): View {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            background = box(c(R.color.fieldBg), 16)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
        }
        val iconRes = if (signal.isPhishing) R.drawable.ic_hook else R.drawable.ic_alert
        val iconTint = if (signal.isPhishing) c(R.color.badgeDangerFg) else c(R.color.badgeWarnFg)
        container.addView(
            textView("$index. ${signal.technique}", sizeSp = 13f, color = c(R.color.textPrimary), bold = true).apply {
                withIcon(iconRes, iconTint, sizeDp = 16)
            }
        )
        container.addView(
            textView(signal.explanation, sizeSp = 13f, color = c(R.color.textSecondary)).apply {
                setPadding(0, dp(3), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            }
        )
        return container
    }

    private fun tipBox(tip: String): View {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            background = box(c(R.color.fieldBg), 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }
        container.addView(textView("Keyingi safar uchun maslahat", sizeSp = 13f, color = c(R.color.textPrimary), bold = true).apply {
                withIcon(R.drawable.ic_bulb, c(R.color.accent))
            })
        container.addView(
            textView(tip, sizeSp = 13f, color = c(R.color.textSecondary)).apply {
                setPadding(0, dp(4), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            }
        )
        return container
    }

    private fun textView(text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
        }
}
