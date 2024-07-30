/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
    /** The MIME type for data whose type is otherwise unknown. */
    const val MIMETYPE_UNKNOWN = "application/octet-stream"

    const val NUMBER_OF_OLD_MESSAGES_TO_FETCH = 500 // Number of Messages we want to fetch when 1st opening a Folder
    /** Beware: the API refuses a PAGE_SIZE bigger than 200. */
    const val PAGE_SIZE = 50
    const val DELAY_BEFORE_FETCHING_ACTIVITIES_AGAIN = 500L

    const val TAG_SEPARATOR = " "

    const val SCHEME_SMSTO = "smsto:"

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
