from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parent.parent
MODELS_DIR = SERVICE_ROOT / "models"
MODEL_ARTIFACT_PATH = MODELS_DIR / "risk_model_v1.joblib"
MODEL_METADATA_PATH = MODELS_DIR / "model_metadata.json"

MODEL_VERSION = "v1.0.0"
ALGORITHM_NAME = "Hybrid(IsolationForest+RandomForest+Rules)"

FEATURE_NAMES: list[str] = [
    "amount_cents",
    "velocity_1h",
    "hour_of_day",
    "account_age_days",
    "payment_method_code",
    "is_foreign_ip",
]

PAYMENT_METHOD_CODES: dict[str, int] = {
    "CARD": 0,
    "PIX": 1,
    "BOLETO": 2,
}

REVIEW_THRESHOLD = 45
BLOCK_THRESHOLD = 75
