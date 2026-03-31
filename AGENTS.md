# AGENTS.md - AI Context Brain

## 1. Project Summary

**Infomaniak Mail** is an Android email client application built by Infomaniak Network SA, featuring a modern UI with Jetpack
Compose, Realm database, and multi-account support with push notifications (Google Play variant).

### High-Level Tech Stack

- **Language**: Kotlin (Java 17, JVM target 17)
- **Platform**: Android (minSdk 27, targetSdk 35, compileSdk 36)
- **Build System**: Gradle with Kotlin DSL (build.gradle.kts)
- **Architecture**: MVVM with Repository pattern, multi-module structure
- **Dependency Injection**: Dagger Hilt
- **Database**: Realm (RealmObject for models)
- **UI Framework**: Hybrid - Jetpack Compose + XML Views with ViewBinding
- **Network**: Retrofit/Ktor with Kotlin Serialization
- **Navigation**: Android Navigation Component with Safe Args
- **Crash Reporting**: Sentry
- **Analytics**: Matomo
- **CI/CD**: Fastlane + GitHub Actions
- **Testing**: JUnit 4, Espresso, UI Automator

## 2. Context Map

```
android-kMail/
├── app/                                      # Main application module
│   ├── src/main/java/com/infomaniak/mail/    # App source code
│   │   ├── MainApplication.kt                # Application entry point
│   │   ├── MainActivity.kt                   # Main navigation coordinator (682 lines)
│   │   ├── data/                             # Data layer (models, cache, API)
│   │   │   ├── cache/                        # Realm database controllers
│   │   │   ├── models/                       # Realm entities (Message, Thread, Draft, etc.)
│   │   │   └── api/                          # API service interfaces
│   │   ├── di/                               # Hilt dependency injection modules
│   │   ├── ui/                               # UI layer (Activities, Fragments, ViewModels)
│   │   │   ├── main/                         # Main mail UI (inbox, thread list, settings)
│   │   │   ├── newMessage/                   # Compose new email flow
│   │   │   ├── login/                        # Authentication flows
│   │   │   └── alertDialogs/                 # Custom AlertDialog implementations
│   │   ├── utils/                            # Utilities (formatters, extensions)
│   │   ├── workers/                          # Background WorkManager workers
│   │   └── receivers/                        # BroadcastReceivers (notifications)
│   ├── src/main/res/                         # Android resources (layouts, drawables)
│   ├── src/main/res/navigation/              # Navigation graph XML files
│   ├── src/test/                             # Unit tests (JUnit)
│   └── src/androidTest/                      # UI/E2E tests (Espresso)
├── Core/                                     # Shared library modules
│   ├── Auth/                                 # Authentication logic
│   ├── Common/                               # Shared utilities
│   ├── Network/                              # Network layer abstractions
│   ├── Ui/                                   # Shared UI components
│   ├── Sentry/                               # Sentry integration
│   ├── Matomo/                               # Analytics integration
│   └── ... (30+ other specialized modules)
├── EmojiComponents/                          # Custom emoji picker components
├── HtmlCleaner/                              # HTML sanitization library
├── fastlane/                                 # Deployment automation
│   └── metadata/android/                     # Play Store metadata
├── .github/workflows/                        # CI/CD pipelines
├── build.gradle.kts                          # Root build configuration
├── settings.gradle.kts                       # Module definitions
└── gradle.properties                         # Gradle configuration
```

## 3. Local Norms

### Architecture & Design

- **MVVM Pattern**: Each screen has a Fragment/Activity + ViewModel
- **Repository Pattern**: Data access abstracted through controller classes (e.g., `ThreadController`)
- **Dependency Injection**: Use Hilt `@Inject` constructor for dependencies
- **Database**: All models extend `RealmObject` or `EmbeddedRealmObject`
- **SOLID**: Keep classes focused; MainActivity is currently 682 lines (do not grow further)
- **KISS**: Prefer simple solutions over complex architecture

