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
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.migration.AutomaticSchemaMigration.MigrationContext

val USER_INFO_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
}

val MAILBOX_INFO_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
}

val MAILBOX_CONTENT_MIGRATION = AutomaticSchemaMigration { migrationContext ->
    SentryDebug.addMigrationBreadcrumb(migrationContext)
    migrationContext.deleteRealmFromFirstMigration()
}

private fun MigrationContext.deleteRealmFromFirstMigration() {
    if (oldRealm.schemaVersion() == 0L) newRealm.deleteAll()
}
