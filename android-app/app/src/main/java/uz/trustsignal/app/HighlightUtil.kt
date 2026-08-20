package uz.trustsignal.app

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat

/**
 * Model can return quotes with typographic apostrophes/quotes even when the
 * source used ASCII ones (o'/oʻ/o'), which would make a verbatim indexOf miss.
 * Every replacement is 1-char-to-1-char, so offsets found in the normalized
 * text map directly back onto the original string.
 */
private fun normalizeForMatch(s: String): String {
    val sb = StringBuilder(s.length)
    for (ch in s) {
        sb.append(
            when (ch) {
                '‘', '’', 'ʻ', 'ʼ', '`', '´' -> '\''
                '“', '”', '«', '»' -> '"'
                '\u00A0' -> ' '
                else -> ch
            }
        )
    }
    return sb.toString()
}

/**
 * Builds a SpannableString highlighting each signal's exact quote within
 * [source]. Matching is tolerant to apostrophe/quote variants and, as a
 * fallback, to letter case. Quotes that still can't be found are skipped.
 */
fun buildHighlightedSpannable(context: Context, source: String, signals: List<Signal>): SpannableString {
    data class Match(val start: Int, val end: Int, val phishing: Boolean)

    // Ranglar tizim light/dark rejimiga qarab values(-night)/colors.xml dan olinadi
    val manipBg = ContextCompat.getColor(context, R.color.hlManipBg)
    val manipFg = ContextCompat.getColor(context, R.color.hlManipFg)
    val phishBg = ContextCompat.getColor(context, R.color.hlPhishBg)
    val phishFg = ContextCompat.getColor(context, R.color.hlPhishFg)

    val normSource = normalizeForMatch(source)
    val normSourceLower = normSource.lowercase()

    // Har bir iqtibos uchun avval qabul qilingan diapazonlar bilan KESISHMAYDIGAN
    // birinchi uchrashuvni qidiramiz — shunda ikki signal bir xil iborani
    // keltirsa, ikkinchisi matndagi keyingi uchrashuvga tushadi (avval esa
    // ikkinchisi indamay tashlab yuborilardi)
    val nonOverlapping = mutableListOf<Match>()
    fun overlapsAccepted(start: Int, end: Int): Boolean =
        nonOverlapping.any { start < it.end && end > it.start }

    for (signal in signals) {
        if (signal.quote.isBlank()) continue
        val normQuote = normalizeForMatch(signal.quote.trim())
        val normQuoteLower = normQuote.lowercase()

        var start = normSource.indexOf(normQuote)
        var caseInsensitive = false
        if (start == -1) {
            start = normSourceLower.indexOf(normQuoteLower)
            caseInsensitive = true
        }
        val haystack = if (caseInsensitive) normSourceLower else normSource
        val needle = if (caseInsensitive) normQuoteLower else normQuote
        while (start != -1 && overlapsAccepted(start, start + needle.length)) {
            start = haystack.indexOf(needle, start + 1)
        }
        if (start == -1) continue
        nonOverlapping.add(Match(start, start + needle.length, signal.isPhishing))
    }

    val spannable = SpannableString(source)
    for (m in nonOverlapping) {
        val bg = if (m.phishing) phishBg else manipBg
        val fg = if (m.phishing) phishFg else manipFg
        spannable.setSpan(
            BackgroundColorSpan(bg),
            m.start,
            m.end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(fg),
            m.start,
            m.end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    return spannable
}
