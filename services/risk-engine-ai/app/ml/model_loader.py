import json
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib

from app.config import MODEL_ARTIFACT_PATH, MODEL_METADATA_PATH
from app.ml.train import train_and_save_model


@dataclass(frozen=True)
class LoadedModelBundle:
    isolation_forest: Any
    random_forest: Any
    metadata: dict[str, Any]
    loaded: bool = True


_LOCK = threading.Lock()
_CACHED_BUNDLE: LoadedModelBundle | None = None


def load_model_bundle(
    artifact_path: Path = MODEL_ARTIFACT_PATH,
    metadata_path: Path = MODEL_METADATA_PATH,
    force_reload: bool = False,
) -> LoadedModelBundle:
    """Load the persisted .joblib bundle and metadata, training automatically if absent."""
    global _CACHED_BUNDLE
    with _LOCK:
        if _CACHED_BUNDLE is not None and not force_reload:
            return _CACHED_BUNDLE

        if not artifact_path.exists() or not metadata_path.exists():
            train_and_save_model(artifact_path=artifact_path, metadata_path=metadata_path)

        bundle = joblib.load(artifact_path)
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        _CACHED_BUNDLE = LoadedModelBundle(
            isolation_forest=bundle["isolation_forest"],
            random_forest=bundle["random_forest"],
            metadata=metadata,
            loaded=True,
        )
        return _CACHED_BUNDLE
