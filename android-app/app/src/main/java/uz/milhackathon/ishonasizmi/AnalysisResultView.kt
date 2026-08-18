package uz.milhackathon.ishonasizmi

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Renders an AnalyzeResult natively (no WebView): caution badge, summary,
 * the original text with matched phrases highlighted inline, the signal
 * list, and the closing tip. Reused by MainActivity and the floating
 * bubble's result card.
 */
class AnalysisResultView(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = roundedBackground(Color.WHITE, dp(16).toFloat())
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    fun render(content: String, result: AnalyzeResult) {
        removeAllViews()

        addView(badge(result.cautionLevel))

        addView(
            textView(result.summary, sizeSp = 15f, colorHex = "#18181B", bold = true).apply {
                setPadding(0, dp(10), 0, 0)
            }
        )

        if (result.signals.isNotEmpty()) {
            addView(sectionTitle("Matn ichida qayerda:"))
            addView(
                TextView(context).apply {
                    text = buildHighlightedSpannable(content, result.signals)
                    textSize = 14f
                    setTextColor(Color.parseColor("#3F3F46"))
                    setBackgroundColor(Color.parseColor("#FAFAFA"))
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    setLineSpacing(dp(2).toFloat(), 1f)
                    movementMethod = LinkMovementMethod.getInstance()
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
                    colorHex = "#71717A"
                ).apply { setPadding(0, dp(8), 0, 0) }
            )
        }

        addView(tipBox(result.tip))
    }

    private fun badge(cautionLevel: String): TextView {
        val (label, bg, fg) = when (cautionLevel) {
            "belgi_topilmadi" -> Triple("Aniq belgi topilmadi", "#D1FAE5", "#065F46")
            "kop_belgi" -> Triple("Ko'plab ehtiyot belgisi bor", "#FEE2E2", "#991B1B")
            else -> Triple("Bir nechta ehtiyot belgisi bor", "#FEF3C7", "#92400E")
        }
        return TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor(fg))
            background = roundedBackground(Color.parseColor(bg), dp(999).toFloat())
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
    }

    private fun sectionTitle(text: String): TextView =
        textView(text, sizeSp = 12f, colorHex = "#71717A", bold = true).apply {
            setPadding(0, dp(14), 0, dp(4))
        }

    private fun signalCard(index: Int, signal: Signal): View {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply {
                setStroke(dp(1), Color.parseColor("#E4E4E7"))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6)
            layoutParams = lp
        }
        val prefix = if (signal.isPhishing) "🎣 " else "⚠️ "
        container.addView(
            textView("$prefix$index. ${signal.technique}", sizeSp = 13f, colorHex = "#18181B", bold = true)
        )
        container.addView(
            textView(signal.explanation, sizeSp = 13f, colorHex = "#52525B").apply {
                setPadding(0, dp(2), 0, 0)
            }
        )
        return container
    }

    private fun tipBox(tip: String): View {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            background = roundedBackground(Color.parseColor("#18181B"), dp(12).toFloat())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            layoutParams = lp
        }
        container.addView(textView("💡 Keyingi safar uchun maslahat:", sizeSp = 12f, colorHex = "#FFFFFF", bold = true))
        container.addView(
            textView(tip, sizeSp = 13f, colorHex = "#E4E4E7").apply {
                setPadding(0, dp(4), 0, 0)
            }
        )
        return container
    }

    private fun textView(text: String, sizeSp: Float, colorHex: String, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(Color.parseColor(colorHex))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
        }
}
