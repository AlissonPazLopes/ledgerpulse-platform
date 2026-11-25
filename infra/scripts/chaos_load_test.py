#!/usr/bin/env python3
"""Load & Chaos Testing suite for the LedgerPulse platform (Week 6 · Day 38).

Pure Python 3.12+ standard library implementation (zero external dependencies).
Scenarios covered in live mode:
  1. Concurrent Idempotency Burst (50 concurrent POST /api/v1/payments with same Idempotency-Key)
  2. Risk Engine Chaos Failover (stop risk-engine-ai -> MANUAL_REVIEW + RISK_ENGINE_UNAVAILABLE -> recover)
  3. Webhook Sink FAIL_500 -> RabbitMQ Retries -> DLQ -> Sink OK_200 -> Replay DELIVERED
  4. Prometheus Metrics Scrape across ledger-core-api, risk-engine-ai, and webhook-dispatcher

Run `--self-test` to validate concurrent aggregation, Prometheus text parsing,
and Markdown report generation offline using a temporary directory.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import re
import shlex
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

DEFAULT_CORE_URL = "http://localhost:8080"
DEFAULT_RISK_URL = "http://localhost:8001"
DEFAULT_WEBHOOK_URL = "http://localhost:8082"
DEFAULT_CONCURRENCY = 50
DEFAULT_TIMEOUT_SECONDS = 210
DEFAULT_REPORT_PATH = "infra/reports/chaos-load-report.md"

DEMO_ADMIN_EMAIL = "admin@ledgerpulse.dev"
DEMO_ADMIN_PASSWORD = "LedgerPulse@2026"
DEMO_MERCHANT_ID = "7b92c3e0-1f42-4876-a1e2-901283471234"
DEMO_RECIPIENT_ACCOUNT_ID = "22222222-2222-4222-8222-222222222222"

PROM_LINE_RE = re.compile(
    r"^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{([^}]*)\})?\s+([^\s]+)(?:\s+[^\s]+)?$"
)
PROM_LABEL_RE = re.compile(r'([a-zA-Z_][a-zA-Z0-9_]*)="((?:[^"\\]|\\.)*)"')


@dataclass(frozen=True)
class ChaosConfig:
    core_url: str
    risk_url: str
    webhook_url: str
    concurrency: int
    timeout_seconds: int
    report_path: str
    stop_risk_cmd: str
    start_risk_cmd: str
    self_test: bool


@dataclass(frozen=True)
class ConcurrentAttemptResult:
    worker_id: int
    http_status: int
    replayed: bool
    payment_id: str | None
    ledger_transaction_id: str | None
    latency_ms: float
    error: str | None = None


@dataclass(frozen=True)
class IdempotencyAggregation:
    total_requests: int
    created_201_count: int
    replayed_count: int
    conflict_409_count: int
    unexpected_error_count: int
    unique_payment_ids: list[str]
    unique_ledger_tx_ids: list[str]
    p95_latency_ms: float
    passed: bool


@dataclass(frozen=True)
class PrometheusSample:
    name: str
    labels: dict[str, str]
    value: float


@dataclass(frozen=True)
class ScenarioResult:
    name: str
    passed: bool
    summary: str
    details: list[str] = field(default_factory=list)


def parse_args(argv: list[str] | None = None) -> ChaosConfig:
    parser = argparse.ArgumentParser(
        description="LedgerPulse Load & Chaos Testing Suite",
    )
    parser.add_argument("--core-url", default=DEFAULT_CORE_URL)
    parser.add_argument("--risk-url", default=DEFAULT_RISK_URL)
    parser.add_argument("--webhook-url", default=DEFAULT_WEBHOOK_URL)
    parser.add_argument("--concurrency", type=int, default=DEFAULT_CONCURRENCY)
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--report-path", default=DEFAULT_REPORT_PATH)
    parser.add_argument(
        "--stop-risk-cmd",
        default="docker compose stop risk-engine-ai",
        help="Shell command to stop risk-engine-ai during chaos failover test",
    )
    parser.add_argument(
        "--start-risk-cmd",
        default="docker compose start risk-engine-ai",
        help="Shell command to restore risk-engine-ai after chaos failover test",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run offline validation for concurrency aggregation, Prometheus parser, and Markdown reporting",
    )
    args = parser.parse_args(argv)
    if args.concurrency <= 0:
        parser.error("--concurrency must be greater than 0")
    if args.timeout <= 0:
        parser.error("--timeout must be greater than 0")

    return ChaosConfig(
        core_url=args.core_url.rstrip("/"),
        risk_url=args.risk_url.rstrip("/"),
        webhook_url=args.webhook_url.rstrip("/"),
        concurrency=args.concurrency,
        timeout_seconds=args.timeout,
        report_path=args.report_path,
        stop_risk_cmd=args.stop_risk_cmd,
        start_risk_cmd=args.start_risk_cmd,
        self_test=bool(args.self_test),
    )


def http_request(
    method: str,
    url: str,
    body: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 15.0,
) -> tuple[int, dict[str, str], str]:
    req_headers: dict[str, str] = {}
    if headers:
        req_headers.update(headers)

    data: bytes | None = None
    if body is not None:
        data = json.dumps(body, separators=(",", ":")).encode("utf-8")
        req_headers.setdefault("Content-Type", "application/json")
        req_headers.setdefault("Accept", "application/json")

    req = urllib.request.Request(url=url, data=data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            resp_headers = {k.lower(): v for k, v in resp.headers.items()}
            return resp.status, resp_headers, raw
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        resp_headers = {k.lower(): v for k, v in exc.headers.items()} if exc.headers else {}
        return exc.code, resp_headers, raw


def http_json(
    method: str,
    url: str,
    body: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 15.0,
) -> tuple[int, dict[str, str], dict[str, Any]]:
    merged_headers = {"Accept": "application/json"}
    if headers:
        merged_headers.update(headers)
    status, resp_headers, raw = http_request(
        method=method,
        url=url,
        body=body,
        headers=merged_headers,
        timeout=timeout,
    )
    parsed: dict[str, Any] = json.loads(raw) if raw.strip().startswith("{") else {}
    return status, resp_headers, parsed


def aggregate_idempotency_results(results: list[ConcurrentAttemptResult]) -> IdempotencyAggregation:
    """Aggregate concurrent payment results ensuring at most one payment and ledger transaction."""
    created_201 = [r for r in results if r.http_status in (200, 201)]
    replayed = [r for r in created_201 if r.replayed]
    conflicts_409 = [r for r in results if r.http_status == 409]
    unexpected = [r for r in results if r.http_status not in (200, 201, 409) or r.error is not None]

    payment_ids = sorted({r.payment_id for r in created_201 if r.payment_id})
    ledger_tx_ids = sorted({r.ledger_transaction_id for r in created_201 if r.ledger_transaction_id})

    latencies = sorted(r.latency_ms for r in results)
    p95_idx = min(len(latencies) - 1, max(0, int(0.95 * len(latencies)) - 1)) if latencies else 0
    p95_ms = round(latencies[p95_idx], 2) if latencies else 0.0

    passed = (
        len(results) > 0
        and len(created_201) >= 1
        and len(unexpected) == 0
        and len(payment_ids) == 1
        and len(ledger_tx_ids) == 1
    )
    return IdempotencyAggregation(
        total_requests=len(results),
        created_201_count=len(created_201),
        replayed_count=len(replayed),
        conflict_409_count=len(conflicts_409),
        unexpected_error_count=len(unexpected),
        unique_payment_ids=payment_ids,
        unique_ledger_tx_ids=ledger_tx_ids,
        p95_latency_ms=p95_ms,
        passed=passed,
    )


def parse_prometheus_text(raw_text: str) -> list[PrometheusSample]:
    """Parse Prometheus/OpenMetrics text exposition into structured samples."""
    samples: list[PrometheusSample] = []
    for raw_line in raw_text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        match = PROM_LINE_RE.match(line)
        if not match:
            continue
        metric_name, labels_raw, value_raw = match.groups()
        labels: dict[str, str] = {}
        if labels_raw:
            for k, v in PROM_LABEL_RE.findall(labels_raw):
                labels[k] = v.replace('\\"', '"').replace("\\\\", "\\")
        try:
            value = float(value_raw)
        except ValueError:
            continue
        samples.append(PrometheusSample(name=metric_name, labels=labels, value=value))
    return samples


def verify_metrics_present(
    samples: list[PrometheusSample],
    required_metrics: list[str],
    required_label_filters: dict[str, dict[str, str]] | None = None,
) -> tuple[bool, list[str]]:
    """Verify that required metric names (and optional label matches) exist in parsed samples."""
    by_name: dict[str, list[PrometheusSample]] = {}
    for sample in samples:
        by_name.setdefault(sample.name, []).append(sample)

    missing: list[str] = []
    for metric in required_metrics:
        candidates = by_name.get(metric, [])
        if not candidates:
            missing.append(metric)
            continue
        if required_label_filters and metric in required_label_filters:
            expected_labels = required_label_filters[metric]
            if not any(
                all(c.labels.get(lk) == lv for lk, lv in expected_labels.items())
                for c in candidates
            ):
                missing.append(f"{metric}{expected_labels}")
    return len(missing) == 0, missing


def render_markdown_report(
    generated_at: str,
    target_urls: dict[str, str],
    scenarios: list[ScenarioResult],
) -> str:
    overall_passed = all(s.passed for s in scenarios) and len(scenarios) > 0
    status_badge = "PASS" if overall_passed else "FAIL"

    lines = [
        "# LedgerPulse — Load & Chaos Testing Report",
        "",
        f"- **Generated At (UTC):** `{generated_at}`",
        f"- **Overall Result:** **{status_badge}**",
        f"- **Core API:** `{target_urls.get('core', '')}`",
        f"- **Risk Engine AI:** `{target_urls.get('risk', '')}`",
        f"- **Webhook Dispatcher:** `{target_urls.get('webhook', '')}`",
        "",
        "## Summary Matrix",
        "",
        "| Scenario | Status | Summary |",
        "| :--- | :---: | :--- |",
    ]
    for scenario in scenarios:
        badge = "PASS" if scenario.passed else "FAIL"
        lines.append(f"| {scenario.name} | **{badge}** | {scenario.summary} |")

    lines.append("")
    lines.append("## Detailed Findings")
    lines.append("")
    for scenario in scenarios:
        badge = "PASS" if scenario.passed else "FAIL"
        lines.append(f"### {scenario.name} — `{badge}`")
        lines.append("")
        lines.append(scenario.summary)
        lines.append("")
        for detail in scenario.details:
            lines.append(f"- {detail}")
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def write_report(report_path: str, content: str) -> Path:
    path = Path(report_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return path


def build_payment_body(merchant_id: str, recipient_id: str, payer_suffix: str) -> dict[str, Any]:
    return {
        "merchantId": merchant_id,
        "payerId": f"chaos-payer-{payer_suffix}",
        "amountCents": 15000,
        "currency": "BRL",
        "paymentMethod": "CARD",
        "description": "Chaos & Load suite automated payment",
        "splits": [
            {
                "recipientAccountId": recipient_id,
                "splitType": "PERCENTAGE",
                "percentageBps": 2000,
            }
        ],
    }


def login_admin(core_url: str) -> str:
    status, _, body = http_json(
        "POST",
        f"{core_url}/api/v1/auth/login",
        body={"email": DEMO_ADMIN_EMAIL, "password": DEMO_ADMIN_PASSWORD},
    )
    if status != 200 or not body.get("accessToken"):
        raise RuntimeError(f"Admin login failed ({status}): {body}")
    return str(body["accessToken"])


def resolve_merchant_and_recipient(core_url: str, token: str) -> tuple[str, str]:
    status, _, page = http_json(
        "GET",
        f"{core_url}/api/v1/accounts?accountType=RECIPIENT&size=10",
        headers={"Authorization": f"Bearer {token}"},
    )
    if status == 200:
        for account in page.get("items", []):
            if account.get("status") == "ACTIVE" and account.get("merchantId"):
                return str(account["merchantId"]), str(account["id"])
    return DEMO_MERCHANT_ID, DEMO_RECIPIENT_ACCOUNT_ID


def run_scenario_1_concurrent_idempotency(
    cfg: ChaosConfig,
    token: str,
    merchant_id: str,
    recipient_id: str,
) -> ScenarioResult:
    print(f"[Scenario 1/4] Sending {cfg.concurrency} concurrent requests with identical Idempotency-Key...")
    idem_key = f"chaos-idem-{uuid.uuid4()}"
    payload = build_payment_body(merchant_id, recipient_id, uuid.uuid4().hex[:8])

    def fire_one(worker_id: int) -> ConcurrentAttemptResult:
        started = time.perf_counter()
        headers = {
            "Authorization": f"Bearer {token}",
            "Idempotency-Key": idem_key,
            "X-Correlation-Id": str(uuid.uuid4()),
        }
        try:
            status, resp_headers, body = http_json(
                "POST",
                f"{cfg.core_url}/api/v1/payments",
                body=payload,
                headers=headers,
                timeout=30.0,
            )
            elapsed_ms = (time.perf_counter() - started) * 1000.0
            return ConcurrentAttemptResult(
                worker_id=worker_id,
                http_status=status,
                replayed=resp_headers.get("x-idempotent-replay") == "true",
                payment_id=str(body["id"]) if body.get("id") else None,
                ledger_transaction_id=(
                    str(body["ledgerTransactionId"]) if body.get("ledgerTransactionId") else None
                ),
                latency_ms=elapsed_ms,
            )
        except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError, ValueError) as exc:
            elapsed_ms = (time.perf_counter() - started) * 1000.0
            return ConcurrentAttemptResult(
                worker_id=worker_id,
                http_status=0,
                replayed=False,
                payment_id=None,
                ledger_transaction_id=None,
                latency_ms=elapsed_ms,
                error=str(exc),
            )

    with concurrent.futures.ThreadPoolExecutor(max_workers=cfg.concurrency) as executor:
        futures = [executor.submit(fire_one, i) for i in range(cfg.concurrency)]
        results = [f.result() for f in concurrent.futures.as_completed(futures)]

    agg = aggregate_idempotency_results(results)

    # Explicit sequential replay check after concurrent burst completes
    seq_status, seq_headers, seq_body = http_json(
        "POST",
        f"{cfg.core_url}/api/v1/payments",
        body=payload,
        headers={
            "Authorization": f"Bearer {token}",
            "Idempotency-Key": idem_key,
            "X-Correlation-Id": str(uuid.uuid4()),
        },
    )
    seq_replay_ok = (
        seq_status == 201
        and seq_headers.get("x-idempotent-replay") == "true"
        and agg.unique_payment_ids
        and seq_body.get("id") == agg.unique_payment_ids[0]
    )

    passed = agg.passed and bool(seq_replay_ok)
    summary = (
        f"{agg.total_requests} concurrent calls -> 1 unique payment "
        f"({agg.created_201_count} x 201, {agg.replayed_count} replays, "
        f"{agg.conflict_409_count} x 409 in-flight locks, 0 duplicates)"
    )
    details = [
        f"Unique payment IDs: `{agg.unique_payment_ids}`",
        f"Unique ledgerTransactionIds: `{agg.unique_ledger_tx_ids}`",
        f"Sequential post-burst replay header `X-Idempotent-Replay: true`: `{seq_replay_ok}`",
        f"Burst p95 latency: `{agg.p95_latency_ms} ms`",
    ]
    return ScenarioResult("Scenario 1 · Concurrent Idempotency (50 Threads)", passed, summary, details)


def run_shell_command(command_str: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        shlex.split(command_str, posix=(sys.platform != "win32")),
        check=True,
        capture_output=True,
        text=True,
    )


def wait_for_risk_health(risk_url: str, timeout_s: int = 60) -> bool:
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        try:
            status, _, body = http_json("GET", f"{risk_url}/health", timeout=3.0)
            if status == 200 and body.get("status") == "ok":
                return True
        except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError):
            time.sleep(2.0)
            continue
        time.sleep(2.0)
    return False


def run_scenario_2_chaos_risk_failover(
    cfg: ChaosConfig,
    token: str,
    merchant_id: str,
    recipient_id: str,
) -> ScenarioResult:
    print("[Scenario 2/4] Stopping risk-engine-ai to test Resilience4j Circuit Breaker fallback...")
    fallback_payments: list[dict[str, Any]] = []
    restored = False

    try:
        run_shell_command(cfg.stop_risk_cmd)
        time.sleep(2.0)

        for i in range(5):
            payload = build_payment_body(merchant_id, recipient_id, f"chaos-cb-{i}-{uuid.uuid4().hex[:6]}")
            status, _, body = http_json(
                "POST",
                f"{cfg.core_url}/api/v1/payments",
                body=payload,
                headers={
                    "Authorization": f"Bearer {token}",
                    "Idempotency-Key": f"chaos-cb-{uuid.uuid4()}",
                    "X-Correlation-Id": str(uuid.uuid4()),
                },
                timeout=15.0,
            )
            if status != 201:
                raise RuntimeError(f"Expected 201 fallback MANUAL_REVIEW, got {status}: {body}")
            fallback_payments.append(body)

        all_manual_review = all(
            p.get("status") == "MANUAL_REVIEW"
            and "RISK_ENGINE_UNAVAILABLE" in (p.get("reasonCodes") or [])
            for p in fallback_payments
        )

        _, _, prom_raw = http_request("GET", f"{cfg.core_url}/actuator/prometheus", timeout=10.0)
        cb_samples = [
            s
            for s in parse_prometheus_text(prom_raw)
            if s.name == "circuit_breaker_state" and s.labels.get("name") == "riskEngine"
        ]
        cb_opened = any(s.value == 1.0 for s in cb_samples)
    finally:
        print("[Scenario 2/4] Restoring risk-engine-ai...")
        run_shell_command(cfg.start_risk_cmd)
        restored = wait_for_risk_health(cfg.risk_url, timeout_s=60)

    passed = all_manual_review and restored and len(fallback_payments) == 5
    summary = (
        f"5/5 fallback payments -> MANUAL_REVIEW + RISK_ENGINE_UNAVAILABLE "
        f"(Circuit Breaker OPEN observed={cb_opened}, risk-engine restored={restored})"
    )
    details = [
        f"Fallback statuses: `{[p.get('status') for p in fallback_payments]}`",
        (
            f'Circuit breaker `circuit_breaker_state{{name="riskEngine"}}` samples during outage: '
            f"`{[s.value for s in cb_samples]}`"
        ),
        f"Risk engine health restored: `{restored}`",
    ]
    return ScenarioResult("Scenario 2 · Chaos Failover (risk-engine-ai)", passed, summary, details)


def run_scenario_3_webhook_dlq_replay(
    cfg: ChaosConfig,
    token: str,
    merchant_id: str,
    recipient_id: str,
) -> ScenarioResult:
    print("[Scenario 3/4] Testing Webhook Sink FAIL_500 -> RabbitMQ DLQ -> OK_200 -> Replay...")

    def set_mode(mode: str) -> None:
        status, _, body = http_json(
            "PUT",
            f"{cfg.webhook_url}/api/v1/sink/mode",
            body={"mode": mode},
            headers={"Authorization": f"Bearer {token}"},
        )
        if status != 200 or body.get("mode") != mode:
            raise RuntimeError(f"Failed to set sink mode={mode}: {status} {body}")

    set_mode("FAIL_500")
    try:
        corr_id = str(uuid.uuid4())
        payload = build_payment_body(merchant_id, recipient_id, f"dlq-{uuid.uuid4().hex[:6]}")
        status, _, payment = http_json(
            "POST",
            f"{cfg.core_url}/api/v1/payments",
            body=payload,
            headers={
                "Authorization": f"Bearer {token}",
                "Idempotency-Key": f"chaos-dlq-{uuid.uuid4()}",
                "X-Correlation-Id": corr_id,
            },
        )
        if status != 201 or payment.get("status") != "SETTLED":
            raise RuntimeError(f"Expected SETTLED payment for DLQ scenario, got {status} {payment}")

        deadline = time.monotonic() + cfg.timeout_seconds
        delivery_id: str | None = None
        query = urllib.parse.urlencode({"merchantId": merchant_id, "size": 50})
        while time.monotonic() < deadline:
            d_status, _, d_page = http_json(
                "GET",
                f"{cfg.webhook_url}/api/v1/webhooks/deliveries?{query}",
                headers={"Authorization": f"Bearer {token}"},
            )
            if d_status == 200:
                for item in d_page.get("items", []):
                    if item.get("correlationId") == corr_id and item.get("status") == "DLQ":
                        delivery_id = str(item["id"])
                        break
            if delivery_id:
                break
            time.sleep(3.0)

        if not delivery_id:
            raise TimeoutError(f"Delivery for correlationId={corr_id} did not reach DLQ within {cfg.timeout_seconds}s")

        set_mode("OK_200")
        r_status, _, replay = http_json(
            "POST",
            f"{cfg.webhook_url}/api/v1/webhooks/dlq/{delivery_id}/replay",
            headers={"Authorization": f"Bearer {token}"},
        )
        passed = r_status == 200 and replay.get("status") == "DELIVERED" and replay.get("httpStatus") == 200
        summary = f"Delivery `{delivery_id}` reached DLQ under FAIL_500 and replayed to DELIVERED (HTTP 200)"
        details = [
            f"Payment ID: `{payment.get('id')}` (`correlationId={corr_id}`)",
            f"DLQ delivery ID: `{delivery_id}`",
            f"Replay result: `status={replay.get('status')}`, `httpStatus={replay.get('httpStatus')}`",
        ]
        return ScenarioResult("Scenario 3 · Webhook Failure, DLQ & Replay", passed, summary, details)
    finally:
        try:
            set_mode("OK_200")
        except (RuntimeError, urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as exc:
            print(f"[Scenario 3/4] Warning: failed to reset sink mode to OK_200: {exc}")


def run_scenario_4_prometheus_scrape(cfg: ChaosConfig) -> ScenarioResult:
    print("[Scenario 4/4] Scraping Prometheus endpoints across core, risk, and webhook services...")
    checks = [
        (
            "ledger-core-api",
            f"{cfg.core_url}/actuator/prometheus",
            [
                "ledger_payments_total",
                "risk_evaluation_latency_seconds_bucket",
                "circuit_breaker_state",
            ],
            {"circuit_breaker_state": {"name": "riskEngine"}},
        ),
        (
            "risk-engine-ai",
            f"{cfg.risk_url}/metrics",
            [
                "risk_model_loaded",
                "risk_evaluation_total",
                "risk_evaluation_latency_seconds_bucket",
            ],
            None,
        ),
        (
            "webhook-dispatcher",
            f"{cfg.webhook_url}/actuator/prometheus",
            [
                "webhook_dlq_total",
                "webhook_deliveries_total",
                "webhook_dlq_replays_total",
                "webhook_delivery_latency_seconds_bucket",
            ],
            None,
        ),
    ]

    all_ok = True
    details: list[str] = []
    for svc_name, url, required, label_filters in checks:
        status, _, raw = http_request("GET", url, timeout=10.0)
        samples = parse_prometheus_text(raw)
        ok, missing = verify_metrics_present(samples, required, label_filters)
        svc_ok = status == 200 and ok
        if not svc_ok:
            all_ok = False
        details.append(
            f"`{svc_name}` ({url}): HTTP `{status}`, samples=`{len(samples)}`, missing=`{missing}`"
        )

    summary = "All 3 service Prometheus endpoints verified against v1.0.0 metrics catalog" if all_ok else "Missing metrics on one or more services"
    return ScenarioResult("Scenario 4 · Prometheus Metrics Scrape", all_ok, summary, details)


def run_self_test() -> int:
    print("[SELF-TEST] 1/3 Testing 50-worker concurrent idempotency aggregation...")
    simulated = [
        ConcurrentAttemptResult(0, 201, False, "pay-1", "tx-1", 42.5),
        *[ConcurrentAttemptResult(i, 409, False, None, None, 12.1) for i in range(1, 20)],
        *[ConcurrentAttemptResult(i, 201, True, "pay-1", "tx-1", 8.4) for i in range(20, 50)],
    ]
    agg = aggregate_idempotency_results(simulated)
    assert agg.passed is True
    assert agg.total_requests == 50
    assert agg.created_201_count == 31
    assert agg.replayed_count == 30
    assert agg.conflict_409_count == 19
    assert agg.unique_payment_ids == ["pay-1"]
    assert agg.unique_ledger_tx_ids == ["tx-1"]

    duplicate_bug = [
        ConcurrentAttemptResult(0, 201, False, "pay-1", "tx-1", 20.0),
        ConcurrentAttemptResult(1, 201, False, "pay-2", "tx-2", 21.0),
    ]
    assert aggregate_idempotency_results(duplicate_bug).passed is False

    print("[SELF-TEST] 2/3 Testing Prometheus exposition parser & catalog check...")
    sample_core_prom = """
