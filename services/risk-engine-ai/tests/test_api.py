import re

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

EXPECTED_BUCKETS = ("0.005", "0.01", "0.025", "0.05", "0.1", "0.25", "0.5", "1.0", "+Inf")


def test_metrics_exposes_full_prometheus_histogram_and_counters_after_evaluate() -> None:
    eval_response = client.post(
        "/v1/risk/evaluate",
        json={
            "payment_id": "e1d2c3b4-5a6b-4c7d-8e9f-001122334455",
            "merchant_id": "7b92c3e0-1f42-4876-a1e2-901283471234",
            "payer_id": "payer-metrics-01",
            "amount_cents": 15000,
            "payment_method": "PIX",
            "ip_country": "BR",
            "velocity_1h": 1,
            "hour_of_day": 14,
            "account_age_days": 180,
        },
    )
    assert eval_response.status_code == 200
    decision = eval_response.json()["decision"]

    metrics_response = client.get("/metrics")
    assert metrics_response.status_code == 200
    assert metrics_response.headers["content-type"].startswith("text/plain")
    body = metrics_response.text

    assert "risk_model_loaded 1" in body
    assert "# TYPE risk_evaluation_total counter" in body
    assert f'risk_evaluation_total{{decision="{decision}"}}' in body
    assert "# TYPE risk_evaluation_latency_seconds histogram" in body

    bucket_values: list[int] = []
    for label in EXPECTED_BUCKETS:
        pattern = rf'^risk_evaluation_latency_seconds_bucket\{{le="{re.escape(label)}"\}}\s+(\d+)$'
        match = re.search(pattern, body, flags=re.MULTILINE)
        assert match is not None, f"Missing bucket le={label} in /metrics output"
        bucket_values.append(int(match.group(1)))

    # Cumulative buckets must be monotonically non-decreasing and end at total count
    assert bucket_values == sorted(bucket_values)

    count_match = re.search(
        r"^risk_evaluation_latency_seconds_count\s+(\d+)$",
        body,
        flags=re.MULTILINE,
    )
    sum_match = re.search(
        r"^risk_evaluation_latency_seconds_sum\s+([0-9]+\.[0-9]+)$",
        body,
        flags=re.MULTILINE,
    )
    assert count_match is not None
    assert sum_match is not None

    total_count = int(count_match.group(1))
    assert total_count >= 1
    assert bucket_values[-1] == total_count
