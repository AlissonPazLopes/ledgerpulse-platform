from typing import Annotated, Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator

PaymentMethodCode = Literal["CARD", "PIX", "BOLETO"]
RiskDecisionCode = Literal["APPROVE", "REVIEW", "BLOCK"]
ReasonCode = Literal[
    "VELOCITY_SPIKE",
    "HIGH_TICKET_NEW_ACCOUNT",
    "NIGHT_ANOMALY",
    "FOREIGN_IP_HIGH_AMOUNT",
    "ML_ANOMALY_DETECTED",
    "ML_HIGH_FRAUD_PROBABILITY",
]


def _require_strict_int(value: Any, field_name: str) -> int:
    if isinstance(value, bool) or isinstance(value, float) or not isinstance(value, int):
        raise ValueError(f"{field_name} must be an integer (float/bool/str are not allowed)")
    return value


class RiskHealthResponse(BaseModel):
    status: str
    modelLoaded: bool
    modelVersion: str


class ModelMetrics(BaseModel):
    precision: float = Field(ge=0.0, le=1.0)
    recall: float = Field(ge=0.0, le=1.0)
    f1Score: float = Field(ge=0.0, le=1.0)
    rocAuc: float = Field(ge=0.0, le=1.0)


class ModelInfoResponse(BaseModel):
    modelVersion: str
    algorithm: str
    features: list[str]
    metrics: ModelMetrics
    trainedAt: str


class RiskEvaluateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    payment_id: UUID
    merchant_id: UUID
    payer_id: Annotated[str, Field(min_length=1, max_length=120)]
    amount_cents: Annotated[int, Field(gt=0)]
    payment_method: PaymentMethodCode
    ip_country: Annotated[str, Field(pattern=r"^[A-Z]{2}$")]
    velocity_1h: Annotated[int, Field(ge=0)]
    hour_of_day: Annotated[int, Field(ge=0, le=23)]
    account_age_days: Annotated[int, Field(ge=0)]

    @field_validator("amount_cents", mode="before")
    @classmethod
    def validate_amount_cents_int(cls, value: Any) -> int:
        return _require_strict_int(value, "amount_cents")

    @field_validator("velocity_1h", mode="before")
    @classmethod
    def validate_velocity_int(cls, value: Any) -> int:
        return _require_strict_int(value, "velocity_1h")

    @field_validator("hour_of_day", mode="before")
    @classmethod
    def validate_hour_int(cls, value: Any) -> int:
        return _require_strict_int(value, "hour_of_day")

    @field_validator("account_age_days", mode="before")
    @classmethod
    def validate_account_age_int(cls, value: Any) -> int:
        return _require_strict_int(value, "account_age_days")

    @field_validator("payer_id")
    @classmethod
    def validate_payer_id_not_blank(cls, value: str) -> str:
        trimmed = value.strip()
        if not trimmed:
            raise ValueError("payer_id must not be blank")
        return trimmed


class RiskEvaluateResponse(BaseModel):
    risk_score: Annotated[int, Field(ge=0, le=100)]
    decision: RiskDecisionCode
    reason_codes: list[ReasonCode]
    model_version: str
