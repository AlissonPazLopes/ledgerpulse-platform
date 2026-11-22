from dataclasses import dataclass

from app.schemas import ReasonCode, RiskEvaluateRequest


@dataclass(frozen=True)
class DeterministicRulesResult:
    score_boost: int
    reason_codes: list[ReasonCode]


def evaluate_deterministic_rules(request: RiskEvaluateRequest) -> DeterministicRulesResult:
    """Evaluate explainable deterministic anti-fraud heuristics using integer cent thresholds."""
    boost = 0
    codes: list[ReasonCode] = []

    if request.velocity_1h >= 5:
        boost += 30
        codes.append("VELOCITY_SPIKE")

    if request.amount_cents >= 100_000 and request.account_age_days <= 14:
        boost += 30
        codes.append("HIGH_TICKET_NEW_ACCOUNT")

    if 0 <= request.hour_of_day <= 5 and request.amount_cents >= 50_000:
        boost += 22
        codes.append("NIGHT_ANOMALY")

    if request.ip_country != "BR" and request.amount_cents >= 50_000:
        boost += 25
        codes.append("FOREIGN_IP_HIGH_AMOUNT")

    return DeterministicRulesResult(score_boost=boost, reason_codes=codes)
