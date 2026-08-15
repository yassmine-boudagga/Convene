# IA Service

FastAPI microservice used by Convene for transcription and summarization.

## Components

- `main.py`: FastAPI entrypoint
- `transcriber.py`: Whisper-based transcription
- `summarizer.py`: summary generation through Ollama
- `pipeline.py`: orchestration and callback to the backend
- `models.py`: Pydantic schemas

## Install

Use the virtual environment for this service and install the exact dependency set.

```bash
pip install -r requirements.txt
```

## Run

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Environment

Copy `ia/.env.example` to `ia/.env` and adjust the local backend / Ollama values.
