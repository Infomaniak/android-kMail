# AGENTS.md - Infomaniak Mail (Top Level)

> **Navigation Guide**: This file describes the composite structure. For app-specific norms, see `app/AGENTS.md`. For Core library norms, see `Core/AGENTS.md`.

## Repository Structure

This is a **composite Gradle build** with two main components:

```
android-kMail/
├── Core/                    # Git submodule - shared library (see Core/AGENTS.md)
│   ├── Auth/               # OAuth2, account management
│   ├── Network/            # Ktor HTTP client
│   ├── Ui/                 # Compose + XML components
│   ├── Common/             # Shared utilities
│   └── ... (30+ modules)
├── app/                     # Main Mail application (see app/AGENTS.md)
│   ├── src/main/java/...   # Mail app source code
│   ├── src/main/res/       # Android resources
│   ├── src/test/           # Unit tests
│   └── src/androidTest/    # UI tests
├── EmojiComponents/         # Custom emoji picker
├── HtmlCleaner/             # HTML sanitization
└── AGENTS.md               # This file (top-level overview)
```

## Quick Summary

| Component | Location | AGENTS.md | Purpose |
|-----------|----------|-----------|---------|
| **Core** | `Core/` | `Core/AGENTS.md` | Reusable library for all Infomaniak apps |
| **App** | `app/` | `app/AGENTS.md` | Mail app-specific code and norms |
| **Root** | `./` | `AGENTS.md` | This file - composite build overview |

## Composite Build Explained

- **Core is a Git submodule**: Changes in `Core/` are tracked separately and shared with other Infomaniak apps
- **Immediate resolution**: App uses `com.infomaniak.core:<module>` which resolves locally (no Maven publishing needed)
- **Impact**: Changes to Core affect ALL Infomaniak apps - be careful!

## Key Integration Points

- **Authentication**: App uses `Core:Auth` for OAuth2 (tokens, account management)
- **Networking**: App uses `Core:Network` with `HttpClientProvider` from `Core:Common`
- **UI Components**: App uses `Core:Ui:Compose` and `Core:Ui:View` components
- **Initialization**: `MainApplication.kt` initializes Core via `NetworkConfiguration.init()` and `AuthConfiguration.init()` inside `configureInfomaniakCore()`

## Quick Commands

```bash
# Build Mail app
./gradlew :app:assembleStandardDebug

# Build included Core modules
./gradlew :Core:Legacy:assemble :Core:Legacy:Confetti:assemble

# Run all tests
./gradlew :app:testStandardDebugUnitTest && ./gradlew :Core:Legacy:test :Core:Legacy:Confetti:test

# Lint included Core modules (uses ktlint)
./gradlew :Core:Legacy:ktlintCheck :Core:Legacy:Confetti:ktlintCheck
```

## Important Rules

1. **Editing Core**: Core changes affect ALL apps - consider impact carefully
2. **Norm separation**: 
   - App norms → `app/AGENTS.md`
   - Core norms → `Core/AGENTS.md`
   - Composite structure → this file
3. **When working on:** 
   - `app/src/...` → read `app/AGENTS.md`
   - `Core/` → read `Core/AGENTS.md`