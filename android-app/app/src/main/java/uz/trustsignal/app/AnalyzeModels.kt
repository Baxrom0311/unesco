package uz.trustsignal.app

data class Signal(
    val technique: String,
    val quote: String,
    val explanation: String,
    val category: String = "other",
    val severity: String = "suspicious"
) {
    val isPhishing: Boolean
        get() = category in setOf("phishing", "account_takeover") ||
            severity in setOf("high", "critical")
}

data class AnalyzeResult(
    val cautionLevel: String,
    val summary: String,
    val signals: List<Signal>,
    val tip: String,
    // Rasm/audio/maqola tahlilida keladi: o'qilgan matn yoki transkript
    val extractedText: String = "",
    // Rasmiy kanallar orqali xavfsiz tekshirish qadamlari.
    val checkSteps: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val riskLevel: String = "none",
    val riskTypes: List<String> = emptyList(),
    val immediateActions: List<String> = emptyList(),
    val recoverySteps: List<String> = emptyList()
)

sealed class AnalyzeOutcome {
    data class Success(val result: AnalyzeResult) : AnalyzeOutcome()
    data class Failure(val message: String) : AnalyzeOutcome()
}
