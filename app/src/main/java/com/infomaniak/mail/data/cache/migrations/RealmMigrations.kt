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
package com.infomaniak.mail.data.cache.migrations

import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.utils.SentryDebug
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getNullableValue
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.migration.AutomaticSchemaMigration.MigrationContext

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

/**
 * If the property we're trying to set doesn't exist anymore in our model at the latest schema version, instead of crashing skip
 * this property value.
 */
internal fun DynamicMutableRealmObject.setIfPropertyExists(propertyName: String, value: Any?) {
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
internal inline fun <reified T : Any> DynamicRealmObject.getNullableValueOrRecover(fieldName: String, recovery: () -> T?): T? {
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
internal inline fun <reified T : Any> DynamicRealmObject.getValueOrNull(fieldName: String): T? {
    return runCatching {
        getValue<T>(fieldName)
    }.getOrElse {
        if (it !is IllegalArgumentException) SentryLog.e(TAG, "Unexpected exception thrown during realm migration", it)

        null
    }
}
