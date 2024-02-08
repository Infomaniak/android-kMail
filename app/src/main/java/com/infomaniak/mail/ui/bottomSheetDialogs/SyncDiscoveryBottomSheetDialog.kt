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
package com.infomaniak.mail.ui.bottomSheetDialogs

import com.infomaniak.mail.MatomoMail.trackSyncAutoConfigEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.extensions.launchSyncAutoConfigActivityForResult

class SyncDiscoveryBottomSheetDialog : DiscoveryBottomSheetDialog() {

    override val titleRes = R.string.syncCalendarsAndContactsTitle
    override val descriptionRes = R.string.syncCalendarsAndContactsDescription
    override val illustrationRes = R.drawable.illustration_discover_sync
    override val positiveButtonRes = R.string.buttonStart
    override val trackMatomoWithCategory: (name: String) -> Unit = { trackSyncAutoConfigEvent(it) }

    override fun onPositiveButtonClicked() {
        launchSyncAutoConfigActivityForResult()
    }
}
