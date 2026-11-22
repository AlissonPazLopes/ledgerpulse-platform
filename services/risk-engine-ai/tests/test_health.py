from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_endpoint_returns_model_loaded() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["modelLoaded"] is True
    assert body["modelVersion"] == "v1.0.0"


def test_model_info_endpoint_returns_features_and_metrics() -> None:
    response = client.get("/v1/risk/model-info")
    assert response.status_code == 200
    body = response.json()
    assert body["modelVersion"] == "v1.0.0"
    assert body["algorithm"] == "Hybrid(IsolationForest+RandomForest+Rules)"
    assert body["features"] == [
        "amount_cents",
        "velocity_1h",
        "hour_of_day",
        "account_age_days",
        "payment_method_code",
        "is_foreign_ip",
    ]
    metrics = body["metrics"]
    for key in ("precision", "recall", "f1Score", "rocAuc"):
        assert 0.0 <= metrics[key] <= 1.0


def test_metrics_endpoint_returns_prometheus_openmetrics_text() -> None:
    response = client.get("/metrics")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/plain")
    text = response.text
    assert "# TYPE risk_evaluation_total counter" in text
    assert "risk_model_loaded 1" in text
