package uz.milhackathon.ishonasizmi

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan

private const val COLOR_MANIPULATION_BG = "#FED7AA" // amber-200
private const val COLOR_MANIPULATION_FG = "#7C2D12" // amber-950-ish
private const val COLOR_PHISHING_BG = "#FECDD3" // rose-200
private const val COLOR_PHISHING_FG = "#881337" // rose-950-ish

/**
 * Builds a SpannableString highlighting each signal's exact quote within
 * [source], mirroring buildHighlightSegments() on the web frontend. Quotes
 * that can't be found verbatim in the source are silently skipped.
 */
fun buildHighlightedSpannable(source: String, signals: List<Signal>): SpannableString {
    data class Match(val start: Int, val end: Int, val phishing: Boolean)

    val matches = signals
        .mapNotNull { signal ->
            if (signal.quote.isBlank()) return@mapNotNull null
            val start = source.indexOf(signal.quote)
            if (start == -1) return@mapNotNull null
            Match(start, start + signal.quote.length, signal.isPhishing)
        }
        .sortedBy { it.start }

    val nonOverlapping = mutableListOf<Match>()
    var lastEnd = -1
    for (m in matches) {
        if (m.start >= lastEnd) {
            nonOverlapping.add(m)
            lastEnd = m.end
        }
    }

    val spannable = SpannableString(source)
    for (m in nonOverlapping) {
        val bg = if (m.phishing) COLOR_PHISHING_BG else COLOR_MANIPULATION_BG
        val fg = if (m.phishing) COLOR_PHISHING_FG else COLOR_MANIPULATION_FG
        spannable.setSpan(
            BackgroundColorSpan(Color.parseColor(bg)),
            m.start,
            m.end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor(fg)),
            m.start,
            m.end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    return spannable
}
