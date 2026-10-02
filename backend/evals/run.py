"""Opt-in smoke evaluation; calls the configured real provider, never runs in CI."""
import argparse
import json
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", action="store_true", help="Allow paid provider calls")
    parser.add_argument("--output", default="eval-results.json")
    args = parser.parse_args()
    if not args.live:
        parser.error("Pass --live to explicitly enable paid Gemini calls")
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from app import AnalyzeRequest, analyze, MODEL_NAME
    cases = json.loads(Path(__file__).with_name("cases.json").read_text())
    results = []
    for case in cases:
        try:
            result = analyze(AnalyzeRequest(content=case["content"]))
            actual = any(s.severity != "info" for s in result.signals)
            quote_matches = all(signal.quote in case["content"] for signal in result.signals)
            results.append({"id": case["id"], "passed": actual == case["expectedSignals"] and quote_matches and result.riskLevel == case["expectedRisk"],
                            "expectedRisk": case["expectedRisk"],
                            "quoteMatches": quote_matches, "result": result.model_dump(mode="json")})
        except Exception as exc:
            results.append({"id": case["id"], "passed": False, "errorType": type(exc).__name__})
    passed = sum(r["passed"] for r in results)
    Path(args.output).write_text(json.dumps({"model": MODEL_NAME, "passed": passed,
                                           "total": len(results), "results": results}, ensure_ascii=False, indent=2))
    print(f"{passed}/{len(results)} smoke cases passed; output: {args.output}")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
