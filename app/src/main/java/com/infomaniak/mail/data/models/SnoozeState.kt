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
package com.infomaniak.mail.data.models

import com.infomaniak.core.common.utils.ApiEnum

enum class SnoozeState(override val apiValue: String) : ApiEnum {
    Snoozed(apiValue = "snoozed"), // Has been snoozed and the snooze end time has not been reached yet
    Unsnoozed(apiValue = "unsnoozed"), // Used to be snoozed but the snooze end time has been reached and the message came back
}
