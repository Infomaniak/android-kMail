/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import android.content.Context
import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.data.models.Folder.FolderRole
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.internal.toHexString
import java.nio.charset.StandardCharsets

object Utils {

    val UTF_8: String = StandardCharsets.UTF_8.name()
    const val TEXT_HTML = "text/html"
    const val TEXT_PLAIN = "text/plain"
    const val EML_CONTENT_TYPE = "message/rfc822"
    /** The MIME type for data whose type is otherwise unknown. */
    const val MIMETYPE_UNKNOWN = "application/octet-stream"

    const val NUMBER_OF_OLD_UIDS_TO_FETCH = 10_000 // Total number of Messages we will ever fetch in a Folder history
    const val NUMBER_OF_OLD_MESSAGES_TO_FETCH = 500 // Number of Messages we want to fetch when 1st opening a Folder
    const val PAGE_SIZE = 50 // Beware: the API refuses a PAGE_SIZE bigger than 200
    const val MIN_THREADS_TO_GET_ENOUGH_THREADS = PAGE_SIZE / 2 // We want to get at least 25 Threads when we fetch 1 old page
    const val MAX_OLD_PAGES_TO_FETCH_TO_GET_ENOUGH_THREADS = 5 // We don't want to spam the API, so we just get a few pages

    const val MAX_UIDS_PER_CALL = 1_000 // Beware: the API refuses a MAX_UIDS_PER_CALL bigger than 1000
    const val MAX_UUIDS_PER_CALL_SNOOZE = 100 // Only for DELETE and PUT
    const val MAX_UUIDS_PER_CALL_SNOOZE_POST = 200 // Only for POST
    const val MAX_DELTA_PER_ACTIVITIES_CALL = 10_000 // If we received too much updates, we just delete the Folder and start again

    const val TAG_SEPARATOR = " "

    const val SCHEME_SMSTO = "smsto:"

    fun colorToHexRepresentation(color: Int) = "#" + color.toHexString().substring(2 until 8)

    fun isPermanentDeleteFolder(role: FolderRole?): Boolean = when (role) {
        FolderRole.SCHEDULED_DRAFTS,
        FolderRole.DRAFT,
        FolderRole.SPAM,
        FolderRole.TRASH -> true
        else -> false
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
        block: suspend CoroutineScope.() -> T,
        onTimeout: (() -> Unit)? = null,
    ): T = runCatching {
        withTimeout(timeout, block)
    }.getOrElse {
        if (it is TimeoutCancellationException) onTimeout?.invoke() else Sentry.captureException(it)
        defaultValue
    }

    fun <T1, T2> waitInitMediator(liveData1: LiveData<T1>, liveData2: LiveData<T2>): MediatorLiveData<Pair<T1, T2>> {
        return waitInitMediator(
            liveData1,
            liveData2,
            constructor = {
                @Suppress("UNCHECKED_CAST")
                it[0] as T1 to it[1] as T2
            },
        )
    }

    fun <T> waitInitMediator(vararg liveData: LiveData<*>, constructor: (List<Any>) -> T): MediatorLiveData<T> {
        return MediatorLiveData<T>().apply {
            liveData.forEach { singleLiveData ->
                addSource(singleLiveData) {
                    if (liveData.all { it.isInitialized }) {
                        val values = liveData.map { it.value }
                        @Suppress("UNCHECKED_CAST")
                        postValue(constructor(values as List<Any>))
                    }
                }
            }
        }
    }

    fun openShortcutHelp(context: Context) {
        ShortcutManagerCompat.reportShortcutUsed(context, Shortcuts.SUPPORT.id)
        context.openUrl(BuildConfig.CHATBOT_URL)
    }

    fun isRunningInTest(): Boolean {
        return try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    enum class MailboxErrorCode {
        NO_MAILBOX,
        NO_VALID_MAILBOX,
    }

    enum class Shortcuts(val id: String) {
        NEW_MESSAGE("newMessage"),
        SUPPORT("support"),
        SEARCH("search"),
    }
}
