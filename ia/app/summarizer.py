import os
import json
import time
import logging
import httpx
from typing import Optional

from .models import MeetingSummary, TaskItem, NoteItem

logger = logging.getLogger(__name__)

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL")
OLLAMA_MODEL    = os.getenv("OLLAMA_MODEL")
OLLAMA_TIMEOUT  = int(os.getenv("OLLAMA_TIMEOUT"))

LANGUAGE_NAMES = {
    "fr": "français", "en": "English", "ar": "العربية",
    "es": "español",  "de": "Deutsch", "it": "italiano",
    "pt": "português","nl": "Nederlands","ru": "русский",
    "zh": "中文",      "ja": "日本語",   "ko": "한국어",
    "tr": "Türkçe",   "pl": "polski",   "sv": "svenska",
    "da": "dansk",    "fi": "suomi",    "nb": "norsk",
    "cs": "čeština",  "ro": "română",
}

def get_language_name(lang_code: str) -> str:
    if not lang_code:
        return "français"
    return LANGUAGE_NAMES.get(lang_code.split("-")[0].lower(), lang_code)

def detect_text_language(text: str) -> str:

    if not text or len(text.strip()) < 20:
        return "fr"

    arabic_chars = sum(1 for c in text if "\u0600" <= c <= "\u06FF")
    total_alpha = sum(1 for c in text if c.isalpha())

    if total_alpha == 0:
        return "fr"

    arabic_ratio = arabic_chars / total_alpha

    if arabic_ratio > 0.3:
        return "ar"
    if arabic_ratio < 0.05:
        import re
        lower = text.lower()
        french_words = [
            "le", "la", "les", "de", "du", "des", "est", "sont",
            "nous", "vous", "et", "que",
        ]
        fr_count = 0
        for w in french_words:
            fr_count += len(re.findall(rf"\b{re.escape(w)}\b", lower))
        return "fr" if fr_count >= 2 else "en"

    return "ar"


