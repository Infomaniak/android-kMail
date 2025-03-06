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
package com.infomaniak.mail.utils

import com.infomaniak.core.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.myksuite.ui.data.MyKSuiteDataManager
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlin.coroutines.cancellation.CancellationException

object MyKSuiteDataUtils : MyKSuiteDataManager() {

    private val TAG = MyKSuiteDataUtils::class.simpleName.toString()

    override val currentUserId get() = AccountUtils.currentUserId

    override var myKSuite: MyKSuiteData? = null

    /** Only call this if you are sure that you want to do the call without checking if it's necessary or not
     * To avoid useless call for account that does not have a my kSuite offer, use [fetchDataIfMyKSuite] */
    override suspend fun fetchData(): MyKSuiteData? = runCatching {
        MyKSuiteDataUtils.requestKSuiteData()
        val apiResponse = ApiRepository.getMyKSuiteData(HttpClient.okHttpClient)
        if (apiResponse.data != null) {
            MyKSuiteDataUtils.upsertKSuiteData(apiResponse.data!!)
        } else {
            @OptIn(ExperimentalSerializationApi::class)
            apiResponse.error?.exception?.let {
                if (it is MissingFieldException) SentryLog.e(TAG, "Error decoding the api result MyKSuiteObject", it)
            }
        }

        return@runCatching apiResponse.data
    }.getOrElse { exception ->
        if (exception is CancellationException) throw exception
        SentryLog.d(TAG, "Exception during myKSuite data fetch", exception)
        null
    }

    suspend fun fetchDataIfMyKSuite(mailboxController: MailboxController): MyKSuiteData? {
        // Only fetch the my kSuite Data if the current account has a my kSuite
        return if (mailboxController.getMyKSuiteMailboxCount(userId = AccountUtils.currentUserId) != 0L) fetchData() else null
    }
}
