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

import android.util.Log
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.extensions.toRealmInstant
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getNullableValue
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.migration.AutomaticSchemaMigration.MigrationContext
import io.realm.kotlin.types.RealmInstant
import java.util.Date

val USER_INFO_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
}

val MAILBOX_INFO_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
    val map = mutableMapOf<String, Any?>()
    migrationContext.keepDefaultValuesAfterSixthMigration(map)
}

val MAILBOX_CONTENT_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
    val map = mutableMapOf<String, Any?>()
    migrationContext.keepDefaultValuesAfterNineteenthMigration(map)
    migrationContext.initializeInternalDateAsDateAfterTwentySecondMigration(map)
    migrationContext.replaceOriginalDateWithDisplayDateAfterTwentyFourthMigration(map)
    migrationContext.deserializeSnoozeUuidDirectlyAfterTwentyFifthMigration(map)
    migrationContext.initIsLastInboxMessageSnoozedAfterTwentySeventhMigration(map)
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
private fun MigrationContext.keepDefaultValuesAfterSixthMigration(map: MutableMap<String, Any?>) {
    if (oldRealm.schemaVersion() <= 6L) {
        enumerate(className = "Mailbox") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {

                // Add property with default value
                setSafe(propertyName = "_isValidInLdap", value = true, map = map)

                // Rename property without losing its previous value
                setSafe(
                    propertyName = "_isLocked",
                    value = oldObject.getValueSafeOrDefault<Boolean>(fieldName = "isLocked", map, false),
                    map = map,
                )

                // Rename property without losing its previous value
                setSafe(
                    propertyName = "hasValidPassword",
                    value = oldObject.getValueSafeOrDefault<Boolean>(fieldName = "isPasswordValid", map, true),
                    map = map,
                )
            }
        }
    }
}

// Migrate from version #19
private fun MigrationContext.keepDefaultValuesAfterNineteenthMigration(map: MutableMap<String, Any?>) {

    if (oldRealm.schemaVersion() <= 19L) {

        enumerate(className = "Folder") { _: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Add property with default value
                setSafe(propertyName = "isDisplayed", value = true, map = map)
            }
        }

        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Rename property without losing its previous value
                setSafe(
                    propertyName = "isScheduledMessage",
                    value = oldObject.getValueSafeOrDefault<Boolean>(fieldName = "isScheduled", map, false),
                    map = map
                )
            }
        }

    }
}
//endregion

// Migrate from version #22
private fun MigrationContext.initializeInternalDateAsDateAfterTwentySecondMigration(map: MutableMap<String, Any?>) {

    if (oldRealm.schemaVersion() <= 22L) {
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                setSafe(
                    propertyName = "internalDate",
                    value = oldObject.getValueSafeOrDefault<RealmInstant>(fieldName = "date", map, Date().toRealmInstant()),
                    map = map
                )

                // Initialize new property with old property value
                setSafe(
                    propertyName = "originalDate",
                    value = oldObject.getValueSafeOrDefault<RealmInstant>(fieldName = "date", map, Date().toRealmInstant()),
                    map = map
                )
            }
        }

        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                setSafe(
                    propertyName = "internalDate",
                    value = oldObject.getValueSafeOrDefault<RealmInstant>(fieldName = "date", map, Date().toRealmInstant()),
                    map = map
                )

                // Initialize new property with old property value
                setSafe(
                    propertyName = "originalDate",
                    value = oldObject.getValueSafeOrDefault<RealmInstant>(fieldName = "date", map, Date().toRealmInstant()),
                    map = map
                )
            }
        }
    }
}
//endregion

// Migrate from version #24
private fun MigrationContext.replaceOriginalDateWithDisplayDateAfterTwentyFourthMigration(map: MutableMap<String, Any?>) {

    if (oldRealm.schemaVersion() <= 24L) {
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                val displayDate = oldObject.getNullableValueSafeOrDefault<RealmInstant>(
                    fieldName = "originalDate",
                    map,
                    Date().toRealmInstant()
                )
                    ?: oldObject.getValueSafeOrDefault<RealmInstant>(fieldName = "internalDate", map, Date().toRealmInstant())

                setSafe(propertyName = "displayDate", value = displayDate, map = map)
            }
        }

        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                val displayDate = oldObject.getNullableValueSafeOrDefault<RealmInstant>(
                    fieldName = "originalDate",
                    map,
                    Date().toRealmInstant(),
                )
                    ?: oldObject.getValueSafeOrDefault<RealmInstant>(fieldName = "internalDate", map, Date().toRealmInstant())

                setSafe(propertyName = "displayDate", value = displayDate, map = map)
            }
        }
    }
}
//endregion

// Migrate from version #25
private fun MigrationContext.deserializeSnoozeUuidDirectlyAfterTwentyFifthMigration(map: MutableMap<String, Any?>) {

    if (oldRealm.schemaVersion() <= 25L) {
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                val snoozeAction = oldObject.getNullableValueSafeOrDefault<String>(fieldName = "snoozeAction", map, null)
                val snoozeUuid = snoozeAction?.lastUuidOrNull()
                setSafe(propertyName = "snoozeUuid", value = snoozeUuid, map = map)
            }
        }

        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                val snoozeAction = oldObject.getNullableValueSafeOrDefault<String>(fieldName = "snoozeAction", map, null)
                val snoozeUuid = snoozeAction?.lastUuidOrNull()
                setSafe(propertyName = "snoozeUuid", value = snoozeUuid, map = map)
            }
        }
    }
}