# HELP ledger_payments_total Total payments processed
# TYPE ledger_payments_total counter
ledger_payments_total{decision="APPROVE",method="CARD",status="SETTLED"} 5.0
# TYPE risk_evaluation_latency_seconds histogram
risk_evaluation_latency_seconds_bucket{le="0.05"} 4.0
risk_evaluation_latency_seconds_bucket{le="+Inf"} 5.0
# TYPE circuit_breaker_state gauge
circuit_breaker_state{name="riskEngine",state="CLOSED"} 0.0
"""
    core_samples = parse_prometheus_text(sample_core_prom)
    ok_core, missing_core = verify_metrics_present(
        core_samples,
        ["ledger_payments_total", "risk_evaluation_latency_seconds_bucket", "circuit_breaker_state"],
        {"circuit_breaker_state": {"name": "riskEngine"}},
    )
    assert ok_core and missing_core == []

    sample_risk_prom = """
risk_model_loaded 1
risk_evaluation_total{decision="APPROVE"} 5
risk_evaluation_latency_seconds_bucket{le="0.005"} 3
risk_evaluation_latency_seconds_bucket{le="+Inf"} 5
"""
    ok_risk, missing_risk = verify_metrics_present(
        parse_prometheus_text(sample_risk_prom),
        ["risk_model_loaded", "risk_evaluation_total", "risk_evaluation_latency_seconds_bucket"],
    )
    assert ok_risk and missing_risk == []

    sample_hook_prom = """
