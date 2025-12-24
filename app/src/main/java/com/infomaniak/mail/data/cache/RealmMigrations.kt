/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.mail.data.cache

import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.utils.SentryDebug
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getNullableValue
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.migration.AutomaticSchemaMigration.MigrationContext
import io.realm.kotlin.types.RealmInstant

private const val TAG = "RealmMigrations"

val USER_INFO_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
}

val MAILBOX_INFO_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
    migrationContext.keepDefaultValuesAfterSixthMigration()
    migrationContext.renameKSuiteRelatedBooleans()
    migrationContext.revertKSuiteRelatedBooleanRenaming()
    migrationContext.keepDefaultValuesAfterTwelveMigration()
}

val MAILBOX_CONTENT_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
    migrationContext.keepDefaultValuesAfterNineteenthMigration()
    migrationContext.initializeInternalDateAsDateAfterTwentySecondMigration()
    migrationContext.replaceOriginalDateWithDisplayDateAfterTwentyFourthMigration()
    migrationContext.deserializeSnoozeUuidDirectlyAfterTwentyFifthMigration()
    migrationContext.initIsLastInboxMessageSnoozedAfterTwentySeventhAndTwentyEightMigration()
    migrationContext.initMessagesWithContentToTheOldMessagesListAfterThirtySecondMigration()
}

// Migrate to version #1
private fun MigrationContext.deleteRealmFromFirstMigration() {
    if (oldRealm.schemaVersion() < 1L) newRealm.deleteAll()
}

//region Use default property values when adding a new column in a migration
/**
 * Migrate from version #6
 *
 * This whole migration needs to be done because of this issue :
 * https://github.com/realm/realm-swift/issues/1793
 *
 * Yes the issue is on the Realm-Swift repository, but all Realm projects are impacted.
 *
 * Documentation to handle manual migrations :
 * https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/realm-database/schemas/change-an-object-model/
 */
private fun MigrationContext.keepDefaultValuesAfterSixthMigration() {
    if (oldRealm.schemaVersion() <= 6L) {
        enumerate(className = "Mailbox") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {

                // Add property with default value
                set(propertyName = "_isValidInLdap", value = true)

                // Rename property without losing its previous value
                set(propertyName = "_isLocked", value = oldObject.getValue<Boolean>(fieldName = "isLocked"))

                // Rename property without losing its previous value
                set(propertyName = "hasValidPassword", value = oldObject.getValue<Boolean>(fieldName = "isPasswordValid"))
            }
        }
    }
}

// Migrate from version #9
private fun MigrationContext.renameKSuiteRelatedBooleans() {

    if (oldRealm.schemaVersion() <= 9L) {
        enumerate(className = "Mailbox") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {

                // Rename property without losing its previous value
                oldObject.getValueOrNull<Boolean>("isFree")?.let {
                    set("isKSuitePerso", value = it)
                }

                // Rename property without losing its previous value
                setIfPropertyExists("isKSuitePersoFree", value = oldObject.getValue<Boolean>("isLimited"))
            }
        }
    }
}

// Migrate from version #11
private fun MigrationContext.revertKSuiteRelatedBooleanRenaming() {

    if (oldRealm.schemaVersion() <= 11L) {
        enumerate(className = "Mailbox") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->

            newObject?.apply {
                // Rename property without losing its previous value
                oldObject.getValueOrNull<Boolean>("isKSuitePersoFree")?.let {
                    set("isLimited", value = it)
                }
            }
        }
    }
}
//endregion

// Migrate from version #19
private fun MigrationContext.keepDefaultValuesAfterNineteenthMigration() {

    if (oldRealm.schemaVersion() <= 19L) {

        enumerate(className = "Folder") { _: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Add property with default value
                set(propertyName = "isDisplayed", value = true)
            }
        }

        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Rename property without losing its previous value
                set(propertyName = "isScheduledMessage", value = oldObject.getValue<Boolean>(fieldName = "isScheduled"))
            }
        }

    }
}
//endregion

// Migrate from version #22
private fun MigrationContext.initializeInternalDateAsDateAfterTwentySecondMigration() {

    if (oldRealm.schemaVersion() <= 22L) {
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                set(propertyName = "internalDate", value = oldObject.getValue<RealmInstant>(fieldName = "date"))

                // Initialize new property with old property value
                setIfPropertyExists(propertyName = "originalDate", value = oldObject.getValue<RealmInstant>(fieldName = "date"))
            }
        }

        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                set(propertyName = "internalDate", value = oldObject.getValue<RealmInstant>(fieldName = "date"))

                // Initialize new property with old property value
                setIfPropertyExists(propertyName = "originalDate", value = oldObject.getValue<RealmInstant>(fieldName = "date"))
            }
        }
    }
}
//endregion

// Migrate from version #24
private fun MigrationContext.replaceOriginalDateWithDisplayDateAfterTwentyFourthMigration() {

    if (oldRealm.schemaVersion() <= 24L) {
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value

                // If migrating from a version a bit too old, "originalDate" might not have existed in the old object
                val originalDate = oldObject.getNullableValueOrRecover(
                    fieldName = "originalDate",
                    recovery = { oldObject.getValue<RealmInstant>(fieldName = "date") },
                )

                val displayDate = originalDate ?: oldObject.getValue<RealmInstant>(fieldName = "internalDate")

                set(propertyName = "displayDate", value = displayDate)
            }
        }

        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value

                // If migrating from a version a bit too old, "originalDate" might not have existed in the old object
                val originalDate = oldObject.getNullableValueOrRecover(
                    fieldName = "originalDate",
                    recovery = { oldObject.getValue<RealmInstant>(fieldName = "date") },
                )

                val displayDate = originalDate ?: oldObject.getValue<RealmInstant>(fieldName = "internalDate")

                set(propertyName = "displayDate", value = displayDate)
            }
        }
    }
}
//endregion

