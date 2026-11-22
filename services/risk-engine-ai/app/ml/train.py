import json
from datetime import datetime, timezone
from pathlib import Path

import joblib
from sklearn.ensemble import IsolationForest, RandomForestClassifier
from sklearn.metrics import f1_score, precision_score, recall_score, roc_auc_score
from sklearn.model_selection import train_test_split

from app.config import (
    ALGORITHM_NAME,
    FEATURE_NAMES,
    MODEL_ARTIFACT_PATH,
    MODEL_METADATA_PATH,
    MODEL_VERSION,
)
from app.ml.dataset import generate_synthetic_dataset


def train_and_save_model(
    artifact_path: Path = MODEL_ARTIFACT_PATH,
    metadata_path: Path = MODEL_METADATA_PATH,
    n_samples: int = 4000,
    random_state: int = 42,
) -> dict:
    """Train IsolationForest + RandomForestClassifier and persist compact .joblib + metadata."""
    x_data, y_data = generate_synthetic_dataset(n_samples=n_samples, random_state=random_state)
    x_train, x_test, y_train, y_test = train_test_split(
        x_data,
        y_data,
        test_size=0.25,
        stratify=y_data,
        random_state=random_state,
    )

    # Fit IsolationForest on legitimate baseline to detect out-of-distribution payment vectors
    x_legit_train = x_train[y_train == 0]
    isolation_forest = IsolationForest(
        n_estimators=40,
        contamination=0.05,
        random_state=random_state,
    )
    isolation_forest.fit(x_legit_train)

    random_forest = RandomForestClassifier(
        n_estimators=40,
        max_depth=8,
        random_state=random_state,
    )
    random_forest.fit(x_train, y_train)

    y_pred = random_forest.predict(x_test)
    y_proba = random_forest.predict_proba(x_test)[:, 1]

    metrics = {
        "precision": round(float(precision_score(y_test, y_pred)), 4),
        "recall": round(float(recall_score(y_test, y_pred)), 4),
        "f1Score": round(float(f1_score(y_test, y_pred)), 4),
        "rocAuc": round(float(roc_auc_score(y_test, y_proba)), 4),
    }

    metadata = {
        "modelVersion": MODEL_VERSION,
        "algorithm": ALGORITHM_NAME,
        "features": FEATURE_NAMES,
        "metrics": metrics,
        "trainedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }

    artifact_path.parent.mkdir(parents=True, exist_ok=True)
    metadata_path.parent.mkdir(parents=True, exist_ok=True)

    bundle = {
        "isolation_forest": isolation_forest,
        "random_forest": random_forest,
        "feature_names": FEATURE_NAMES,
        "model_version": MODEL_VERSION,
    }
    joblib.dump(bundle, artifact_path, compress=3)
    metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    return metadata


if __name__ == "__main__":
    trained_metadata = train_and_save_model()
    size_kb = MODEL_ARTIFACT_PATH.stat().st_size / 1024.0
    print(
        f"Trained {trained_metadata['modelVersion']} ({size_kb:.1f} KB) - "
        f"metrics={trained_metadata['metrics']}"
    )
