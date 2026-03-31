# AGENTS.md - Infomaniak Mail App

> For the Core library, see `Core/AGENTS.md`. For composite build overview, see root `AGENTS.md`.

## Project Summary

**Infomaniak Mail** is an Android email client application built by Infomaniak Network SA, featuring a modern UI with Jetpack
Compose, Realm database, and multi-account support.

### High-Level Tech Stack

- **Language**: Kotlin (Java 17, JVM target 17)
- **Platform**: Android (minSdk 27, targetSdk 35, compileSdk 36)
- **Build System**: Gradle with Kotlin DSL
- **Architecture**: MVVM with Repository pattern
- **Dependency Injection**: Dagger Hilt
- **Database**: Realm (RealmObject for models)
- **UI Framework**: Hybrid - Jetpack Compose + XML Views with ViewBinding
- **Network**: Ktor with Kotlin Serialization
- **Navigation**: Android Navigation Component with Safe Args
- **Crash Reporting**: Sentry
- **Analytics**: Matomo

## Context Map

```
app/src/main/java/com/infomaniak/mail/
├── MainApplication.kt          # Entry point, must call InfomaniakCore.init()
├── MainActivity.kt             # Main coordinator (682 lines - keep focused)
├── data/                       # Data layer
│   ├── cache/                  # Realm controllers (ThreadController, etc.)
│   ├── models/                 # Realm entities (Message, Thread, Draft, Mailbox, etc.)
│   └── api/                    # API service interfaces
├── di/                         # Hilt modules
├── ui/                         # UI layer
│   ├── main/                   # Inbox, thread list, settings
│   ├── newMessage/             # Compose new email flow
│   ├── login/                  # Authentication flows
│   └── alertDialogs/           # Custom dialogs
├── utils/                      # Utilities and extensions
├── workers/                    # WorkManager background tasks
└── receivers/                  # BroadcastReceivers (notifications)

app/src/main/res/
├── layout/                     # XML layouts (ViewBinding)
├── navigation/                 # Navigation graphs
├── drawable/                   # Vector drawables
└── ...                         # Other resources

app/src/test/                   # JUnit unit tests
app/src/androidTest/            # Espresso UI tests
```

## Local Norms

### Architecture & Design

- **MVVM**: Fragment/Activity + ViewModel per screen
- **Repository**: Data access via controllers (`ThreadController`, `DraftController`)
- **DI**: Use Hilt `@Inject` constructor
- **Database**: Models extend `RealmObject` or `EmbeddedRealmObject`
- **SOLID/KISS**: Keep classes focused; MainActivity is already 682 lines

### Commands

```bash
# Build debug
./gradlew :app:assembleStandardDebug

# Build release (requires env.properties with sentryAuthToken)
./gradlew :app:assembleStandardRelease

# Run tests
./gradlew :app:testStandardDebugUnitTest
./gradlew :app:connectedStandardDebugAndroidTest  # UI tests need device

# Clean
./gradlew clean
```

### Code Style

**Line Length:**

- Maximum **130 characters** per line for Kotlin files
- Exceptions: single-line comments, import statements, hardcoded strings

**Blank Lines:**

- Never use more than 1 consecutive blank line
- Always add 1 blank line after early return statements/blocks

**Copyright Headers:**

- Required in ALL files (including resources)
- Format: `Copyright (C) YYYY` or `Copyright (C) startYear-endYear`
- Example: `(C) 2022-2026 Infomaniak Network SA`
- **No blank line between copyright and package declaration**

**Naming:**

- Classes: PascalCase (`MainActivity`, `ThreadController`)
- Functions/Properties: camelCase (`getThreads()`, `isEmpty`)
- Packages: lowercase (`com.infomaniak.mail.ui.main`)
- **New enums**: PascalCase entries (`Active`, `Inactive`)
- **Old enums**: DO NOT rename (stored in sharedPrefs/Realm - would break)

**Control Flow:**

```kotlin
// Trivial statements: prefer one-line (under 130 chars)
if (condition) return result

// Trivial if/else: prefer one-line
val color = if (isDark) darkColor else lightColor

// Non-trivial: always use braces + newlines
if (condition) {
    doSomething()
    doAnotherThing()
}
```

**Jetpack Compose:**

```kotlin
// One parameter: one line (if under 130 chars)
@Composable
fun MyButton(onClick: () -> Unit) {
    Text("Click")
}

// Multiple parameters: standard formatting with proper indent
@Composable
fun MyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // implementation
}
```

**Resources (XML):**

- Remove `fillColor="#00000000"` (invisible colors auto-added by Figma)
- Follow Android Studio formatting conventions
- Use Android Studio's Reformat Code on drawable/ files

**Kotlin Files:**

- Follow official Kotlin code style (`kotlin.code.style=official` in gradle.properties)
- GPL license headers required

### UI Development

- **Hybrid Approach**: Compose for new screens, XML with ViewBinding for existing
- **Compose**: Material3 components, place in `ui.components.compose`
- **XML**: ViewBinding with property access syntax, place in `views`
- **Navigation**: Use Safe Args plugin for type-safe navigation

### Testing

- **Unit Tests**: `app/src/test/java/`
    - JUnit 4 + MockK for mocking
    - Use dummy datasets in `dataset/` package for database tests
- **UI Tests**: `app/src/androidTest/java/`
    - Espresso for view interactions
    - Pattern: `*ActivityTest.kt` or `*Test.kt`

### Product Flavors

| Flavor       | Description                                             | Dependencies             |
|--------------|---------------------------------------------------------|--------------------------|
| **standard** | Full features, Google Play Services, push notifications | `standardImplementation` |
| **fdroid**   | FOSS variant, no proprietary dependencies               | `fdroidImplementation`   |

### Environment

- `env.properties`: Required for release builds (sentryAuthToken)
- `local.properties`: Local SDK paths (auto-generated)
- Never commit `env.properties` or `local.properties` (see .gitignore)

## Learned Preferences

*Add project-specific corrections here as they occur.*

**Kotlin Control Flow:**

- Prefer one-line if/else for trivial statements under 130 chars
- Always use braces + newlines for non-trivial statements

**Jetpack Compose:**

- One-line composables with single parameter (within line limit)

**Resources (XML):**

- Remove fillColor="#00000000" (invisible colors from Figma imports)
- Follow Android Studio formatting on PRs if formatting is off

## Self-correction

1. **Stale Map**: Update when you encounter new files/folders not listed
2. **New Norms**: Add user corrections to "Learned Preferences" immediately
3. **Reference Core**: When editing Core imports, check `Core/AGENTS.md` for Core-specific norms
