import logging
import threading
import time

from fastapi import APIRouter, Header, Response

from app.ml.model_loader import load_model_bundle
from app.schemas import (
    ModelInfoResponse,
    RiskEvaluateRequest,
    RiskEvaluateResponse,
    RiskHealthResponse,
)
from app.scoring.engine import score_payment_risk

logger = logging.getLogger("risk-engine-ai")
router = APIRouter()

_METRICS_LOCK = threading.Lock()
_DECISION_COUNTS: dict[str, int] = {
    "APPROVE": 0,
    "REVIEW": 0,
    "BLOCK": 0,
}
_HISTOGRAM_BUCKETS: tuple[tuple[str, float], ...] = (
    ("0.005", 0.005),
    ("0.01", 0.01),
    ("0.025", 0.025),
    ("0.05", 0.05),
    ("0.1", 0.1),
    ("0.25", 0.25),
    ("0.5", 0.5),
    ("1.0", 1.0),
)
_LATENCY_BUCKET_COUNTS: dict[str, int] = {label: 0 for label, _ in _HISTOGRAM_BUCKETS}
_LATENCY_SUM_SECONDS = 0.0
_LATENCY_COUNT = 0


def _record_evaluation_metrics(decision: str, elapsed_seconds: float) -> None:
    global _LATENCY_SUM_SECONDS, _LATENCY_COUNT
    with _METRICS_LOCK:
        _DECISION_COUNTS[decision] = _DECISION_COUNTS.get(decision, 0) + 1
        _LATENCY_SUM_SECONDS += elapsed_seconds
        _LATENCY_COUNT += 1
        for label, upper_bound in _HISTOGRAM_BUCKETS:
            if elapsed_seconds <= upper_bound:
                _LATENCY_BUCKET_COUNTS[label] += 1


@router.get("/health", response_model=RiskHealthResponse, tags=["Health"])
def get_health() -> RiskHealthResponse:
    bundle = load_model_bundle()
    return RiskHealthResponse(
        status="ok",
        modelLoaded=bundle.loaded,
        modelVersion=str(bundle.metadata["modelVersion"]),
    )


@router.get("/v1/risk/model-info", response_model=ModelInfoResponse, tags=["Risk"])
def get_model_info() -> ModelInfoResponse:
    bundle = load_model_bundle()
    return ModelInfoResponse(**bundle.metadata)


@router.post("/v1/risk/evaluate", response_model=RiskEvaluateResponse, tags=["Risk"])
def evaluate_risk(
    payload: RiskEvaluateRequest,
    x_correlation_id: str | None = Header(default=None, alias="X-Correlation-Id"),
) -> RiskEvaluateResponse:
    start = time.perf_counter()
    bundle = load_model_bundle()
    result = score_payment_risk(payload, bundle)
    elapsed = time.perf_counter() - start
    _record_evaluation_metrics(result.decision, elapsed)
    logger.info(
        "Evaluated payment_id=%s decision=%s risk_score=%d correlationId=%s",
        payload.payment_id,
        result.decision,
        result.risk_score,
        x_correlation_id or "",
    )
    return result


@router.get("/metrics", tags=["Observability"])
def get_metrics() -> Response:
    bundle = load_model_bundle()
    with _METRICS_LOCK:
        counts = dict(_DECISION_COUNTS)
        bucket_counts = dict(_LATENCY_BUCKET_COUNTS)
        latency_sum = _LATENCY_SUM_SECONDS
        latency_count = _LATENCY_COUNT

    lines = [
        "# HELP risk_model_loaded Whether the ML fraud model bundle is loaded",
        "# TYPE risk_model_loaded gauge",
        f"risk_model_loaded {1 if bundle.loaded else 0}",
        "# HELP risk_evaluation_total Total risk evaluations by decision",
        "# TYPE risk_evaluation_total counter",
    ]
    for decision, count in counts.items():
        lines.append(f'risk_evaluation_total{{decision="{decision}"}} {count}')

    lines.extend([
        "# HELP risk_evaluation_latency_seconds Risk evaluation duration histogram",
        "# TYPE risk_evaluation_latency_seconds histogram",
    ])
    for label, _ in _HISTOGRAM_BUCKETS:
        lines.append(
            f'risk_evaluation_latency_seconds_bucket{{le="{label}"}} {bucket_counts[label]}'
        )
    lines.extend([
        f'risk_evaluation_latency_seconds_bucket{{le="+Inf"}} {latency_count}',
        f"risk_evaluation_latency_seconds_sum {latency_sum:.6f}",
        f"risk_evaluation_latency_seconds_count {latency_count}",
    ])
    return Response(
        content="\n".join(lines) + "\n",
        media_type="text/plain; version=0.0.4; charset=utf-8",
    )
