/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.infomaniak.mail.data.models.Folder.FolderRole
import io.sentry.Sentry
import kotlinx.coroutines.*
import okhttp3.internal.toHexString
import java.nio.charset.StandardCharsets

object Utils {

    val UTF_8: String = StandardCharsets.UTF_8.name()
    const val TEXT_HTML: String = "text/html"
    const val TEXT_PLAIN: String = "text/plain"
    /** The MIME type for data whose type is otherwise unknown. */
    const val MIMETYPE_UNKNOWN = "application/octet-stream"

    const val NUMBER_OF_OLD_MESSAGES_TO_FETCH = 500
    /** Beware: the API refuses a PAGE_SIZE bigger than 200. */
    const val PAGE_SIZE: Int = 50
    const val MAX_DELAY_BETWEEN_API_CALLS = 500L
    const val DELAY_BEFORE_FETCHING_ACTIVITIES_AGAIN = 500L

    const val TAG_SEPARATOR = " "

    fun colorToHexRepresentation(color: Int) = "#" + color.toHexString().substring(2 until 8)

    fun isPermanentDeleteFolder(role: FolderRole?): Boolean {
        return role == FolderRole.DRAFT || role == FolderRole.SPAM || role == FolderRole.TRASH
    }

    fun kSyncAccountUri(accountName: String): Uri = "content://com.infomaniak.sync.accounts/account/$accountName".toUri()

    inline fun <R> runCatchingRealm(block: () -> R): Result<R> {
        return runCatching { block() }.onFailure { exception ->
            if (!exception.shouldIgnoreRealmError()) Sentry.captureException(exception)
            exception.printStackTrace()
        }
    }

    suspend fun <T> executeWithTimeoutOrDefault(
        timeout: Long,
        defaultValue: T,
        block: CoroutineScope.() -> T,
        onTimeout: (() -> Unit)? = null,
    ): T = runCatching {
        coroutineWithTimeout(timeout, block)
    }.getOrElse {
        if (it is TimeoutCancellationException) onTimeout?.invoke() else Sentry.captureException(it)
        defaultValue
    }

    private suspend fun <T> coroutineWithTimeout(
        timeout: Long,
        block: CoroutineScope.() -> T,
    ): T = withTimeout(timeout) {
        var result: T? = null
        CoroutineScope(Dispatchers.Default).launch { result = block() }.join()
        result!!
    }


    // A Mediator of the type 'non null' acts as a normal mediator but will only trigger if all values are non-null
    fun <R> nonNullMediator(vararg liveData: LiveData<*>, constructor: (List<Any>) -> R): MediatorLiveData<R> {
        return MediatorLiveData<R>().apply {
            liveData.forEach { singleLiveData ->
                addSource(singleLiveData) {
                    val values = liveData.map { it.value }
                    if (values.all { it != null }) postValue(constructor(values as List<Any>))
                }
            }
        }
    }

    // Helper method to automatically wrap two live data inside a Pair
    fun <T1, T2> nonNullMediator(liveData1: LiveData<T1>, liveData2: LiveData<T2>): MediatorLiveData<Pair<T1, T2>> {
        return nonNullMediator(liveData1, liveData2) { (value1, value2) ->
            Pair(value1 as T1, value2 as T2)
        }
    }


    // Helper method to automatically wrap three live data inside a Triple
    fun <T1, T2, T3> nonNullMediator(
        liveData1: LiveData<T1>,
        liveData2: LiveData<T2>,
        liveData3: LiveData<T3>,
    ): MediatorLiveData<Triple<T1, T2, T3>> {
        return nonNullMediator(liveData1, liveData2, liveData3) { (value1, value2, value3) ->
            Triple(value1 as T1, value2 as T2, value3 as T3)
        }
    }

    enum class MailboxErrorCode {
        NO_MAILBOX,
        NO_VALID_MAILBOX,
    }
}
