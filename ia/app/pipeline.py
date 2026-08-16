import time
import logging
import os
import httpx
from typing import Optional

from .transcriber import transcriber
from .summarizer import summarizer
from .models import NoteItem

logger = logging.getLogger(__name__)


def _build_transcript_payload(
    full_text: str = "",
    language: str = "unknown",
    duration_seconds: Optional[float] = None
) -> dict:
    duration_value = None
    if duration_seconds is not None:
        try:
            duration_value = int(round(float(duration_seconds)))
        except (TypeError, ValueError):
            duration_value = None
    return {
        "rawText": full_text or "",
        "language": language or "unknown",
        "durationSeconds": duration_value,
    }


async def _post_callback(callback_url: str, payload: dict):
    headers = {"Content-Type": "application/json"}
    secret = os.getenv("AI_CALLBACK_SECRET")
    if secret:
        headers["x-ai-callback-secret"] = secret
    async with httpx.AsyncClient(timeout=60.0) as client:
        await client.post(callback_url, json=payload, headers=headers)


async def run_pipeline(
    meeting_id: str,
    audio_path: str,
    notes: list[NoteItem],
    meta: dict,
    callback_url: str
):
    """Pipeline complet asynchrone avec callback vers le backend Node.js."""
    pipeline_start = time.time()

    meeting_title = (
        meta.get("title")
        or "Untitled meeting"
    )
    meeting_description = meta.get("description", "")
    participants = meta.get("participants") or []

    logger.info(
        f"\n{'='*60}\n"
        f"[Pipeline] START → meetingId={meeting_id}\n"
        f"  titre       : {meeting_title}\n"
        f"  audio       : {audio_path}\n"
        f"  participants: {participants}\n"
        f"  notes       : {len(notes)} note(s)\n"
        f"{'='*60}"
    )
    # ÉTAPE 1 : Validation du fichier audio 
    audio_path = os.path.normpath(audio_path)

    if not os.path.exists(audio_path):
        logger.error(f"[Pipeline] Fichier audio introuvable : {audio_path}")
        await _post_callback(callback_url, {
            "error": "transcription_failed",
            "details": f"Fichier audio introuvable : {audio_path}"
        })
        return

    file_size_mb = os.path.getsize(audio_path) / (1024 * 1024)
    logger.info(f"[Pipeline] Fichier audio validé | taille={file_size_mb:.1f}MB")

    #  ÉTAPE 2 : Transcription Whisper 
    logger.info("[Pipeline] ÉTAPE 2 → Transcription Whisper...")

    try:
        transcription_result = transcriber.transcribe(
            audio_path=audio_path,
            language=meta.get("language")
        )
        logger.info(
            f"[Pipeline] Transcription OK | "
            f"langue={transcription_result.language} | "
            f"segments={len(transcription_result.segments)} | "
            f"mots≈{len(transcription_result.fullText.split())}"
        )
    except FileNotFoundError as e:
        await _post_callback(callback_url, {
            "error": "transcription_failed",
            "details": str(e)
        })
        return
    except Exception as e:
        logger.exception("[Pipeline] Erreur Whisper inattendue")
        await _post_callback(callback_url, {
            "error": "transcription_failed",
            "details": f"Erreur transcription : {str(e)}"
        })
        return

    if not transcription_result.fullText.strip():
        logger.warning(f"[Pipeline] Transcription vide pour {meeting_id} → résumé ignoré")
        total_elapsed = time.time() - pipeline_start
        await _post_callback(callback_url, {
            "pipelineStatus": "empty_audio",
            "transcript": _build_transcript_payload("", "unknown", 0),
            "summary": {"keyPoints": [], "decisions": [], "actionItems": []},
            "tasksToCreate": [],
            "processingTimeSeconds": round(total_elapsed, 2)
        })
        return

    #  ÉTAPE 3 : Génération résumé + tâches 
    logger.info("[Pipeline] ÉTAPE 3 → Génération résumé Mistral...")

    try:
        summary = await summarizer.summarize(
            transcription=transcription_result.fullText,
            notes=notes,
            participants=participants,
            meeting_title=meeting_title,
            meeting_description=meeting_description,
            transcript_language=transcription_result.language or "fr"
        )
        logger.info(
            f"[Pipeline] Résumé OK | "
            f"keyPoints={len(summary.keyPoints)} | "
            f"decisions={len(summary.decisions)} | "
            f"tasks={len(summary.tasks)}"
        )
    except RuntimeError as e:
        logger.error(f"[Pipeline] Erreur Mistral : {e}")
        await _post_callback(callback_url, {
            "pipelineStatus": "failed",
            "error": "summary_failed",
            "details": f"Transcription OK mais résumé échoué : {str(e)}",
            "transcript": _build_transcript_payload(
                transcription_result.fullText,
                transcription_result.language,
                transcription_result.durationSeconds
            )
        })
        return
    except Exception as e:
        logger.exception("[Pipeline] Erreur Mistral inattendue")
        await _post_callback(callback_url, {
            "pipelineStatus": "failed",
            "error": "summary_failed",
            "details": f"Résumé échoué : {str(e)}",
            "transcript": _build_transcript_payload(
                transcription_result.fullText,
                transcription_result.language,
                transcription_result.durationSeconds
            )
        })
        return
    #  ÉTAPE 4 : Assemblage réponse finale 
    total_elapsed = time.time() - pipeline_start

    logger.info(
        f"\n{'='*60}\n"
        f"[Pipeline] TERMINÉ ✓ | meetingId={meeting_id}\n"
        f"  Temps total : {total_elapsed:.1f}s\n"
        f"{'='*60}\n"
    )
    action_items = [
        {
            "text": t.title,
            "ownerHint": t.assignedTo,
            "dueDateHint": t.suggestedDeadline,
            "priorityHint": t.priority or "medium"
        }
        for t in summary.tasks
    ]

    await _post_callback(callback_url, {
        "pipelineStatus": "completed",
        "transcript": _build_transcript_payload(
            transcription_result.fullText,
            transcription_result.language,
            transcription_result.durationSeconds
        ),
        "summary": {
            "keyPoints": summary.keyPoints,
            "decisions": summary.decisions,
            "actionItems": action_items
        },
        "processingTimeSeconds": round(total_elapsed, 2)
    })