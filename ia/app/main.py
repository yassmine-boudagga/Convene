import logging
import os
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv

from .models import (
    ProcessMeetingRequest,
    HealthResponse,
)
from .transcriber import transcriber
from .summarizer import summarizer
from .pipeline import run_pipeline

load_dotenv()
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger(__name__)

NODE_BACKEND_URL = os.getenv("NODE_BACKEND_URL")
# chargement des modèles au démarrage
@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Smart Meeting AI Service - START")
    transcriber.load()
    logger.info("[Startup] Whisper ✓")
    ollama_ok = await summarizer.check_ollama()
    if ollama_ok:
        logger.info("[Startup] Ollama ✓")
    else:
        logger.warning("[Startup] X Ollama non disponible au démarrage. ")

    logger.info("[Startup] Microservice IA prêt ✓")
    logger.info(f"[Startup] Whisper model  : {transcriber.model_size}")
    logger.info(f"[Startup] Ollama model   : {os.getenv('OLLAMA_MODEL')}")
    logger.info(f"[Startup] Backend URL    : {NODE_BACKEND_URL}")

    yield
    logger.info("[Shutdown] Arrêt du microservice IA")

app = FastAPI(
    title="Smart Meeting AI Service",
    description="Microservice de transcription et résumé ",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[os.getenv("NODE_BACKEND_URL")],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)
# ROUTES
@app.get("/health", response_model=HealthResponse)
async def health_check():
    ollama_ok = await summarizer.check_ollama()
    return HealthResponse(
        status="ok" if (transcriber.is_loaded() and ollama_ok) else "degraded",
        whisperModel=transcriber.model_size,
        ollamaModel=os.getenv("OLLAMA_MODEL"),
        ollamaReachable=ollama_ok
    )

@app.post("/process", status_code=202)
async def process_meeting(
    req: ProcessMeetingRequest,
    background_tasks: BackgroundTasks
):#Déclenche le pipeline en arrière-plan et répond immédiatement.
    if not transcriber.is_loaded():
        raise HTTPException(
            status_code=503,
            detail="Whisper non chargé. Le service démarre peut-être encore."
        )
    callback_url = req.callbackUrl
    meta = req.meta or {}
    if req.meetingTitle and "title" not in meta:
        meta["title"] = req.meetingTitle
    if req.participants and "participants" not in meta:
        meta["participants"] = req.participants
    if req.language and "language" not in meta:
        meta["language"] = req.language
    logger.info(f"[API] POST /process → meetingId={req.meetingId}")
    background_tasks.add_task(
        run_pipeline,
        req.meetingId,
        req.audioPath,
        req.notes,
        meta,
        callback_url
    )
    return {
        "status": "processing",
        "meetingId": req.meetingId
    }