// Migrate from version #25
private fun MigrationContext.deserializeSnoozeUuidDirectlyAfterTwentyFifthMigration() {

    if (oldRealm.schemaVersion() <= 25L) {
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value

                // If snoozeAction was never initialized, default to null the same way the code used to set its default value
                val snoozeAction = oldObject.getNullableValueOrRecover<String>(fieldName = "snoozeAction", recovery = { null })
                val snoozeUuid = snoozeAction?.lastUuidOrNull()
                set(propertyName = "snoozeUuid", value = snoozeUuid)
            }
        }

        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value

                // If snoozeAction was never initialized, default to null the same way the code used to set its default value
                val snoozeAction = oldObject.getNullableValueOrRecover<String>(fieldName = "snoozeAction", recovery = { null })
                val snoozeUuid = snoozeAction?.lastUuidOrNull()
                set(propertyName = "snoozeUuid", value = snoozeUuid)
            }
        }
    }
}

private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
private val lastUuidRegex = Regex("""$UUID_PATTERN(?!.*$UUID_PATTERN)""", RegexOption.IGNORE_CASE)
private fun String.lastUuidOrNull() = lastUuidRegex.find(this)?.value
//endregion

// Migrate from version #27 or #28
// Bumping to schema 29 required to recompute the Thread object again. If already done for schema 28, no need to do it twice
private fun MigrationContext.initIsLastInboxMessageSnoozedAfterTwentySeventhAndTwentyEightMigration() {

    if (oldRealm.schemaVersion() <= 28L) {
        enumerate(className = "Thread") { _: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.let { newThread ->
                // Initialize new property by computing it based on other fields
                val threadFolderId = newObject.getValue<String>("folderId")

                val messages = newThread.getObjectList(propertyName = "messages")
                messages.sortBy { it.getValue<RealmInstant>("internalDate") }

                val lastMessage = messages.lastOrNull { it.getValue<String>("folderId") == threadFolderId }

                val isSnoozed = if (lastMessage == null) {
                    // Defaulting to `false` only means that this thread won't be automatically unsnoozed until it's recomputed
                    false
                } else {
                    val snoozeState = lastMessage.getNullableValueOrRecover<String>("_snoozeState", recovery = { null })
                    val snoozeEndDate = lastMessage.getNullableValueOrRecover<RealmInstant>("snoozeEndDate", recovery = { null })
                    val snoozeUuid = lastMessage.getNullableValueOrRecover<String>("snoozeUuid", recovery = { null })

                    lastMessage.getValueOrNull<RealmInstant>("displayDate")?.let { displayDate ->
                        newThread.set(propertyName = "displayDate", value = displayDate)
                    }
                    lastMessage.getValueOrNull<RealmInstant>("internalDate")?.let { internalDate ->
                        newThread.set(propertyName = "internalDate", value = internalDate)
                    }

                    snoozeState == SnoozeState.Snoozed.apiValue && snoozeEndDate != null && snoozeUuid != null
                }

                newThread.set(propertyName = "isLastInboxMessageSnoozed", value = isSnoozed)
            }
        }
    }
}
//endregion

// Migrate from version #32
private fun MigrationContext.initMessagesWithContentToTheOldMessagesListAfterThirtySecondMigration() {

    if (oldRealm.schemaVersion() <= 32L) {
        enumerate(className = "Thread") { _: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.let { newThread ->
                // Initialize messagesWithContent by copying the existing messages
                val messages = newThread.getObjectList(propertyName = "messages")
                newThread.set(propertyName = "messagesWithContent", value = messages)
            }
        }
    }
}
//endregion

// Migrate from version #13
private fun MigrationContext.keepDefaultValuesAfterTwelveMigration() {

    if (oldRealm.schemaVersion() <= 13L) {
        enumerate(className = "Mailbox") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Rename property without losing its previous value
                set(propertyName = "isLocked", value = oldObject.getValue<Boolean>(fieldName = "_isLocked"))
            }
        }
    }
}
//endregion

/**
 * If the property we're trying to set doesn't exist anymore in our model at the latest schema version, instead of crashing skip
 * this property value.
 */
private fun DynamicMutableRealmObject.setIfPropertyExists(propertyName: String, value: Any?) {
    runCatching {
        set(propertyName, value)
    }.onFailure {
        if (it !is IllegalArgumentException) SentryLog.e(TAG, "Unexpected exception thrown during realm migration", it)
    }
}

/**
 * Tries to read [fieldName] but if the value is not in the [DynamicRealmObject], instead of crashing, fallback to an
 * alternative recovery method to get the expected value.
 *
 * Used for when we can be migrating from versions of the model that might never have had [fieldName] initialized.
 */
private inline fun <reified T : Any> DynamicRealmObject.getNullableValueOrRecover(fieldName: String, recovery: () -> T?): T? {
    return runCatching {
        getNullableValue<T>(fieldName)
    }.getOrElse {
        if (it !is IllegalArgumentException) SentryLog.e(TAG, "Unexpected exception thrown during realm migration", it)

        recovery()
    }
}

/**
 * Tries to read [fieldName] but if the value is not in the [DynamicRealmObject], instead of crashing, fallback to an
 * alternative recovery method to get the expected value.
 *
 * Used for when we can be migrating from versions of the model that might never have had [fieldName] initialized.
 */
private inline fun <reified T : Any> DynamicRealmObject.getValueOrNull(fieldName: String): T? {
    return runCatching {
        getValue<T>(fieldName)
    }.getOrElse {
        if (it !is IllegalArgumentException) SentryLog.e(TAG, "Unexpected exception thrown during realm migration", it)

        null
    }
}
