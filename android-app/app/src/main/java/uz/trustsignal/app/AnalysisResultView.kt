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

        addView(badge(result.riskLevel))
        result.warnings.forEach { warning ->
            addView(textView(warning, 13f, c(R.color.textSecondary)).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }

        addView(
            textView(result.summary, sizeSp = 15f, color = c(R.color.textPrimary), bold = true).apply {
                setPadding(0, dp(10), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            }
        )

        if (result.riskTypes.isNotEmpty()) {
            addView(textView(result.riskTypes.joinToString(" · ") { threatLabel(it) }, 13f, c(R.color.textSecondary)).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }
        if (result.immediateActions.isNotEmpty()) {
            addView(checkStepsBox(result.immediateActions, context.getString(R.string.immediate_actions_title)))
        }
        if (result.recoverySteps.isNotEmpty()) {
            addView(checkStepsBox(result.recoverySteps, context.getString(R.string.recovery_steps_title)))
        }

        if (result.signals.isNotEmpty()) {
            addView(sectionTitle(context.getString(R.string.evidence_title)))
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

            addView(sectionTitle(context.getString(R.string.signals_title)))
            result.signals.forEachIndexed { i, signal ->
                addView(signalCard(i + 1, signal))
            }
        } else {
            addView(
                textView(
                    context.getString(R.string.no_risk_signals),
                    sizeSp = 13f,
                    color = c(R.color.textSecondary)
                ).apply { setPadding(0, dp(8), 0, 0) }
            )
        }

        addView(tipBox(result.tip))
        if (result.checkSteps.isNotEmpty()) {
            addView(checkStepsBox(result.checkSteps, context.getString(R.string.verification_title)))
        }
        addView(textView(context.getString(R.string.analysis_limits), 12f, c(R.color.textTertiary)).apply {
            setPadding(0, dp(12), 0, 0)
        })
        addView(shareButton(result))
    }

    /** Himoya, tiklash va xavfsiz tekshirish qadamlari. */
    private fun checkStepsBox(steps: List<String>, title: String): View {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            background = box(c(R.color.fieldBg), 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            layoutParams = lp
        }
        container.addView(
            textView(title, sizeSp = 13f, color = c(R.color.textPrimary), bold = true).apply {
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

    private fun badgeLabel(riskLevel: String): String = context.getString(when (riskLevel) {
        "none" -> R.string.risk_none
        "suspicious" -> R.string.risk_suspicious
        "high" -> R.string.risk_high
        "critical" -> R.string.risk_critical
        else -> R.string.risk_unknown
    })

    private fun threatLabel(category: String): String = context.getString(when (category) {
        "phishing" -> R.string.threat_phishing
        "impersonation" -> R.string.threat_impersonation
        "payment_scam" -> R.string.threat_payment
        "investment_scam" -> R.string.threat_investment
        "account_takeover" -> R.string.threat_account
        "malicious_software" -> R.string.threat_software
        "extortion" -> R.string.threat_extortion
        "shopping_scam" -> R.string.threat_shopping
        "job_scam" -> R.string.threat_job
        "romance_scam" -> R.string.threat_romance
        "suspicious_link" -> R.string.threat_link
        else -> R.string.threat_other
    })

    /** Natijani guruh/suhbatga ogohlantirish sifatida ulashish tugmasi. */
    private fun shareButton(result: AnalyzeResult): View {
        val button = TextView(context).apply {
            text = context.getString(R.string.share_warning_action)
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
                appendLine("Natija: ${badgeLabel(result.riskLevel)}")
                appendLine()
                appendLine(result.summary)
                result.warnings.forEach { appendLine(it) }
                if (result.signals.isNotEmpty()) {
                    appendLine()
                    appendLine("Topilgan belgilar:")
                    result.signals.forEachIndexed { i, s ->
                        val prefix = if (s.isPhishing) "🎣" else "⚠️"
                        appendLine("$prefix ${i + 1}. ${s.technique}")
                    }
                }
                appendLine()
                if (result.immediateActions.isNotEmpty()) {
                    appendLine(context.getString(R.string.immediate_actions_title))
                    result.immediateActions.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
                    appendLine()
                }
                appendLine("💡 ${result.tip}")
                appendLine()
                append(context.getString(R.string.analysis_limits))
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

    private fun badge(riskLevel: String): TextView {
        val label = badgeLabel(riskLevel)
        val (bg, fg) = when (riskLevel) {
            "none" -> c(R.color.fieldBg) to c(R.color.textSecondary)
            "high", "critical" -> c(R.color.badgeDangerBg) to c(R.color.badgeDangerFg)
            else -> c(R.color.badgeWarnBg) to c(R.color.badgeWarnFg)
        }
        return TextView(context).apply {
            text = context.getString(R.string.caution_badge, label)
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
        if (signal.quote.isNotBlank()) {
            container.addView(textView(signal.quote, 13f, c(R.color.textPrimary)).apply {
                setPadding(0, dp(6), 0, 0)
                setTextIsSelectable(true)
                // Display QR/URL text without auto-opening an untrusted link.
                autoLinkMask = 0
            })
        }
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
        container.addView(textView(context.getString(R.string.prevention_title), sizeSp = 13f, color = c(R.color.textPrimary), bold = true).apply {
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
