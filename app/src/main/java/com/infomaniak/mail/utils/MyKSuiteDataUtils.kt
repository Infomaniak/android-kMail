/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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

import com.infomaniak.core.auth.networking.HttpClient
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.ksuite.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.ksuite.myksuite.ui.data.MyKSuiteDataManager
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MyKSuiteDataUtils @Inject constructor(private val mailboxController: MailboxController) : MyKSuiteDataManager() {

    override val currentUserId get() = AccountUtils.currentUserId

    override var myKSuite: MyKSuiteData? = null

    override suspend fun fetchData(): MyKSuiteData? = runCatching {
        requestKSuiteData()
        // Only fetch the Data if the current user has a my kSuite mailbox
        if (mailboxController.getMyKSuiteMailboxCount(userId = AccountUtils.currentUserId) == 0L) return@runCatching null

        val apiResponse = ApiRepository.getMyKSuiteData(HttpClient.okHttpClientWithTokenInterceptor)
        if (apiResponse.data != null) {
            upsertKSuiteData(apiResponse.data!!)
        } else {
            @OptIn(ExperimentalSerializationApi::class)
            apiResponse.error?.exception?.let {
                if (it is MissingFieldException) SentryLog.e(TAG, "Error decoding the api result MyKSuiteObject", it)
            }
        }

        return@runCatching apiResponse.data
    }.cancellable().getOrElse { exception ->
        SentryLog.d(TAG, "Exception during myKSuite data fetch", exception)
        null
    }

    companion object {

        private val TAG = MyKSuiteDataUtils::class.simpleName.toString()
    }
}
