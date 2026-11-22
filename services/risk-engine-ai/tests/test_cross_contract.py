import json
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import app
from app.schemas import RiskEvaluateRequest, RiskEvaluateResponse

CONTRACT_SAMPLE_PATH = (
    Path(__file__).resolve().parents[3]
    / "docs"
    / "contracts"
    / "contract-samples"
    / "risk-evaluate-contract.json"
)


def test_pydantic_schemas_match_frozen_cross_contract_fixture() -> None:
    assert CONTRACT_SAMPLE_PATH.exists(), f"Missing fixture at {CONTRACT_SAMPLE_PATH}"
    contract = json.loads(CONTRACT_SAMPLE_PATH.read_text(encoding="utf-8"))

    parsed_request = RiskEvaluateRequest.model_validate(contract["request"])
    assert parsed_request.amount_cents == 15000
    assert parsed_request.payment_method == "CARD"

    for key in ("approve", "review", "block"):
        parsed_resp = RiskEvaluateResponse.model_validate(contract["responses"][key])
        assert parsed_resp.decision == key.upper()

    client = TestClient(app)
    http_resp = client.post("/v1/risk/evaluate", json=contract["request"])
    assert http_resp.status_code == 200
    validated_live = RiskEvaluateResponse.model_validate(http_resp.json())
    assert validated_live.decision == "APPROVE"