def build_prompt(
    transcription: str,
    notes: list[NoteItem],
    participants: list[str],
    meeting_title: str,
    meeting_description: str = "",
    transcript_language: str = "fr"
) -> tuple[str, str]:
    output_language_name = get_language_name(transcript_language)
    is_arabic = transcript_language.startswith("ar")
    is_french = transcript_language.startswith("fr")

    if is_arabic:
        example_str = (
            'Input: "يجب على أحمد إنهاء وحدة الدفع هذا الأسبوع. تم تأجيل Stripe."\n'
            'Output: {"objective":"إنهاء وحدة الدفع","keyPoints":["وحدة الدفع أولوية","تأجيل Stripe"],'
            '"decisions":["تأجيل تكامل Stripe"],"tasks":[{"assignedTo":"أحمد",'
            '"title":"إنهاء وحدة الدفع","priority":"high","suggestedDeadline":"cette semaine"}]}'
        )
    elif transcript_language.startswith("en"):
        example_str = (
            'Input: "Alice needs to finish the payment module this week. Stripe is postponed."\n'
            'Output: {"objective":"Finalize payment module","keyPoints":["Payment module is priority",'
            '"Stripe postponed"],"decisions":["Postpone Stripe integration"],'
            '"tasks":[{"assignedTo":"Alice","title":"Finish payment module","priority":"high",'
            '"suggestedDeadline":"cette semaine"}]}'
        )
    else:
        example_str = (
            'Input: "Alice doit finir le module paiement cette semaine. On reporte Stripe."\n'
            'Output: {"objective":"Finalisation du module paiement","keyPoints":["Module paiement prioritaire",'
            '"Stripe reporté"],"decisions":["Reporter l\'intégration Stripe"],'
            '"tasks":[{"assignedTo":"Alice","title":"Finaliser le module paiement","priority":"high",'
            '"suggestedDeadline":"cette semaine"}]}'
        )

    system_prompt = f"""Tu es un analyste de réunions professionnelles.
Tu extrais des informations structurées depuis des transcriptions audio.
RÈGLES STRICTES:

- Réponds UNIQUEMENT en JSON valide, sans texte avant/après, sans balises markdown
- N'invente JAMAIS d'information absente de la transcription ou des notes
- Si une information est absente ou ambiguë, retourne une valeur vide ("" ou [])
- Rédige tous les champs textuels dans la langue indiquée dans le message utilisateur
- Le champ "suggestedDeadline" est TOUJOURS en français (ex: "dans 3 jours",
"demain", "cette semaine", "semaine prochaine", "dans 2 semaines")
- Les priorités sont TOUJOURS: "high", "medium", "low"
- Assigne les tâches UNIQUEMENT aux participants listés
- Si aucune décision n'a été prise, retourne decisions: []
- Si aucune tâche n'est identifiable, retourne tasks: []
- Respecte STRICTEMENT la structure JSON fournie, sans ajouter ni retirer de champs
- NE PAS ajouter: sentiment, nextSteps, participationSummary, ni aucun autre champ

EXEMPLE:
{example_str}"""

    notes_section = ""
    if notes:
        notes_section = "\n\nNOTES TEXTUELLES PRISES PENDANT LA RÉUNION:\n"
        for note in notes:
            ts = f" [{note.timestamp.strftime('%H:%M')}]" if note.timestamp else ""
            notes_section += f"- {note.userName}{ts}: {note.content}\n"
    else:
        notes_section = "\n\nNOTES TEXTUELLES: Aucune note prise pendant la réunion.\n"

    participants_str = ", ".join(participants) if participants else "Participants non spécifiés"

    context_section = (
        f"CONTEXTE DE LA RÉUNION:\n"
        f"- Titre: {meeting_title}\n"
        f"- Participants: {participants_str}\n"
        f"- Langue détectée: {output_language_name} (code: {transcript_language})"
    )
    if meeting_description.strip():
        context_section += f"\n- Description: {meeting_description}"

    # Instruction de langue renforcée selon la langue cible
    if is_arabic:
        language_instruction = (
            "LANGUE DE RÉDACTION:\n"
            "OBLIGATOIRE: Rédige TOUS les champs textuels en arabe (العربية).\n"
            "objective, keyPoints, decisions, et title des tasks → en arabe UNIQUEMENT.\n"
            "NE PAS utiliser le français ni l'anglais pour ces champs."
        )
        deadline_instruction = (
            'EXCEPTION CRITIQUE: Le champ "suggestedDeadline" doit être OBLIGATOIREMENT '
            'en français, peu importe la langue du résumé. '
            'Exemples valides: "dans 3 jours", "demain", "semaine prochaine", "dans 1 mois". '
            'NE PAS écrire en arabe. NE PAS écrire de dates absolues.'
        )
    elif is_french:
        language_instruction = "LANGUE DE RÉDACTION:\nRédige TOUS les champs textuels en français."
        deadline_instruction = (
            'Pour les tâches, le champ "suggestedDeadline" doit être en français '
            '(ex: "dans 3 jours", "demain", "semaine prochaine", "dans 2 semaines").'
        )
    else:
        language_instruction = (
            f"LANGUE DE RÉDACTION:\n"
            f"IMPORTANT: Rédige TOUS les champs textuels (objective, keyPoints, decisions, "
            f"titres des tâches) en {output_language_name}."
        )
        deadline_instruction = (
            f'EXCEPTION CRITIQUE: Le champ "suggestedDeadline" doit être OBLIGATOIREMENT '
            f'en français, peu importe la langue du résumé. '
            f'Exemples valides: "dans 3 jours", "demain", "semaine prochaine", "dans 1 mois". '
            f'NE PAS écrire en {output_language_name}. NE PAS écrire de dates absolues.'
        )

    user_prompt = f"""Analyse la transcription et les notes, puis génère un résumé structuré.

{context_section}

{language_instruction}

TRANSCRIPTION AUDIO:
{transcription}
{notes_section}

INSTRUCTIONS:
1. Analyse attentivement la transcription et les notes
2. Les notes complètent la transcription, donne-leur de l'importance
3. Assigne les tâches UNIQUEMENT aux participants listés
4. Génère entre 1 et 5 points clés réellement présents
5. Génère uniquement les décisions réellement prises (tableau vide si aucune)
6. Ne jamais inventer d'éléments absents
7. {deadline_instruction}
8. Réponds UNIQUEMENT avec le JSON, sans texte avant/après, sans balises markdown

FORMAT JSON ATTENDU (EXACTEMENT ces 4 champs, rien d'autre):
{{
  "objective": "Objectif principal en une phrase",
  "keyPoints": [
    "point clé"
  ],
  "decisions": [
    "Décision 1"
  ],
  "tasks": [
    {{
      "assignedTo": "Nom du participant",
      "title": "Titre de la tâche",
      "priority": "high",
      "suggestedDeadline": "dans 3 jours"
    }}
  ]
}}"""

    return system_prompt, user_prompt

