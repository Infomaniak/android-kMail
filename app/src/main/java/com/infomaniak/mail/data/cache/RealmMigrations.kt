/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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

import com.infomaniak.mail.utils.SentryDebug
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.migration.AutomaticSchemaMigration.MigrationContext

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
    migrationContext.keepThreadsInFolderAfterNineteenthMigration()
}

// Migrate to version #1
private fun MigrationContext.deleteRealmFromFirstMigration() {
    if (oldRealm.schemaVersion() < 1L) newRealm.deleteAll()
}

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

/**
 * Migrate from version #19
 */
private fun MigrationContext.keepThreadsInFolderAfterNineteenthMigration() {
    if (oldRealm.schemaVersion() <= 19L) {
        enumerate(className = "Folder") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.apply {
                // Backup previous value
                set(
                    propertyName = "threads",
                    value = oldObject.getObjectList(propertyName = "threads").mapTo(realmSetOf(), newRealm::findLatest),
                )
            }
        }
    }
}
