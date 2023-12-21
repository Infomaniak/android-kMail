/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.views.itemViews

import androidx.lifecycle.asLiveData
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.ContactUtils
import io.realm.kotlin.ext.copyFromRealm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AvatarMergedContactData @Inject constructor(
    mergedContactController: MergedContactController,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    val mergedContactLiveData = mergedContactController
        .getMergedContactsAsync()
        .mapLatest { ContactUtils.arrangeMergedContacts(it.list.copyFromRealm()) }
        .asLiveData(ioDispatcher) // TODO : was ioCoroutineContext
}
