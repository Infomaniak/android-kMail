/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

sealed interface AvailableService {
    sealed class DisplayUnavailableService(@StringRes val title: Int, @DrawableRes val icon: Int) : AvailableService {
        data object NetworkNotAvailable : DisplayUnavailableService(
            com.infomaniak.mail.R.string.noNetwork,
            com.infomaniak.mail.R.drawable.ic_no_network
        )
        data object ServerNotAvailable : DisplayUnavailableService(
            com.infomaniak.mail.R.string.serverUnavailable,
            com.infomaniak.mail.R.drawable.ic_cloud_slash
        )
    }

    data object AllAvailable : AvailableService
}
