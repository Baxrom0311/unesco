import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from cyber import AnalyzeResult, Signal, finalize_risk


class RiskPolicyTests(unittest.TestCase):
    def result(self, signals):
        return AnalyzeResult(summary="Tahlil", signals=signals, tip="Tekshiring")

    def signal(self, severity="suspicious", category="phishing"):
        return Signal(technique="Belgi", quote="dalil", explanation="Izoh", severity=severity, category=category)

    def test_one_severe_signal_outweighs_many_weak_signals(self):
        weak = self.result([self.signal() for _ in range(20)])
        finalize_risk(weak)
        self.assertEqual(weak.riskLevel, "suspicious")
        strong = self.result([self.signal("high")])
        finalize_risk(strong)
        self.assertEqual(strong.riskLevel, "high")

    def test_reported_compromise_is_critical(self):
        result = self.result([self.signal("critical", "account_takeover"), self.signal("high")])
        finalize_risk(result)
        self.assertEqual(result.riskLevel, "critical")
        self.assertTrue(result.immediateActions)
        self.assertTrue(result.recoverySteps)

    def test_qr_presence_is_not_risk(self):
        result = self.result([self.signal("info", "other")])
        finalize_risk(result)
        self.assertEqual(result.riskLevel, "none")
        self.assertEqual(result.cautionLevel, "belgi_topilmadi")
        self.assertEqual(result.riskTypes, [])
        self.assertEqual(result.immediateActions, [])

    def test_empty_findings_do_not_keep_unfounded_alarm(self):
        result = self.result([])
        result.immediateActions = ["Foydalanuvchiga dalilsiz vahima"]
        result.recoverySteps = ["Hamma parollarni o'zgartiring"]
        finalize_risk(result)
        self.assertEqual(result.riskLevel, "none")
        self.assertFalse(result.recoverySteps)

    def test_url_findings_replace_stale_neutral_summary(self):
        result = self.result([self.signal(category="suspicious_link")])
        result.summary = "Belgi topilmadi"
        finalize_risk(result)
        self.assertNotEqual(result.summary, "Belgi topilmadi")
        self.assertEqual(result.riskLevel, "suspicious")
        self.assertTrue(result.immediateActions)

    def test_categories_deduplicated_and_recovery_is_relevant(self):
        result = self.result([self.signal("high", "payment_scam"), self.signal("high", "payment_scam")])
        finalize_risk(result)
        self.assertEqual(result.riskTypes, ["payment_scam"])
        self.assertEqual(len(result.recoverySteps), 1)
        self.assertIn("bank", result.recoverySteps[0])

    def test_model_specific_actions_preserved(self):
        result = self.result([self.signal("high")])
        result.immediateActions = ["Suhbatdagi kodni yubormang."]
        result.recoverySteps = ["Rasmiy ilovada sessiyalarni tekshiring."]
        finalize_risk(result)
        self.assertEqual(result.immediateActions, ["Suhbatdagi kodni yubormang."])
        self.assertEqual(result.recoverySteps, ["Rasmiy ilovada sessiyalarni tekshiring."])


if __name__ == "__main__":
    unittest.main()