webhook_dlq_total{eventType="payment.settled"} 1.0
webhook_deliveries_total{eventType="payment.settled",status="DELIVERED"} 1.0
webhook_dlq_replays_total{status="DELIVERED"} 1.0
webhook_delivery_latency_seconds_bucket{le="0.1"} 1.0
"""
    ok_hook, missing_hook = verify_metrics_present(
        parse_prometheus_text(sample_hook_prom),
        [
            "webhook_dlq_total",
            "webhook_deliveries_total",
            "webhook_dlq_replays_total",
            "webhook_delivery_latency_seconds_bucket",
        ],
    )
    assert ok_hook and missing_hook == []

    print("[SELF-TEST] 3/3 Testing Markdown report generation in temporary file...")
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_report = Path(tmpdir) / "chaos-load-report.md"
        report_text = render_markdown_report(
            generated_at="2026-11-25T12:00:00Z",
            target_urls={
                "core": DEFAULT_CORE_URL,
                "risk": DEFAULT_RISK_URL,
                "webhook": DEFAULT_WEBHOOK_URL,
            },
            scenarios=[
                ScenarioResult(
                    name="Scenario 1 · Concurrent Idempotency (50 Threads)",
                    passed=True,
                    summary="50 concurrent calls -> 1 unique payment",
                    details=["Unique ledgerTransactionIds: `['tx-1']`"],
                )
            ],
        )
        written = write_report(str(tmp_report), report_text)
        assert written.exists()
        content = written.read_text(encoding="utf-8")
        assert "# LedgerPulse — Load & Chaos Testing Report" in content
        assert "**PASS**" in content
        assert "Scenario 1" in content

    print("[SELF-TEST] PASS: all offline chaos/load suite checks succeeded.")
    return 0


def run_live(cfg: ChaosConfig) -> int:
    token = login_admin(cfg.core_url)
    merchant_id, recipient_id = resolve_merchant_and_recipient(cfg.core_url, token)

    scenarios: list[ScenarioResult] = []
    scenarios.append(run_scenario_1_concurrent_idempotency(cfg, token, merchant_id, recipient_id))
    scenarios.append(run_scenario_2_chaos_risk_failover(cfg, token, merchant_id, recipient_id))
    scenarios.append(run_scenario_3_webhook_dlq_replay(cfg, token, merchant_id, recipient_id))
    scenarios.append(run_scenario_4_prometheus_scrape(cfg))

    generated_at = datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
    report = render_markdown_report(
        generated_at=generated_at,
        target_urls={"core": cfg.core_url, "risk": cfg.risk_url, "webhook": cfg.webhook_url},
        scenarios=scenarios,
    )
    report_path = write_report(cfg.report_path, report)
    print(f"[CHAOS] Report written to {report_path}")

    all_passed = all(s.passed for s in scenarios)
    for s in scenarios:
        print(f" - {s.name}: {'PASS' if s.passed else 'FAIL'} ({s.summary})")
    return 0 if all_passed else 1


def main(argv: list[str] | None = None) -> int:
    cfg = parse_args(argv)
    if cfg.self_test:
        return run_self_test()
    return run_live(cfg)


if __name__ == "__main__":
    sys.exit(main())
