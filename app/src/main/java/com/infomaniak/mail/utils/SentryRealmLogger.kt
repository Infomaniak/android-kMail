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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SentryRealmLogger : RealmLogger {

    private val mutex = Mutex()
    private val messagesMap = mutableMapOf<Long, MutableList<String>>()

    override val level = LogLevel.DEBUG
    override val tag = "Realm"

    override fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {

        val throwableMessage = throwable?.message
        if (throwableMessage != null) {
            val breadcrumb = Breadcrumb.error(throwableMessage).apply { category = "exception" }
            Sentry.addBreadcrumb(breadcrumb)
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            mutex.withLock {
                val now = System.currentTimeMillis() / MAX_DELAY

                message?.let {
                    if (messagesMap[now] == null) {
                        messagesMap[now] = mutableListOf(it)
                    } else {
                        messagesMap[now]?.add(it)
                    }
                }

                var shouldContinue = true
                while (shouldContinue) {
                    val key = messagesMap.keys.firstOrNull()
                    val values = messagesMap[key]
                    if (key != null && (key < now || values!!.count() > MAX_ZIP)) {
                        val breadcrumb = Breadcrumb().apply {
                            this.level = SentryLevel.INFO
                            this.category = tag
                            this.message = values?.joinToString(separator = "\n") { it }
                        }
                        Sentry.addBreadcrumb(breadcrumb)
                        messagesMap.remove(key)

                    } else {
                        shouldContinue = false
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_DELAY = 100
        private const val MAX_ZIP = 100
    }
}
