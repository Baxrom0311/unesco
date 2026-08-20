package uz.trustsignal.app

data class Signal(
    val technique: String,
    val quote: String,
    val explanation: String
) {
    val isPhishing: Boolean
        get() = listOf("fishing", "phish", "firibgar", "scam").any {
            technique.contains(it, ignoreCase = true)
        }
}

data class AnalyzeResult(
    val cautionLevel: String,
    val summary: String,
    val signals: List<Signal>,
    val tip: String,
    // Rasm/audio/maqola tahlilida keladi: o'qilgan matn yoki transkript
    val extractedText: String = "",
    // Shu kontentni mustaqil tekshirish uchun 2-4 qadam (SIFT)
    val checkSteps: List<String> = emptyList()
)

sealed class AnalyzeOutcome {
    data class Success(val result: AnalyzeResult) : AnalyzeOutcome()
    data class Failure(val message: String) : AnalyzeOutcome()
}
