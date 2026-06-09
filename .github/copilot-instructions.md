# Copilot Coding Agent Onboarding Guide for Infomaniak/android-kMail

Before reading this file, please read AGENTS.md to learn more about the project context, structure, and conventions.

## Pull Request Review Instructions

- Ensure strings are localized via `strings.xml` resources.
- When reviewing Realm model changes, check whether the persisted schema changed: added, removed, renamed, or type-changed persisted properties, changed optionality, lists, embedded objects, or object types.
- If the persisted Realm schema changed, ensure the matching schema version constant (`USER_INFO_SCHEMA_VERSION`, `MAILBOX_INFO_SCHEMA_VERSION`, or `MAILBOX_CONTENT_SCHEMA_VERSION`) was incremented in `app/src/main/java/com/infomaniak/mail/data/cache/RealmDatabase.kt`, and that the relevant migration block in `MailboxContentMigration.kt`, `MailboxInfoMigration.kt`, or `RealmMigrations.kt` is updated when existing data needs migration.
- Ensure new UI written in Jetpack Compose uses Material3 components and follows the hybrid approach (Compose for new screens, XML with ViewBinding for existing).
