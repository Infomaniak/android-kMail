# Copilot Coding Agent Onboarding — android-kMail

> **Read `AGENTS.md`, `app/AGENTS.md`, and `Core/AGENTS.md`** for architecture, conventions, and module structure. This file covers build, CI, and critical validation rules.

## Overview
Infomaniak Mail for Android — full-featured email client. Hybrid UI (Jetpack Compose for new screens, XML/ViewBinding for existing). Hilt DI, Realm database (3 separate Realm instances), Ktor networking. Two build flavors: `standard` (Google Play, Firebase) and `fdroid`. Two internal modules: `EmojiComponents`, `HtmlCleaner`.

## One-Time Environment Setup
```bash
git submodule update --init --recursive   # pull Core submodule — required for Gradle settings plugin
cp env.example.properties env.properties  # fill sentryAuthToken (use a dummy value locally)
```
Missing `env.properties` or uninitialized submodule → Gradle config phase fails.

## Build & Test (CI: `.github/workflows/android.yml`)
CI runs on every non-draft PR:
```bash
./gradlew clean
./gradlew build                         # all flavors
./gradlew testDebugUnitTest --stacktrace
```
Replicate locally with the same sequence. Use flavor-specific tasks when needed:
```bash
./gradlew assembleStandardDebug
./gradlew assembleFdroidDebug
./gradlew testStandardDebugUnitTest
```

## Project Layout
```
app/src/main/java/com/infomaniak/mail/
├── MainApplication.kt        # Entry — configureInfomaniakCore()
├── ui/
│   └── MainActivity.kt       # Main coordinator (at app/src/main/java/com/infomaniak/mail/ui/)
├── data/
│   ├── cache/                # Realm controllers (RealmDatabase.kt — schema versions here)
│   ├── models/               # Realm entities (Message, Thread, Draft, Mailbox…)
│   └── api/                  # API service interfaces
├── di/                       # Hilt modules
├── ui/                       # Compose + XML screens
└── workers/                  # WorkManager tasks
EmojiComponents/              # Custom emoji picker module
HtmlCleaner/                  # HTML sanitization module
Core/                         # Git submodule — Infomaniak shared library
gradle/libs.versions.toml
```

## PR Review Instructions

- Ensure strings are localized via `strings.xml` resources (located at `app/src/main/res/values/strings.xml`).
- When reviewing Realm model changes, check whether the persisted schema changed: added, removed, renamed, or type-changed persisted properties, changed optionality, lists, embedded objects, or object types.
- If the persisted Realm schema changed, ensure the matching schema version constant (`USER_INFO_SCHEMA_VERSION`, `MAILBOX_INFO_SCHEMA_VERSION`, or `MAILBOX_CONTENT_SCHEMA_VERSION`) was incremented in `app/src/main/java/com/infomaniak/mail/data/cache/RealmDatabase.kt`, and that the relevant migration block in `MailboxContentMigration.kt`, `MailboxInfoMigration.kt`, or `RealmMigrations.kt` is updated when existing data needs migration.
- Ensure new UI written in Jetpack Compose uses Material3 components and follows the hybrid approach (Compose for new screens, XML with ViewBinding for existing, supports different screen sizes).
- `standard` flavor only: Firebase, Google services — fdroid builds must compile without them.
- When adding/removing a runtime dependency, update `LICENSES.md` at the repo root.
