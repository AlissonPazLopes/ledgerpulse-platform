#!/usr/bin/env python3
"""End-to-end verification script for the LedgerPulse payment -> ledger -> DLQ -> replay flow.

Uses only the Python standard library (compatible with Python 3.12 and 3.13).
Run `--self-test` to verify HMAC-SHA256 signing/verification, payload generation,
and CLI config parsing locally without requiring running Docker containers.
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from typing import Any

DEFAULT_CORE_URL = "http://localhost:8080"
DEFAULT_RISK_URL = "http://localhost:8001"
DEFAULT_WEBHOOK_URL = "http://localhost:8082"
DEFAULT_TIMEOUT_SECONDS = 210

DEMO_ADMIN_EMAIL = "admin@ledgerpulse.dev"
DEMO_ADMIN_PASSWORD = "LedgerPulse@2026"
DEMO_MERCHANT_ID = "7b92c3e0-1f42-4876-a1e2-901283471234"
DEMO_RECIPIENT_ACCOUNT_ID = "22222222-2222-4222-8222-222222222222"


@dataclass(frozen=True)
class E2EConfig:
    core_url: str
    risk_url: str
    webhook_url: str
    timeout_seconds: int
    self_test: bool


def parse_args(argv: list[str] | None = None) -> E2EConfig:
    parser = argparse.ArgumentParser(
        description="LedgerPulse E2E test: Payment + Split + Idempotency + Ledger + Webhook DLQ Replay",
    )
    parser.add_argument(
        "--core-url",
        default=DEFAULT_CORE_URL,
        help=f"Base URL for ledger-core-api (default: {DEFAULT_CORE_URL})",
    )
    parser.add_argument(
        "--risk-url",
        default=DEFAULT_RISK_URL,
        help=f"Base URL for risk-engine-ai (default: {DEFAULT_RISK_URL})",
    )
    parser.add_argument(
        "--webhook-url",
        default=DEFAULT_WEBHOOK_URL,
        help=f"Base URL for webhook-dispatcher (default: {DEFAULT_WEBHOOK_URL})",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=DEFAULT_TIMEOUT_SECONDS,
        help=f"Max seconds to wait for webhook delivery to reach DLQ (default: {DEFAULT_TIMEOUT_SECONDS})",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Validate HMAC-SHA256 helper, payload generation, and CLI config locally without Docker.",
    )
    args = parser.parse_args(argv)
    if args.timeout <= 0:
        parser.error("--timeout must be greater than 0")

    return E2EConfig(
        core_url=args.core_url.rstrip("/"),
        risk_url=args.risk_url.rstrip("/"),
        webhook_url=args.webhook_url.rstrip("/"),
        timeout_seconds=args.timeout,
        self_test=bool(args.self_test),
    )


def sign_webhook_payload(raw_body: str, secret: str, timestamp: int) -> str:
    """Create `X-LedgerPulse-Signature` header (`t=<epoch>,v1=<hex_hmac>`)."""
    signed_content = f"{timestamp}.{raw_body}".encode("utf-8")
    digest = hmac.new(secret.encode("utf-8"), signed_content, hashlib.sha256).hexdigest()
    return f"t={timestamp},v1={digest}"


def parse_signature_header(signature_header: str) -> tuple[int, str] | None:
    """Parse `t=<unix_epoch>,v1=<hex_hmac>` into `(timestamp, hex_digest)`."""
    if not signature_header:
        return None
    parts: dict[str, str] = {}
    for segment in signature_header.split(","):
        key, sep, value = segment.strip().partition("=")
        if not sep or not key or not value:
            return None
        parts[key] = value
    if "t" not in parts or "v1" not in parts:
        return None
    try:
        timestamp = int(parts["t"])
    except ValueError:
        return None
    digest = parts["v1"].lower()
    if len(digest) != 64:
        return None
    return timestamp, digest


def verify_webhook_signature(raw_body: str, secret: str, signature_header: str) -> bool:
    """Verify `X-LedgerPulse-Signature` using constant-time HMAC comparison."""
    parsed = parse_signature_header(signature_header)
    if parsed is None:
        return False
    timestamp, provided_digest = parsed
    expected_header = sign_webhook_payload(raw_body, secret, timestamp)
    expected_digest = expected_header.split("v1=", 1)[1]
    return hmac.compare_digest(expected_digest, provided_digest)


def build_payment_payload(
    merchant_id: str,
    recipient_account_id: str,
    amount_cents: int = 15000,
    percentage_bps: int = 2000,
    payer_id: str | None = None,
) -> dict[str, Any]:
    """Build an integer-cent payment request with percentage marketplace split."""
    if isinstance(amount_cents, bool) or not isinstance(amount_cents, int) or amount_cents <= 0:
        raise ValueError("amount_cents must be a positive integer")
    if isinstance(percentage_bps, bool) or not isinstance(percentage_bps, int) or not (1 <= percentage_bps <= 10000):
        raise ValueError("percentage_bps must be an integer between 1 and 10000")

    return {
        "merchantId": merchant_id,
        "payerId": payer_id or f"e2e-payer-{uuid.uuid4().hex[:8]}",
        "amountCents": amount_cents,
        "currency": "BRL",
        "paymentMethod": "CARD",
        "description": "E2E automated payment with split and DLQ replay",
        "splits": [
            {
                "recipientAccountId": recipient_account_id,
                "splitType": "PERCENTAGE",
                "percentageBps": percentage_bps,
            }
        ],
    }


def http_json(
    method: str,
    url: str,
    body: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 15.0,
) -> tuple[int, dict[str, str], dict[str, Any]]:
    """Perform an HTTP JSON request using urllib.request and return `(status, headers, json_body)`."""
    req_headers: dict[str, str] = {"Accept": "application/json"}
    if headers:
        req_headers.update(headers)

    data: bytes | None = None
    if body is not None:
        data = json.dumps(body, separators=(",", ":")).encode("utf-8")
        req_headers.setdefault("Content-Type", "application/json")

    request = urllib.request.Request(url=url, data=data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            parsed_headers = {k.lower(): v for k, v in response.headers.items()}
            payload = json.loads(raw) if raw else {}
            return response.status, parsed_headers, payload
    except urllib.error.HTTPError as exc:
        err_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} from {method} {url}: {err_body}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Connection failure calling {method} {url}: {exc.reason}") from exc


def run_self_test() -> int:
    print("[SELF-TEST] Validating CLI parser defaults and overrides...")
    default_cfg = parse_args([])
    assert default_cfg.core_url == DEFAULT_CORE_URL
    assert default_cfg.risk_url == DEFAULT_RISK_URL
    assert default_cfg.webhook_url == DEFAULT_WEBHOOK_URL
    assert default_cfg.timeout_seconds == DEFAULT_TIMEOUT_SECONDS
    assert not default_cfg.self_test

    custom_cfg = parse_args([
        "--core-url",
        "http://core.internal:8080/",
        "--risk-url",
        "http://risk.internal:8001/",
        "--webhook-url",
        "http://webhook.internal:8082/",
        "--timeout",
        "90",
        "--self-test",
    ])
    assert custom_cfg.core_url == "http://core.internal:8080"
    assert custom_cfg.risk_url == "http://risk.internal:8001"
    assert custom_cfg.webhook_url == "http://webhook.internal:8082"
    assert custom_cfg.timeout_seconds == 90
    assert custom_cfg.self_test

    print("[SELF-TEST] Validating integer-cent payment payload generation...")
    payload = build_payment_payload(DEMO_MERCHANT_ID, DEMO_RECIPIENT_ACCOUNT_ID)
    assert isinstance(payload["amountCents"], int) and payload["amountCents"] == 15000
    assert payload["currency"] == "BRL"
    assert len(payload["splits"]) == 1
    assert payload["splits"][0]["splitType"] == "PERCENTAGE"
    assert payload["splits"][0]["percentageBps"] == 2000

    print("[SELF-TEST] Validating HMAC-SHA256 signature helper...")
    sample_event = json.dumps(
        {
            "eventId": "c7a615f1-82b3-4e11-9c2a-5d2d1b63e701",
            "eventType": "payment.settled",
            "payload": {"amountCents": 15000},
        },
        separators=(",", ":"),
    )
    secret = "whsec_ledgerpulse_self_test_secret"
    ts = 1795430400
    sig = sign_webhook_payload(sample_event, secret, ts)
    assert sig.startswith(f"t={ts},v1=")
    assert verify_webhook_signature(sample_event, secret, sig)
    assert not verify_webhook_signature(sample_event + " ", secret, sig)
    assert not verify_webhook_signature(sample_event, "wrong_secret", sig)
    assert not verify_webhook_signature(sample_event, secret, "malformed-header")

    print("[SELF-TEST] PASS: all offline checks succeeded.")
    return 0


def check_health(cfg: E2EConfig) -> None:
    print("[1/8] Checking health endpoints across core, risk, and webhook services...")
    status, _, core_health = http_json("GET", f"{cfg.core_url}/api/v1/health")
    assert status == 200 and core_health.get("status") == "UP", f"Unexpected core health: {core_health}"

    status, _, risk_health = http_json("GET", f"{cfg.risk_url}/health")
    assert status == 200 and risk_health.get("status") == "ok", f"Unexpected risk health: {risk_health}"

    status, _, webhook_health = http_json("GET", f"{cfg.webhook_url}/api/v1/health")
    assert status == 200 and webhook_health.get("status") == "UP", f"Unexpected webhook health: {webhook_health}"


def login_admin(cfg: E2EConfig) -> str:
    print("[2/8] Authenticating operator via JWT...")
    status, _, login_body = http_json(
        "POST",
        f"{cfg.core_url}/api/v1/auth/login",
        body={"email": DEMO_ADMIN_EMAIL, "password": DEMO_ADMIN_PASSWORD},
    )
    assert status == 200
    token = login_body.get("accessToken")
    if not token:
        raise RuntimeError(f"Login succeeded without accessToken: {login_body}")
    return str(token)


def set_sink_mode(cfg: E2EConfig, token: str, mode: str) -> None:
    status, _, body = http_json(
        "PUT",
        f"{cfg.webhook_url}/api/v1/sink/mode",
        body={"mode": mode},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert status == 200 and body.get("mode") == mode, f"Failed to set sink mode={mode}: {body}"


def resolve_merchant_and_recipient(cfg: E2EConfig, token: str) -> tuple[str, str]:
    status, _, page = http_json(
        "GET",
        f"{cfg.core_url}/api/v1/accounts?accountType=RECIPIENT&size=10",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert status == 200
    for account in page.get("items", []):
        if account.get("status") == "ACTIVE" and account.get("merchantId"):
            return str(account["merchantId"]), str(account["id"])
    return DEMO_MERCHANT_ID, DEMO_RECIPIENT_ACCOUNT_ID


def create_payment_with_idempotency_check(cfg: E2EConfig, token: str) -> dict[str, Any]:
    print("[4/8] Creating payment with split + verifying Idempotency-Key replay...")
    merchant_id, recipient_id = resolve_merchant_and_recipient(cfg, token)
    payload = build_payment_payload(merchant_id=merchant_id, recipient_account_id=recipient_id)
    idem_key = f"e2e-{uuid.uuid4()}"
    correlation_id = str(uuid.uuid4())
    req_headers = {
        "Authorization": f"Bearer {token}",
        "Idempotency-Key": idem_key,
        "X-Correlation-Id": correlation_id,
    }

    status, _, payment = http_json("POST", f"{cfg.core_url}/api/v1/payments", body=payload, headers=req_headers)
    assert status == 201, f"Expected 201 on initial payment creation, got {status}"
    assert payment.get("status") == "SETTLED", f"Expected SETTLED payment, got {payment}"
    assert payment.get("ledgerTransactionId"), f"Missing ledgerTransactionId: {payment}"

    replay_status, replay_headers, replay_payment = http_json(
        "POST",
        f"{cfg.core_url}/api/v1/payments",
        body=payload,
        headers=req_headers,
    )
    assert replay_status == 201
    assert replay_headers.get("x-idempotent-replay") == "true", (
        f"Expected X-Idempotent-Replay: true header, got {replay_headers}"
    )
    assert replay_payment.get("id") == payment.get("id"), "Idempotent replay returned different payment ID"
    return payment


def verify_balanced_ledger(cfg: E2EConfig, token: str, ledger_tx_id: str, expected_amount: int) -> None:
    print(f"[5/8] Verifying balanced double-entry ledger transaction {ledger_tx_id}...")
    status, _, tx = http_json(
        "GET",
        f"{cfg.core_url}/api/v1/ledger/transactions/{ledger_tx_id}",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert status == 200
    assert tx.get("balanced") is True, f"Ledger transaction is not balanced: {tx}"

    entries = tx.get("entries", [])
    assert len(entries) >= 2, f"Expected at least 2 double-entry lines, got {entries}"

    debit_sum = sum(e["amountCents"] for e in entries if e.get("direction") == "DEBIT")
    credit_sum = sum(e["amountCents"] for e in entries if e.get("direction") == "CREDIT")
    assert debit_sum == credit_sum == expected_amount, (
        f"Unbalanced ledger legs: debit={debit_sum}, credit={credit_sum}, expected={expected_amount}"
    )


def wait_for_dlq_delivery(cfg: E2EConfig, token: str, merchant_id: str, correlation_id: str) -> str:
    print(f"[6/8] Waiting up to {cfg.timeout_seconds}s for webhook delivery to enter DLQ...")
    deadline = time.monotonic() + cfg.timeout_seconds
    query = urllib.parse.urlencode({"merchantId": merchant_id, "size": 50})
    last_seen_status: str | None = None

    while time.monotonic() < deadline:
        status, _, page = http_json(
            "GET",
            f"{cfg.webhook_url}/api/v1/webhooks/deliveries?{query}",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert status == 200
        for delivery in page.get("items", []):
            if delivery.get("correlationId") == correlation_id:
                last_seen_status = str(delivery.get("status"))
                if last_seen_status == "DLQ":
                    attempts = delivery.get("attempts", [])
                    if attempts and attempts[-1].get("signatureHeader"):
                        parsed = parse_signature_header(str(attempts[-1]["signatureHeader"]))
                        assert parsed is not None, f"Invalid signature header format: {attempts[-1]}"
                    return str(delivery["id"])
        time.sleep(3.0)

    raise TimeoutError(
        f"Timed out after {cfg.timeout_seconds}s waiting for delivery correlationId={correlation_id} "
        f"to reach DLQ (last seen status: {last_seen_status})"
    )


def replay_dlq_delivery(cfg: E2EConfig, token: str, delivery_id: str) -> None:
    print(f"[8/8] Replaying DLQ delivery {delivery_id}...")
    status, _, replay = http_json(
        "POST",
        f"{cfg.webhook_url}/api/v1/webhooks/dlq/{delivery_id}/replay",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert status == 200
    assert replay.get("status") == "DELIVERED", f"Expected DELIVERED after replay, got {replay}"
    assert replay.get("httpStatus") == 200, f"Expected httpStatus=200 after replay, got {replay}"


def run_e2e(cfg: E2EConfig) -> int:
    check_health(cfg)
    token = login_admin(cfg)

    print("[3/8] Switching Merchant Webhook Sink to FAIL_500...")
    set_sink_mode(cfg, token, "FAIL_500")

    try:
        payment = create_payment_with_idempotency_check(cfg, token)
        verify_balanced_ledger(
            cfg,
            token,
            str(payment["ledgerTransactionId"]),
            int(payment["amountCents"]),
        )
        delivery_id = wait_for_dlq_delivery(
            cfg,
            token,
            str(payment["merchantId"]),
            str(payment["correlationId"]),
        )

        print("[7/8] Switching Merchant Webhook Sink to OK_200...")
        set_sink_mode(cfg, token, "OK_200")
        replay_dlq_delivery(cfg, token, delivery_id)
    finally:
        # Best-effort reset so subsequent manual checks start from OK_200
        try:
            set_sink_mode(cfg, token, "OK_200")
        except Exception:
            pass

    print("[E2E] PASS: full payment -> split -> ledger -> DLQ -> replay flow succeeded.")
    return 0


def main(argv: list[str] | None = None) -> int:
    cfg = parse_args(argv)
    if cfg.self_test:
        return run_self_test()
    return run_e2e(cfg)


if __name__ == "__main__":
    sys.exit(main())
