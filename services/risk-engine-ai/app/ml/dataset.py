import numpy as np

from app.config import PAYMENT_METHOD_CODES
from app.schemas import RiskEvaluateRequest


def extract_feature_vector(request: RiskEvaluateRequest) -> np.ndarray:
    """Convert a validated request into the 6-feature integer vector expected by the ML models."""
    method_code = PAYMENT_METHOD_CODES[request.payment_method]
    is_foreign_ip = 0 if request.ip_country == "BR" else 1
    return np.array(
        [[
            int(request.amount_cents),
            int(request.velocity_1h),
            int(request.hour_of_day),
            int(request.account_age_days),
            int(method_code),
            int(is_foreign_ip),
        ]],
        dtype=np.int64,
    )


def generate_synthetic_dataset(
    n_samples: int = 4000,
    random_state: int = 42,
) -> tuple[np.ndarray, np.ndarray]:
    """Generate a deterministic synthetic payment fraud dataset with integer-cent amounts."""
    rng = np.random.default_rng(random_state)

    n_fraud = int(n_samples * 0.20)
    n_legit = n_samples - n_fraud

    # Legitimate baseline transactions
    legit_amounts = rng.integers(1_500, 95_000, size=n_legit, dtype=np.int64)
    legit_velocity = rng.choice([0, 1, 2, 3], size=n_legit, p=[0.40, 0.35, 0.18, 0.07])
    legit_hours = rng.integers(7, 23, size=n_legit, dtype=np.int64)
    legit_ages = rng.integers(25, 900, size=n_legit, dtype=np.int64)
    legit_methods = rng.choice([0, 1, 2], size=n_legit, p=[0.50, 0.38, 0.12])
    legit_foreign = rng.choice([0, 1], size=n_legit, p=[0.97, 0.03])

    x_legit = np.column_stack([
        legit_amounts,
        legit_velocity,
        legit_hours,
        legit_ages,
        legit_methods,
        legit_foreign,
    ])
    y_legit = np.zeros(n_legit, dtype=np.int64)

    # Fraud archetypes (velocity spike, high ticket new account, night anomaly, foreign IP)
    quarter = n_fraud // 4
    remainder = n_fraud - 3 * quarter

    # 1. Velocity spike
    f1 = np.column_stack([
        rng.integers(40_000, 350_000, size=quarter, dtype=np.int64),
        rng.integers(5, 16, size=quarter, dtype=np.int64),
        rng.integers(0, 24, size=quarter, dtype=np.int64),
        rng.integers(1, 120, size=quarter, dtype=np.int64),
        rng.choice([0, 1], size=quarter, p=[0.75, 0.25]),
        rng.choice([0, 1], size=quarter, p=[0.70, 0.30]),
    ])

    # 2. High ticket on brand-new account
    f2 = np.column_stack([
        rng.integers(110_000, 600_000, size=quarter, dtype=np.int64),
        rng.integers(1, 7, size=quarter, dtype=np.int64),
        rng.integers(0, 24, size=quarter, dtype=np.int64),
        rng.integers(0, 14, size=quarter, dtype=np.int64),
        rng.choice([0, 1, 2], size=quarter, p=[0.70, 0.20, 0.10]),
        rng.choice([0, 1], size=quarter, p=[0.75, 0.25]),
    ])

    # 3. Night anomaly + elevated amount
    f3 = np.column_stack([
        rng.integers(75_000, 450_000, size=quarter, dtype=np.int64),
        rng.integers(2, 9, size=quarter, dtype=np.int64),
        rng.integers(0, 6, size=quarter, dtype=np.int64),
        rng.integers(0, 45, size=quarter, dtype=np.int64),
        rng.choice([0, 1], size=quarter, p=[0.80, 0.20]),
        rng.choice([0, 1], size=quarter, p=[0.65, 0.35]),
    ])

    # 4. Foreign IP + high amount
    f4 = np.column_stack([
        rng.integers(70_000, 500_000, size=remainder, dtype=np.int64),
        rng.integers(2, 10, size=remainder, dtype=np.int64),
        rng.integers(0, 24, size=remainder, dtype=np.int64),
        rng.integers(0, 60, size=remainder, dtype=np.int64),
        np.zeros(remainder, dtype=np.int64),
        np.ones(remainder, dtype=np.int64),
    ])

    x_fraud = np.vstack([f1, f2, f3, f4])
    y_fraud = np.ones(n_fraud, dtype=np.int64)

    # Small controlled label noise (2%) for realistic validation metrics
    x_all = np.vstack([x_legit, x_fraud]).astype(np.int64)
    y_all = np.concatenate([y_legit, y_fraud])

    flip_mask = rng.random(n_samples) < 0.02
    y_all = np.where(flip_mask, 1 - y_all, y_all)

    perm = rng.permutation(n_samples)
    return x_all[perm], y_all[perm]