private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
private val lastUuidRegex = Regex("""$UUID_PATTERN(?!.*$UUID_PATTERN)""", RegexOption.IGNORE_CASE)
private fun String.lastUuidOrNull() = lastUuidRegex.find(this)?.value
//endregion

// Migrate from version #27
private fun MigrationContext.initIsLastInboxMessageSnoozedAfterTwentySeventhMigration(map: MutableMap<String, Any?>) {

    if (oldRealm.schemaVersion() <= 27L) {
        enumerate(className = "Thread") { _: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.let { newThread ->
                // Initialize new property by computing it based on other fields
                val threadFolderId = newObject.getValueSafeOrDefault<String>("folderId", map, "")

                // TODO: getObjectListSafe
                val messages = newThread.getObjectList(propertyName = "messages")
                messages.sortBy { it.getValue<RealmInstant>("internalDate") }
                val lastMessage = messages.lastOrNull { it.getValueSafeOrDefault<String>("folderId", map, "") == threadFolderId }

                val isSnoozed = if (lastMessage == null) {
                    // Defaulting to `false` only means that this thread won't be automatically unsnoozed until it's recomputed
                    false
                } else {
                    val snoozeState = lastMessage.getNullableValueSafeOrDefault<String>("_snoozeState", map, null)
                    val snoozeEndDate = lastMessage.getNullableValueSafeOrDefault<RealmInstant>("snoozeEndDate", map, null)
                    val snoozeUuid = lastMessage.getNullableValueSafeOrDefault<String>("snoozeUuid", map, null)

                    snoozeState == SnoozeState.Snoozed.apiValue && snoozeEndDate != null && snoozeUuid != null
                }

                newThread.setSafe(propertyName = "isLastInboxMessageSnoozed", value = isSnoozed, map = map)
            }
        }
    }
}
//endregion

/**
 * If the property we're trying to set doesn't exist anymore in our model at the latest schema version, instead of crashing, cache
 * the data locally inside a shared [map] where future migrations can retrieve this intermediate value. Future migrations will
 * fail to read this value from the old model's values as well and will refer to [map] to read the last updated intermediate value
 */
fun DynamicMutableRealmObject.setSafe(propertyName: String, value: Any?, map: MutableMap<String, Any?>) {
    runCatching {
        set(propertyName, value)
    }.onFailure {
        if (it !is IllegalArgumentException) {
            // TODO
            SentryLog.e(TAG, "Unexpected exception thrown during realm migration", it)
        }
        map[propertyName] = value
    }
}

// private inline fun <reified T : Any> DynamicRealmObject.getValueSafe(fieldName: String, defaultValue: T): T {
//     return getOrDefault(defaultValue) { getValue<T>(fieldName) }
// }
//
// private inline fun <reified T : Any> DynamicRealmObject.getNullableValueSafe(fieldName: String, defaultValue: T): T? {
//     return getNullableOrDefault<T>(defaultValue) { getNullableValue<T>(fieldName) }
// }

private inline fun <reified T : Any> DynamicRealmObject.getValueSafeOrDefault(
    fieldName: String,
    map: MutableMap<String, Any?>,
    defaultValue: T,
): T {
    return getOrDefault<T>(
        lazyDefaultValue = {
            if (map.containsKey(fieldName)) map[fieldName] as T else defaultValue.also {
                Log.v("gibran", "getValueSafeOrDefault - used default value for fieldName: ${fieldName}")
            }
        }
    ) {
        getValue<T>(fieldName)
    }
}

private inline fun <reified T : Any> DynamicRealmObject.getNullableValueSafeOrDefault(
    fieldName: String,
    map: MutableMap<String, Any?>,
    defaultValue: T?,
): T? {
    return getNullableOrDefault<T>(
        lazyDefaultValue = {
            if (map.containsKey(fieldName)) map[fieldName] as T? else defaultValue.also {
                Log.v("gibran", "getNullableValueSafeOrDefault - used default value for fieldName: ${fieldName}")
            }
        }
    ) {
        getNullableValue<T>(fieldName)
    }
}

private inline fun <reified T : Any> DynamicRealmObject.getOrDefault(lazyDefaultValue: () -> T, block: () -> T): T {
    return runCatching {
        block()
    }.getOrElse {
        if (it !is IllegalArgumentException) {
            // TODO
            SentryLog.e(TAG, "Unexpected exception thrown during realm migration", it)
        }
        lazyDefaultValue()
    }
}

private inline fun <reified T : Any> DynamicRealmObject.getNullableOrDefault(lazyDefaultValue: () -> T?, block: () -> T?): T? {
    return runCatching {
        block()
    }.getOrElse {
        if (it !is IllegalArgumentException) {
            // TODO
            SentryLog.e(TAG, "Unexpected exception thrown during realm migration", it)
        }
        lazyDefaultValue()
    }
}

private const val TAG = "RealmMigrations"
