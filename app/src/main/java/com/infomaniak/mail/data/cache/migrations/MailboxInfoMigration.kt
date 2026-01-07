/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import com.infomaniak.mail.utils.SentryDebug
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.migration.AutomaticSchemaMigration.MigrationContext

fun mailboxInfoMigration() = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
    migrationContext.keepDefaultValuesAfterSixthMigration()
    migrationContext.renameKSuiteRelatedBooleans()
    migrationContext.revertKSuiteRelatedBooleanRenaming()
    migrationContext.keepDefaultValuesAfterTwelveMigration()
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