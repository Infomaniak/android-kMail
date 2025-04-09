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

import com.infomaniak.lib.core.utils.SentryLog
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
    val map = mutableMapOf<String, MutableList<DynamicRealmObject.() -> Any?>>()
    migrationContext.keepDefaultValuesAfterSixthMigration(map)
}

val MAILBOX_CONTENT_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
    val map = mutableMapOf<String, MutableList<DynamicRealmObject.() -> Any?>>()
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
private fun MigrationContext.keepDefaultValuesAfterSixthMigration(map: MutableMap<String, MutableList<DynamicRealmObject.() -> Any?>>) {
    if (oldRealm.schemaVersion() <= 6L) {
        var isFirstInstance = true
        enumerate(className = "Mailbox") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {

                // Add property with default value
                setSafe(propertyName = "_isValidInLdap", oldObject, howToGetTheValueFromOldObject = { true }, map = map, isFirstInstance)

                // Rename property without losing its previous value
                setSafe(
                    propertyName = "_isLocked",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = { getValueSafe<Boolean>(fieldName = "isLocked", oldObject, map) },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                // Rename property without losing its previous value
                setSafe(
                    propertyName = "hasValidPassword",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = { getValueSafe<Boolean>(fieldName = "isPasswordValid", oldObject, map) },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                isFirstInstance = false
            }
        }
    }
}

// Migrate from version #19
private fun MigrationContext.keepDefaultValuesAfterNineteenthMigration(map: MutableMap<String, MutableList<DynamicRealmObject.() -> Any?>>) {

    if (oldRealm.schemaVersion() <= 19L) {

        var isFirstInstance = true
        enumerate(className = "Folder") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Add property with default value
                setSafe(propertyName = "isDisplayed", oldObject, howToGetTheValueFromOldObject = { true }, map = map, isFirstInstance)

                isFirstInstance = false
            }
        }

        isFirstInstance = true
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Rename property without losing its previous value
                setSafe(
                    propertyName = "isScheduledMessage",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = { getValueSafe<Boolean>(fieldName = "isScheduled", oldObject, map) },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                isFirstInstance = false
            }
        }

    }
}
//endregion

// Migrate from version #22
private fun MigrationContext.initializeInternalDateAsDateAfterTwentySecondMigration(map: MutableMap<String, MutableList<DynamicRealmObject.() -> Any?>>) {

    if (oldRealm.schemaVersion() <= 22L) {
        var isFirstInstance = true
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                setSafe(
                    propertyName = "internalDate",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = { getValueSafe<RealmInstant>(fieldName = "date", oldObject, map) },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                // Initialize new property with old property value
                setSafe(
                    propertyName = "originalDate",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = { getValueSafe<RealmInstant>(fieldName = "date", oldObject, map) },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                isFirstInstance = false
            }
        }

        isFirstInstance = true
        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                setSafe(
                    propertyName = "internalDate",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = { getValueSafe<RealmInstant>(fieldName = "date", oldObject, map) },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                // Initialize new property with old property value
                setSafe(
                    propertyName = "originalDate",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = { getValueSafe<RealmInstant>(fieldName = "date", oldObject, map) },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                isFirstInstance = false
            }
        }
    }
}
//endregion

// Migrate from version #24
private fun MigrationContext.replaceOriginalDateWithDisplayDateAfterTwentyFourthMigration(map: MutableMap<String, MutableList<DynamicRealmObject.() -> Any?>>) {

    if (oldRealm.schemaVersion() <= 24L) {
        var isFirstInstance = true
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value

                setSafe(
                    propertyName = "displayDate",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = {
                        getNullableValueSafe<RealmInstant>(fieldName = "originalDate", oldObject, map)
                            ?: getValueSafe<RealmInstant>(fieldName = "internalDate", oldObject, map)
                    },
                    map = map,
                    isFirstInstance = isFirstInstance
                )

                isFirstInstance = false
            }
        }

        isFirstInstance = true
        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                setSafe(
                    propertyName = "displayDate",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = {
                        getNullableValueSafe<RealmInstant>(fieldName = "originalDate", oldObject, map)
                            ?: getValueSafe<RealmInstant>(fieldName = "internalDate", oldObject, map)
                    },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                isFirstInstance = false
            }
        }
    }
}
//endregion

// Migrate from version #25
private fun MigrationContext.deserializeSnoozeUuidDirectlyAfterTwentyFifthMigration(map: MutableMap<String, MutableList<DynamicRealmObject.() -> Any?>>) {

    if (oldRealm.schemaVersion() <= 25L) {
        var isFirstInstance = true
        enumerate(className = "Message") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                setSafe(
                    propertyName = "snoozeUuid",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = {
                        val snoozeAction = getNullableValueSafe<String>(fieldName = "snoozeAction", oldObject, map)
                        snoozeAction?.lastUuidOrNull()
                    },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                isFirstInstance = false
            }
        }

        isFirstInstance = true
        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Initialize new property with old property value
                setSafe(
                    propertyName = "snoozeUuid",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = {
                        val snoozeAction = getNullableValueSafe<String>(fieldName = "snoozeAction", oldObject, map)
                        snoozeAction?.lastUuidOrNull()
                    },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                isFirstInstance = false
            }
        }
    }
}

