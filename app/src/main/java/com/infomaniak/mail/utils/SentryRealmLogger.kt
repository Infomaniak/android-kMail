/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

class SentryRealmLogger : RealmLogger {

    override val level: LogLevel = LogLevel.DEBUG

    override val tag: String = "Realm"

    override fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        val throwableMsg = throwable?.message
        val breadCrumb = when {
            throwableMsg != null -> Breadcrumb.error(throwableMsg).apply {
                category = "exception"
            }
            else -> Breadcrumb().apply {
                this.level = SentryLevel.INFO
                category = tag
                this.message = "($tag): $message"
            }
        }
        Sentry.addBreadcrumb(breadCrumb)
    }
}
