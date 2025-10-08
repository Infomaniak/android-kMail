/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
package com.infomaniak.mail.firebase

import com.infomaniak.mail.utils.IFirebaseNotificationReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object FirebaseNotificationReceiver : IFirebaseNotificationReceiver {

    private val _refreshThreadInForegroundTrigger = MutableSharedFlow<Unit>()
    override val refreshThreadInForegroundTrigger: SharedFlow<Unit>? = _refreshThreadInForegroundTrigger.asSharedFlow()

    fun emitNotificationTrigger() {
        CoroutineScope(Dispatchers.Default).launch { _refreshThreadInForegroundTrigger.emit(Unit) }
    }
}
