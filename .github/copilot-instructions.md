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
├── MainApplication.kt        # Entry — configureInfomaniakCore() (NetworkConfiguration + AuthConfiguration)
├── MainActivity.kt           # Main coordinator
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

## ⚠️ Realm Schema — Critical Rule
Realm has **3 separate databases**, each with its own schema version constant in `RealmDatabase.kt`:
- `USER_INFO_SCHEMA_VERSION`
- `MAILBOX_INFO_SCHEMA_VERSION`
- `MAILBOX_CONTENT_SCHEMA_VERSION`

**When changing a persisted Realm model** (add/remove/rename property, change type, optionality, lists, embedded objects):
1. Increment the matching schema version constant in `RealmDatabase.kt`.
2. Add a migration block in the matching migration file: `RealmMigrations.kt`, `MailboxInfoMigration.kt`, or `MailboxContentMigration.kt`.

Forgetting this causes a crash at runtime on user devices with existing data.

## Key Rules
- All user-visible strings in `res/values/strings.xml` — never hardcoded.
- New UI must use **Jetpack Compose + Material3**; do not add new XML screens.
- `standard` flavor only: Firebase, Google services (`standardImplementation`). fdroid builds must compile without them.
- `isCoreLibraryDesugaringEnabled = true` — Java 8+ APIs are available via desugaring.
- `env.properties` is git-ignored — never commit it.
- When adding/removing a runtime dependency, update `LICENSES.md` at the repo root.
