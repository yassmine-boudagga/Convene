import os
import time
import logging
from datetime import timedelta
from typing import Optional
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()

# CUDA DLL paths
venv_path = Path(os.environ["VIRTUAL_ENV"])

cuda_paths = [
    venv_path / "Lib/site-packages/nvidia/cublas/bin",
    venv_path / "Lib/site-packages/nvidia/cudnn/bin",
]

for path in cuda_paths:
    if path.exists():
        logger.debug(f"[CUDA] Adding DLL path: {path}")

        os.environ["PATH"] = str(path) + os.pathsep + os.environ["PATH"]
        # Python 3.8+
        os.add_dll_directory(str(path))
    else:
        logger.debug(f"[CUDA] Path not found: {path}")

from faster_whisper import WhisperModel

from .models import TranscriptionResult, TranscriptionSegment

logger = logging.getLogger(__name__)

# CONFIGURATION MODÈLE
WHISPER_MODEL_SIZE = os.getenv("WHISPER_MODEL_SIZE","base")
WHISPER_DEVICE     = os.getenv("WHISPER_DEVICE", "cpu")   
WHISPER_COMPUTE    = os.getenv("WHISPER_COMPUTE", "int8")  
WHISPER_THREADS    = int(os.getenv("WHISPER_THREADS", "2"))    

class Transcriber:

    def __init__(self):
        self._model: Optional[WhisperModel] = None
        self.model_size = WHISPER_MODEL_SIZE

    def load(self):

        logger.info(
            f"[Whisper] Chargement modèle '{WHISPER_MODEL_SIZE}' "
            f"| device={WHISPER_DEVICE} | compute={WHISPER_COMPUTE}"
        )
        start = time.time()

        self._model = WhisperModel(
            WHISPER_MODEL_SIZE,
            device=WHISPER_DEVICE,
            compute_type=WHISPER_COMPUTE,
            cpu_threads=WHISPER_THREADS,
            num_workers=1            
        )

        elapsed = time.time() - start
        logger.info(f"[Whisper] Modèle chargé en {elapsed:.1f}s ✓")

    def is_loaded(self) -> bool:
        return self._model is not None

    def transcribe(
        self,
        audio_path: str,
        language: Optional[str] = None
    ) -> TranscriptionResult:

        if not self.is_loaded():
            raise RuntimeError(
                "[Whisper] Modèle non chargé. "
                "Veuillez appeler load() au démarrage de l'application."
            )

        if not os.path.exists(audio_path):
            raise FileNotFoundError(
                f"[Whisper] Fichier audio introuvable : {audio_path}"
            )

        logger.info(f"[Whisper] Début transcription → {audio_path}")
        start_time = time.time()

        # beam_size=5 : meilleure qualité
        try:
            segments_gen, info = self._model.transcribe(
                audio_path,
                language=language,        
                beam_size=5,
                vad_filter=True,             # filtre les silences automatiquement
                vad_parameters=dict(
                    min_silence_duration_ms=500  # silence > 500ms est ignoré
                ),
                word_timestamps=False,
            )
        except ValueError as e:
            if "max() arg is an empty sequence" in str(e):
                logger.warning(
                    "[Whisper] Audio silencieux détecté (VAD a tout filtré) "
                    "→ transcription vide"
                )
                return TranscriptionResult(
                    language="unknown",
                    fullText="",
                    segments=[],
                    durationSeconds=0.0,
                )
            raise

        #  Construction des segments 
        segments_list = []
        full_text_parts = []

        for i, segment in enumerate(segments_gen, start=1):
            start_str = str(timedelta(seconds=int(segment.start)))
            end_str   = str(timedelta(seconds=int(segment.end)))
            text      = segment.text.strip()

            if not text:   # ignore les segments vides
                continue

            segments_list.append(TranscriptionSegment(
                index=i,
                start=start_str,
                end=end_str,
                text=text
            ))
            full_text_parts.append(text)

        elapsed = time.time() - start_time
        full_text = " ".join(full_text_parts)

        logger.info(
            f"[Whisper] Transcription terminée en {elapsed:.1f}s | "
            f"langue={info.language} | segments={len(segments_list)} | "
            f"mots≈{len(full_text.split())}"
        )

        return TranscriptionResult(
            language=info.language,
            fullText=full_text,
            segments=segments_list,
            durationSeconds=info.duration
        )

# INSTANCE SINGLETON
transcriber = Transcriber()