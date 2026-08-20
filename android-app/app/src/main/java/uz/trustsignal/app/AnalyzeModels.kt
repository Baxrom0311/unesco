package uz.milhackathon.ishonasizmi

data class Signal(
    val technique: String,
    val quote: String,
    val explanation: String
) {
    val isPhishing: Boolean
        get() = technique.contains("fishing", ignoreCase = true) ||
            technique.contains("phish", ignoreCase = true)
}

data class AnalyzeResult(
    val cautionLevel: String,
    val summary: String,
    val signals: List<Signal>,
    val tip: String
)

sealed class AnalyzeOutcome {
    data class Success(val result: AnalyzeResult) : AnalyzeOutcome()
    data class Failure(val message: String) : AnalyzeOutcome()
}
