from app.config import BLOCK_THRESHOLD, REVIEW_THRESHOLD
from app.ml.dataset import extract_feature_vector
from app.ml.model_loader import LoadedModelBundle
from app.schemas import ReasonCode, RiskDecisionCode, RiskEvaluateRequest, RiskEvaluateResponse
from app.scoring.rules import evaluate_deterministic_rules


def score_payment_risk(
    request: RiskEvaluateRequest,
    bundle: LoadedModelBundle,
) -> RiskEvaluateResponse:
    """Combine ML signals (IsolationForest + RandomForest) and deterministic rules into 0..100."""
    features = extract_feature_vector(request)
    rf_fraud_prob = float(bundle.random_forest.predict_proba(features)[0][1])
    iso_prediction = int(bundle.isolation_forest.predict(features)[0])

    rf_points = round(rf_fraud_prob * 45)
    iso_points = 15 if iso_prediction == -1 else 0

    rules_result = evaluate_deterministic_rules(request)
    total_score = min(100, max(0, rf_points + iso_points + rules_result.score_boost))

    reason_codes: list[ReasonCode] = list(rules_result.reason_codes)
    if iso_prediction == -1:
        reason_codes.append("ML_ANOMALY_DETECTED")
    if rf_fraud_prob >= 0.60:
        reason_codes.append("ML_HIGH_FRAUD_PROBABILITY")

    decision: RiskDecisionCode
    if total_score >= BLOCK_THRESHOLD:
        decision = "BLOCK"
    elif total_score >= REVIEW_THRESHOLD:
        decision = "REVIEW"
    else:
        decision = "APPROVE"

    return RiskEvaluateResponse(
        risk_score=total_score,
        decision=decision,
        reason_codes=reason_codes,
        model_version=str(bundle.metadata["modelVersion"]),
    )
