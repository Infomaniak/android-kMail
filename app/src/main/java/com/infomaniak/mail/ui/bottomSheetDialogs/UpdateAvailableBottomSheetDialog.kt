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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.getAppName
import com.infomaniak.lib.core.utils.goToPlayStore
import com.infomaniak.lib.stores.StoresSettingsRepository
import com.infomaniak.lib.stores.StoresViewModel
import com.infomaniak.mail.MatomoMail.DISCOVER_LATER
import com.infomaniak.mail.MatomoMail.DISCOVER_NOW
import com.infomaniak.mail.MatomoMail.trackAppUpdateEvent
import com.infomaniak.mail.R
import com.infomaniak.lib.core.R as RCore

class UpdateAvailableBottomSheetDialog : InformationBottomSheetDialog() {

    private val storesViewModel: StoresViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        title.setText(RCore.string.updateAvailableTitle)
        description.text = resources.getString(RCore.string.updateAvailableDescription, context.getAppName())
        infoIllustration.setBackgroundResource(R.drawable.ic_update_logo)

        actionButton.apply {
            trackAppUpdateEvent(DISCOVER_NOW)
            setText(RCore.string.buttonUpdate)
            setOnClickListener {
                storesViewModel.set(StoresSettingsRepository.IS_USER_WANTING_UPDATES_KEY, true)
                requireContext().goToPlayStore()
                dismiss()
            }
        }

        secondaryActionButton.setOnClickListener {
            trackAppUpdateEvent(DISCOVER_LATER)
            storesViewModel.set(StoresSettingsRepository.IS_USER_WANTING_UPDATES_KEY, false)
            dismiss()
        }
    }
}
