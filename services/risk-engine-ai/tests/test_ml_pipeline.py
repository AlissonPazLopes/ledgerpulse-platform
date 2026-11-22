from pathlib import Path

import joblib
import numpy as np

from app.ml.dataset import generate_synthetic_dataset
from app.ml.train import train_and_save_model


def test_dataset_generation_is_deterministic() -> None:
    x1, y1 = generate_synthetic_dataset(n_samples=500, random_state=42)
    x2, y2 = generate_synthetic_dataset(n_samples=500, random_state=42)

    assert x1.shape == (500, 6)
    assert y1.shape == (500,)
    assert np.issubdtype(x1.dtype, np.integer)
    assert np.array_equal(x1, x2)
    assert np.array_equal(y1, y2)
    assert set(np.unique(y1)) == {0, 1}


def test_train_and_serialize_compact_joblib_artifact(tmp_path: Path) -> None:
    artifact_path = tmp_path / "risk_model_v1.joblib"
    metadata_path = tmp_path / "model_metadata.json"

    metadata = train_and_save_model(
        artifact_path=artifact_path,
        metadata_path=metadata_path,
        n_samples=2000,
        random_state=42,
    )

    assert artifact_path.exists()
    assert metadata_path.exists()
    assert artifact_path.stat().st_size < 300 * 1024

    metrics = metadata["metrics"]
    assert metrics["precision"] >= 0.80
    assert metrics["recall"] >= 0.80
    assert metrics["f1Score"] >= 0.80
    assert metrics["rocAuc"] >= 0.85

    bundle = joblib.load(artifact_path)
    assert "isolation_forest" in bundle
    assert "random_forest" in bundle
    assert bundle["model_version"] == "v1.0.0"
