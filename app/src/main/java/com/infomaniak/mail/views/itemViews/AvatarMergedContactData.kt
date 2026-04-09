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
package com.infomaniak.mail.views.itemViews

import androidx.lifecycle.asLiveData
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ContactUtils
import com.infomaniak.mail.utils.coroutineContext
import io.realm.kotlin.ext.copyFromRealm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AvatarMergedContactData @Inject constructor(
    mergedContactController: MergedContactController,
    mailboxController: MailboxController,
    globalCoroutineScope: CoroutineScope,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    private val ioCoroutineContext = globalCoroutineScope.coroutineContext(ioDispatcher)

    val mergedContactFlow: StateFlow<Map<String, Map<String, MergedContact>>> = mergedContactController
        .getMergedContactsAsync()
        .mapLatest { ContactUtils.arrangeMergedContacts(it.list.copyFromRealm()) }
        .distinctUntilChanged()
        .stateIn(
            scope = globalCoroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val isBimiEnabledFlow: StateFlow<Boolean> = mailboxController
        .getMailboxAsync(AccountUtils.currentUserId, AccountUtils.currentMailboxId)
        .mapLatest { it.obj?.featureFlags?.contains(FeatureFlag.BIMI) ?: false }
        .filterNotNull()
        .distinctUntilChanged()
        .stateIn(
            scope = globalCoroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val mergedContactLiveData = mergedContactController
        .getMergedContactsAsync()
        .mapLatest { ContactUtils.arrangeMergedContacts(it.list.copyFromRealm()) }
        .asLiveData(ioCoroutineContext)

    val isBimiEnabledLiveData = mailboxController
        .getMailboxAsync(AccountUtils.currentUserId, AccountUtils.currentMailboxId)
        .mapLatest { it.obj?.featureFlags?.contains(FeatureFlag.BIMI) }
        .filterNotNull()
        .distinctUntilChanged()
        .asLiveData(ioCoroutineContext)
}