def _parse_summary_response_robust(raw_text: str) -> MeetingSummary:
    import re

    try:
        data = json.loads(raw_text.strip())
    except json.JSONDecodeError:
        match = re.search(r'\{.*\}', raw_text, re.DOTALL)
        if not match:
            logger.error(f"[Summarizer] No JSON found in response. raw={raw_text[:200]}")
            return MeetingSummary(objective="", keyPoints=[], decisions=[], tasks=[])
        try:
            data = json.loads(match.group())
        except json.JSONDecodeError:
            logger.error(f"[Summarizer] JSON parse failed after fallback. raw={raw_text[:200]}")
            return MeetingSummary(objective="", keyPoints=[], decisions=[], tasks=[])

    validated_tasks = []
    for task in (data.get("tasks", []) if isinstance(data.get("tasks"), list) else []):
        if not isinstance(task, dict):
            continue
        validated_tasks.append(TaskItem(
            assignedTo=str(task.get("assignedTo", "")),
            title=str(task.get("title", "")),
            priority=task.get("priority", "medium") if task.get("priority") in {"high", "medium", "low"} else "medium",
            suggestedDeadline=str(task.get("suggestedDeadline", ""))
        ))

    return MeetingSummary(
        objective=data.get("objective", "") if isinstance(data.get("objective"), str) else "",
        keyPoints=data.get("keyPoints", []) if isinstance(data.get("keyPoints"), list) else [],
        decisions=data.get("decisions", []) if isinstance(data.get("decisions"), list) else [],
        tasks=validated_tasks
    )


class Summarizer:
    """Service de génération de résumé via Ollama/Mistral local."""

    async def check_ollama(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.get(f"{OLLAMA_BASE_URL}/api/tags") # prese
                if resp.status_code == 200:
                    models = resp.json().get("models", [])
                    model_names = [m["name"] for m in models]
                    available = any(
                        OLLAMA_MODEL in name or name in OLLAMA_MODEL
                        for name in model_names
                    )
                    if not available:
                        logger.warning(
                            f"[Summarizer] Modèle '{OLLAMA_MODEL}' non trouvé. "
                            f"Disponibles : {model_names}"
                        )
                    return available
        except Exception as e:
            logger.error(f"[Summarizer] Ollama inaccessible : {e}")
            return False

    async def summarize(
        self,
        transcription: str,
        notes: list[NoteItem],
        participants: list[str],
        meeting_title: str,
        meeting_description: str = "",
        transcript_language: str = "fr"
    ) -> MeetingSummary:
        if not transcription.strip():
            logger.warning("[Summarizer] Transcription vide reçue")
            return MeetingSummary(
                objective="Réunion sans contenu audio détecté",
                keyPoints=[], decisions=[], tasks=[],
            )

        # Whisper a la priorité si sa détection est fiable 
        # detect_text_language sert uniquement de fallback quand Whisper retourne "unknown"
        detected_from_text = detect_text_language(transcription)

        if transcript_language and transcript_language not in ("unknown", ""):
            effective_language = transcript_language
            if detected_from_text != transcript_language:
                logger.warning(
                    f"[Summarizer] Divergence langue : Whisper={transcript_language}, "
                    f"texte détecté={detected_from_text} → Whisper prioritaire"
                )
        else:
            # Whisper n'a pas détecté de langue → fallback
            effective_language = detected_from_text
            logger.warning(
                f"[Summarizer] Whisper langue inconnue → détection textuelle : {effective_language}"
            )

        system_prompt, user_prompt = build_prompt(
            transcription, notes, participants, meeting_title,
            meeting_description=meeting_description,
            transcript_language=effective_language
        )

        logger.info(
            f"[Summarizer] Envoi à Ollama ({OLLAMA_MODEL}) | "
            f"langue={effective_language} | prompt≈{len(user_prompt.split())} mots"
        )
        start_time = time.time()

        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(300.0, connect=10.0)) as client:
                response = await client.post(
                    f"{OLLAMA_BASE_URL}/api/chat",
                    json={
                        "model": OLLAMA_MODEL,
                        "messages": [
                            {"role": "system", "content": system_prompt},
                            {"role": "user", "content": user_prompt}
                        ],
                        "stream": False,
                        "format": "json",
                        "options": {
                            "temperature": 0.1,
                            "top_p": 0.9,
                            "seed": 42,
                            "num_predict": 1500,
                            "num_ctx": 8192,
                        }
                    }
                )
                response.raise_for_status()
        except httpx.TimeoutException:
            logger.error(f"[Summarizer] Timeout après {OLLAMA_TIMEOUT}s")
            raise RuntimeError(f"Ollama timeout ({OLLAMA_TIMEOUT}s).")
        except httpx.ConnectError:
            logger.error("[Summarizer] Connexion Ollama refusée")
            raise RuntimeError("Impossible de se connecter à Ollama. Lance 'ollama serve'.")

        elapsed = time.time() - start_time
        raw_text = response.json().get("message", {}).get("content", "")
        logger.info(f"[Summarizer] Réponse reçue en {elapsed:.1f}s | {len(raw_text)} chars")

        return _parse_summary_response_robust(raw_text)

summarizer = Summarizer()