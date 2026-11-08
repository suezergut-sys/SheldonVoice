# Voice Telegram Client

- Keep the app independent of the adjacent VPS repositories. Do not copy their secrets.
- Use TDLib as a user client, never Bot API to impersonate a user.
- No credentials, sessions, phone numbers, private messages or audio in Git/logs.
- Microphone recording requires permission and a visible resumed Activity. Cancel on pause.
- Keep core state logic Android-free and cover its transitions with JVM tests.
- Before delivery run `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Update README, LIMITATIONS and CHANGELOG with user-facing changes.
