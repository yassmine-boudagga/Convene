# Frontend

Android client for Convene.

## Stack

- Kotlin
- Jetpack Compose
- Hilt
- Retrofit / OkHttp
- LiveKit Android SDK

## Run locally

Open the project in Android Studio or use Gradle from the `frontend/` directory.

```bash
./gradlew assembleDebug
```

## Environment

Android reads local values from `frontend/local.properties`. Keep that file out of Git and use it only for local endpoints.