### Command Patterns

```bash
# Build debug APK
./gradlew :app:assembleStandardDebug

# Build release APK (requires env.properties with sentryAuthToken)
./gradlew :app:assembleStandardRelease

# Run unit tests
./gradlew :app:testStandardDebugUnitTest

# Run UI tests (requires device/emulator)
./gradlew :app:connectedStandardDebugAndroidTest

# Clean build
./gradlew clean

# Full project build
./gradlew build
```

### Code Style

- **Kotlin Style**: Follow official Kotlin code style (`kotlin.code.style=official` in gradle.properties)
- **Naming**:
    - Classes: PascalCase (e.g., `ThreadController`, `MainActivity`)
    - Functions/Properties: camelCase (e.g., `getThreads()`, `isEmpty`)
    - Packages: lowercase, single word (e.g., `com.infomaniak.mail.ui.main`)
- **File Naming**: Match class name exactly (e.g., `MainActivity.kt`)
- **Comments**: GPL license header in all source files

### UI Development

- **Hybrid Approach**: Use Compose for new screens, XML with ViewBinding for existing
- **Compose**: Use Material3 components, place composables in `com.infomaniak.mail.ui.components.compose`
- **XML Views**: Use ViewBinding (property access syntax), place in `com.infomaniak.mail.views`
- **Navigation**: Use Safe Args plugin for type-safe navigation between destinations

### Testing

- **Unit Tests**: Located in `app/src/test/java/`
    - Use JUnit 4 with MockK for mocking
    - Database tests use dummy datasets in `dataset/` package
- **UI Tests**: Located in `app/src/androidTest/java/`
    - Use Espresso for view interactions
    - Test files follow pattern: `*ActivityTest.kt` or `*Test.kt`
- **Test Flavors**: Separate test source sets for `standard` and `fdroid` flavors

### Product Flavors

- **standard**: Full-featured with Google Play Services (push notifications, Firebase)
- **fdroid**: FOSS variant without proprietary dependencies (no push notifications)
- Use `standardImplementation` or `fdroidImplementation` for flavor-specific dependencies

### Environment Configuration

- **env.properties**: Required for release builds (contains `sentryAuthToken`)
- **env.example.properties**: Template showing required properties
- **local.properties**: Local SDK paths (auto-generated by Android Studio)
- Never commit `env.properties` or `local.properties` (see .gitignore)

### Learned Preferences

When writing code, follow these specific style rules:

**Line Length:**

- Maximum 130 characters per line for kotlin files
- Exceptions: single-line comments, import statements, hardcoded strings

**Blank Lines:**

- Never use more than 1 consecutive blank line
- Always add 1 blank line after early return statements/blocks (helps identify happy path)

**Copyright Headers:**

- Required in ALL files (including resources)
- Format: `Copyright (C) YYYY` or `Copyright (C) startYear-endYear`
- No blank line between copyright and package declaration

**Kotlin Control Flow:**

- Prefer one-line if/else for trivial statements under 130 chars
- Always use braces + newlines for non-trivial statements

**Jetpack Compose:**

- One-line composables with single parameter (within line limit)

**Resources (XML):**

- Remove fillColor="#00000000" (invisible colors from Figma imports)
- Follow Android Studio formatting on PRs if formatting is off

**Enums:**

- New enums: PascalCase entries (e.g., `Active`, `Inactive`)
- DO NOT rename existing enums (stored in sharedPrefs/Realm)

## 4. Self-correction

This section is for you, the future agent.

1. **Stale Map**: If you encounter a file or folder not listed in the "Context Map", update the map in this file.
2. **New Norms**: If the user corrects you (e.g., "Don't use X, use Y"), add that rule to the "Local norms" section immediately so
   you don't make the mistake again.
3. **Refinement**: If you find this file is too verbose, prune it. Keep it high-signal.
