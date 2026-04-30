/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.folder

import android.app.Application
import android.text.format.DateUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.emojicomponents.data.Reaction
import com.infomaniak.mail.data.cache.mailboxContent.MessageController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.SearchUtils
import com.infomaniak.mail.utils.WebViewVersionUtils.getWebViewVersionData
import com.infomaniak.mail.utils.extensions.appContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ThreadListViewModel @Inject constructor(
    application: Application,
    private val messageController: MessageController,
    private val searchUtils: SearchUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private var updatedAtJob: Job? = null

    val isRecoveringFinished = MutableLiveData(true)
    val updatedAtTrigger = MutableLiveData<Unit>()
    val isWebViewOutdated = MutableLiveData(false)

    val contentDisplayMode = MutableLiveData(ContentDisplayMode.Threads)

    var currentFolderCursor: String? = null
    var currentThreadsCount: Int? = null

    fun startUpdatedAtJob() {
        updatedAtJob?.cancel()
        updatedAtJob = viewModelScope.launch(ioDispatcher) {
            while (true) {
                delay(DateUtils.MINUTE_IN_MILLIS)
                ensureActive()
                updatedAtTrigger.postValue(Unit)
            }
        }
    }

    fun deleteSearchData() = viewModelScope.launch(ioDispatcher) {
        // Delete Search data in case they couldn't be deleted at the end of the previous Search.
        searchUtils.deleteRealmSearchData()
    }

    fun checkWebViewVersion(canShowWebViewOutdated: Boolean) {
        val versionData = getWebViewVersionData(appContext)

        val hasOutdatedMajorVersion = if (versionData == null)
            false
        else {
            when (versionData.webViewPackageName) {
                WEBVIEW_OFFICIAL_PACKAGE_NAME -> versionData.majorVersion < WEBVIEW_OFFICIAL_MIN_VERSION
                else -> false // We'll add other package names in the future if needed here
            }
        }

        isWebViewOutdated.value = canShowWebViewOutdated && hasOutdatedMajorVersion
    }

    suspend fun getEmojiReactionsFor(messageUid: String): Map<String, Reaction>? = withContext(ioDispatcher) {
        messageController.getMessage(messageUid)?.let { message ->
            message.emojiReactions.associateBy { it.emoji }
        }
    }

    enum class ContentDisplayMode {
        Threads,
        NoNetwork,
        EmptyFolder,
    }

    companion object {
        private const val WEBVIEW_OFFICIAL_PACKAGE_NAME = "com.google.android.webview"
        private const val WEBVIEW_OFFICIAL_MIN_VERSION = 124
    }
}
