from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

BASE_UUID_PAYMENT = "e1d2c3b4-5a6b-4c7d-8e9f-001122334455"
BASE_UUID_MERCHANT = "7b92c3e0-1f42-4876-a1e2-901283471234"


def test_evaluate_returns_approve_for_low_risk_payment() -> None:
    response = client.post(
        "/v1/risk/evaluate",
        headers={"X-Correlation-Id": "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab"},
        json={
            "payment_id": BASE_UUID_PAYMENT,
            "merchant_id": BASE_UUID_MERCHANT,
            "payer_id": "payer-low-risk",
            "amount_cents": 15000,
            "payment_method": "CARD",
            "ip_country": "BR",
            "velocity_1h": 1,
            "hour_of_day": 14,
            "account_age_days": 180,
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["decision"] == "APPROVE"
    assert 0 <= body["risk_score"] < 45
    assert body["reason_codes"] == []
    assert body["model_version"] == "v1.0.0"


def test_evaluate_returns_review_for_moderate_anomaly() -> None:
    response = client.post(
        "/v1/risk/evaluate",
        json={
            "payment_id": BASE_UUID_PAYMENT,
            "merchant_id": BASE_UUID_MERCHANT,
            "payer_id": "payer-night-review",
            "amount_cents": 95000,
            "payment_method": "PIX",
            "ip_country": "BR",
            "velocity_1h": 2,
            "hour_of_day": 2,
            "account_age_days": 90,
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["decision"] == "REVIEW"
    assert 45 <= body["risk_score"] < 75
    assert "NIGHT_ANOMALY" in body["reason_codes"]


def test_evaluate_returns_block_for_high_risk_fraud_signals() -> None:
    response = client.post(
        "/v1/risk/evaluate",
        json={
            "payment_id": BASE_UUID_PAYMENT,
            "merchant_id": BASE_UUID_MERCHANT,
            "payer_id": "payer-high-risk",
            "amount_cents": 350000,
            "payment_method": "CARD",
            "ip_country": "US",
            "velocity_1h": 9,
            "hour_of_day": 3,
            "account_age_days": 2,
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["decision"] == "BLOCK"
    assert body["risk_score"] >= 75
    assert "VELOCITY_SPIKE" in body["reason_codes"]
    assert "HIGH_TICKET_NEW_ACCOUNT" in body["reason_codes"]
    assert "NIGHT_ANOMALY" in body["reason_codes"]
    assert "FOREIGN_IP_HIGH_AMOUNT" in body["reason_codes"]


def test_evaluate_rejects_float_zero_negative_and_invalid_amount_cents() -> None:
    for invalid_amount in (15000.5, 15000.0, 0, -500, True):
        response = client.post(
            "/v1/risk/evaluate",
            json={
                "payment_id": BASE_UUID_PAYMENT,
                "merchant_id": BASE_UUID_MERCHANT,
                "payer_id": "payer-invalid",
                "amount_cents": invalid_amount,
                "payment_method": "CARD",
                "ip_country": "BR",
                "velocity_1h": 1,
                "hour_of_day": 14,
                "account_age_days": 180,
            },
        )
        assert response.status_code == 422, f"Expected 422 for amount_cents={invalid_amount!r}"
