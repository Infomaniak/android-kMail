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

import com.infomaniak.mail.data.api.SnoozeUuidSerializer.lastUuidOrNull
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.utils.SentryDebug
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getNullableValue
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.migration.AutomaticSchemaMigration.MigrationContext
import io.realm.kotlin.types.RealmInstant

val USER_INFO_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
}

val MAILBOX_INFO_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
    migrationContext.keepDefaultValuesAfterSixthMigration()
}

val MAILBOX_CONTENT_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
    migrationContext.keepDefaultValuesAfterNineteenthMigration()
    migrationContext.initializeInternalDateAsDateAfterTwentySecondMigration()
    migrationContext.replaceOriginalDateWithDisplayDateAfterTwentyFourthMigration()
    migrationContext.deserializeSnoozeUuidDirectlyAfterTwentyFifthMigration()
    migrationContext.initIsLastInboxMessageSnoozedAfterTwentySeventhMigration()
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
                set(propertyName = "originalDate", value = oldObject.getValue<RealmInstant>(fieldName = "date"))
            }
        }

        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                set(propertyName = "internalDate", value = oldObject.getValue<RealmInstant>(fieldName = "date"))

                // Initialize new property with old property value
                set(propertyName = "originalDate", value = oldObject.getValue<RealmInstant>(fieldName = "date"))
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
                val displayDate = oldObject.getNullableValue<RealmInstant>(fieldName = "originalDate")
                    ?: oldObject.getValue<RealmInstant>(fieldName = "internalDate")

                set(propertyName = "displayDate", value = displayDate)
            }
        }

        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                val displayDate = oldObject.getNullableValue<RealmInstant>(fieldName = "originalDate")
                    ?: oldObject.getValue<RealmInstant>(fieldName = "internalDate")

                set(propertyName = "displayDate", value = displayDate)
            }
        }
    }
}

// Migrate from version #25
private fun MigrationContext.deserializeSnoozeUuidDirectlyAfterTwentyFifthMigration() {

    if (oldRealm.schemaVersion() <= 25L) {
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                val snoozeAction = oldObject.getNullableValue<String>(fieldName = "snoozeAction")
                val snoozeUuid = snoozeAction?.lastUuidOrNull()
                set(propertyName = "snoozeUuid", value = snoozeUuid)
            }
        }

        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                val snoozeAction = oldObject.getNullableValue<String>(fieldName = "snoozeAction")
                val snoozeUuid = snoozeAction?.lastUuidOrNull()
                set(propertyName = "snoozeUuid", value = snoozeUuid)
            }
        }
    }
}
//endregion

// Migrate from version #27
private fun MigrationContext.initIsLastInboxMessageSnoozedAfterTwentySeventhMigration() {

    if (oldRealm.schemaVersion() <= 27L) {
        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.let { newThread ->
                // Initialize new property by computing it based on other fields
                val threadFolderId = newObject.getValue<String>("folderId")

                val messages = newThread.getObjectList(propertyName = "messages")
                messages.sortBy { it.getValue<RealmInstant>("internalDate") }
                val lastMessage = messages.last { it.getValue<String>("folderId") == threadFolderId } // TODO: Fix crash if message not found

                val snoozeState = lastMessage.getNullableValue<String>("_snoozeState")
                val snoozeEndDate = lastMessage.getNullableValue<RealmInstant>("snoozeEndDate")
                val snoozeUuid = lastMessage.getNullableValue<String>("snoozeUuid")
                val isSnoozed = snoozeState == SnoozeState.Snoozed.apiValue && snoozeEndDate != null && snoozeUuid != null

                newThread.set(propertyName = "isLastInboxMessageSnoozed", value = isSnoozed)
            }
        }
    }
}
//endregion
