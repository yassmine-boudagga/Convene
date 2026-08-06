# Convene

Convene is a multi-stack meeting platform composed of:

- `backend/`: Node.js + Express API with MongoDB, LiveKit, MinIO/S3, email, admin endpoints and WebSocket sync.
- `frontend/`: Android app built with Kotlin, Jetpack Compose, Hilt and LiveKit.
- `ia/`: Python FastAPI microservice for transcription and summarization.

## Project layout

- `backend/` exposes the REST API and serves the admin UI.
- `frontend/` contains the Android client.
- `ia/` processes audio, generates summaries and posts results back to the backend.

## Local setup

1. Prepare the backend environment file from `backend/.env.example`.
2. Prepare the IA environment file from `ia/.env.example`.
3. Install backend dependencies with `npm install` in `backend/`.
4. Install IA dependencies with `pip install -r ia/requirements.txt` in a Python virtual environment.
5. Open `frontend/` in Android Studio or run Gradle tasks from the command line.

## Notes before publishing

- Keep all secrets out of Git history.
- Do not commit local build outputs, virtual environments or IDE caches.
- Rotate any production credentials before public release.