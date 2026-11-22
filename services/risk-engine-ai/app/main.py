from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.routes import router
from app.ml.model_loader import load_model_bundle


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    load_model_bundle()
    yield


app = FastAPI(
    title="LedgerPulse Risk Engine AI",
    version="1.0.0",
    lifespan=lifespan,
)
app.include_router(router)
