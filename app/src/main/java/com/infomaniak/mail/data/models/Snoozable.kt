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
package com.infomaniak.mail.data.models

import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.thread.Thread
import io.realm.kotlin.types.RealmInstant

interface Snoozable {
    val snoozeState: SnoozeState?
    var snoozeEndDate: RealmInstant?
    var snoozeUuid: String?
}

/**
 * Keep the snooze state condition of [Snoozable.isSnoozed] the same as
 * the condition used in [ThreadController.getThreadsWithSnoozeFilterQuery].
 * As in, check that [Thread.snoozeEndDate] and [Thread.snoozeUuid] are not null.
 */
fun Snoozable.isSnoozed() = snoozeState == SnoozeState.Snoozed && snoozeEndDate != null && snoozeUuid != null