private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
private val lastUuidRegex = Regex("""$UUID_PATTERN(?!.*$UUID_PATTERN)""", RegexOption.IGNORE_CASE)
private fun String.lastUuidOrNull() = lastUuidRegex.find(this)?.value
//endregion

// Migrate from version #27
private fun MigrationContext.initIsLastInboxMessageSnoozedAfterTwentySeventhMigration(map: MutableMap<String, MutableList<DynamicRealmObject.() -> Any?>>) {

    if (oldRealm.schemaVersion() <= 27L) {
        var isFirstInstance = true
        enumerate(className = "Thread") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.let { newThread ->
                // Initialize new property by computing it based on other fields
                newThread.setSafe(
                    propertyName = "isLastInboxMessageSnoozed",
                    oldObject = oldObject,
                    howToGetTheValueFromOldObject = {
                        val threadFolderId = getValueSafe<String>("folderId", oldObject, map)

                        // TODO: getObjectListSafe
                        val messages = getObjectList(propertyName = "messages")
                        messages.sortBy { it.getValue<RealmInstant>("internalDate") }
                        val lastMessage = messages.lastOrNull { it.getValueSafe<String>("folderId", oldObject, map) == threadFolderId }

                        if (lastMessage == null) {
                            // Defaulting to `false` only means that this thread won't be automatically unsnoozed until it's recomputed
                            false
                        } else {
                            val snoozeState = lastMessage.getNullableValueSafe<String>("_snoozeState", oldObject, map)
                            val snoozeEndDate = lastMessage.getNullableValueSafe<RealmInstant>("snoozeEndDate", oldObject, map)
                            val snoozeUuid = lastMessage.getNullableValueSafe<String>("snoozeUuid", oldObject, map)

                            snoozeState == SnoozeState.Snoozed.apiValue && snoozeEndDate != null && snoozeUuid != null
                        }
                    },
                    map = map,
                    isFirstInstance = isFirstInstance,
                )

                isFirstInstance = false
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
fun DynamicMutableRealmObject.setSafe(
    propertyName: String,
    oldObject: DynamicRealmObject,
    howToGetTheValueFromOldObject: DynamicRealmObject.() -> Any?,
    map: MutableMap<String, MutableList<DynamicRealmObject.() -> Any?>>,
    isFirstInstance: Boolean,
) {
    runCatching {
        set(propertyName, oldObject.howToGetTheValueFromOldObject())
    }.onFailure {
        if (it !is IllegalArgumentException) {
            // TODO
            SentryLog.e(TAG, "Unexpected exception thrown during realm migration", it)
        }
        if (isFirstInstance) {
            map.getOrPut(propertyName, { mutableListOf() }) += howToGetTheValueFromOldObject
        }
    }
}

// private inline fun <reified T : Any> DynamicRealmObject.getValueSafe(fieldName: String, defaultValue: T): T {
//     return getOrDefault(defaultValue) { getValue<T>(fieldName) }
// }
//
// private inline fun <reified T : Any> DynamicRealmObject.getNullableValueSafe(fieldName: String, defaultValue: T): T? {
//     return getNullableOrDefault<T>(defaultValue) { getNullableValue<T>(fieldName) }
// }

private inline fun <reified T : Any> DynamicRealmObject.getValueSafe(fieldName: String, oldObject: DynamicRealmObject, map: MutableMap<String, MutableList<DynamicRealmObject.() -> Any?>>): T? {
    return getOrDefault<T>(lazyDefaultValue = {
        val howToGetTheValueFromOldObjectSteps = map[fieldName] as List<DynamicRealmObject.() -> T>
        var result: ReproductionStepsResult = ReproductionStepsResult.Failure

        for (step in howToGetTheValueFromOldObjectSteps.asReversed()) {
            result = runCatching {
                ReproductionStepsResult.Success(oldObject.step())
            }.getOrElse {
                ReproductionStepsResult.Failure
            }

            if (result is ReproductionStepsResult.Success<*>) break
        }

        (result as ReproductionStepsResult.Success<T>).value
    }) {
        getValue<T>(fieldName)
    }
}

sealed interface ReproductionStepsResult {
    data class Success<T>(val value: T) : ReproductionStepsResult
    data object Failure : ReproductionStepsResult
}

private inline fun <reified T : Any> DynamicRealmObject.getNullableValueSafe(
    fieldName: String,
    oldObject: DynamicRealmObject,
    map: MutableMap<String, MutableList<DynamicRealmObject.() -> Any?>>,
): T? {
    return getNullableOrDefault<T>(lazyDefaultValue = {
        val howToGetTheValueFromOldObjectSteps = map[fieldName] as List<DynamicRealmObject.() -> T?>
        var result: ReproductionStepsNullableResult = ReproductionStepsNullableResult.Failure

        for (step in howToGetTheValueFromOldObjectSteps.asReversed()) {
            result = runCatching {
                ReproductionStepsNullableResult.Success(oldObject.step())
            }.getOrElse {
                ReproductionStepsNullableResult.Failure
            }

            if (result is ReproductionStepsNullableResult.Success<*>) break
        }

        (result as ReproductionStepsNullableResult.Success<T?>).value
    }) {
        getNullableValue<T>(fieldName)
    }
}

sealed interface ReproductionStepsNullableResult {
    data class Success<T>(val value: T?) : ReproductionStepsNullableResult
    data object Failure : ReproductionStepsNullableResult
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
