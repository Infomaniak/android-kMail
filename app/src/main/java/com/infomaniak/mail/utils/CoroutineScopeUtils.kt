/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import io.realm.kotlin.internal.interop.ErrorCode
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

val CoroutineScope.handler
    get() = CoroutineExceptionHandler { _, exception ->
        if (isActive) handleException(exception)
    }

val Job.handler
    get() = CoroutineExceptionHandler { _, exception ->
        if (isActive) handleException(exception)
    }

/** Ignore errors due to forced realm closure and notify sentry when necessary */
private fun handleException(exception: Throwable) {

    /** Ignore all errors due to voluntary realm closure
     * @return true if the error is recognized otherwise false
     **/
    fun shouldIgnoreRealmError(): Boolean = exception.message?.run {
        return contains(ErrorCode.RLM_ERR_CLOSED_REALM.name)
                || contains(ErrorCode.RLM_ERR_INVALIDATED_OBJECT.name)
                || contains(ErrorCode.RLM_ERR_INVALID_TABLE_REF.name)
                || contains(ErrorCode.RLM_ERR_STALE_ACCESSOR.name)
    } ?: false

    if (!shouldIgnoreRealmError()) {
        exception.printStackTrace()
        Sentry.captureException(exception)
    }
}

fun CoroutineScope.coroutineContext(dispatcher: CoroutineDispatcher): CoroutineContext = coroutineContext + handler + dispatcher
