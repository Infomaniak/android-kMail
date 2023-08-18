/*
 * Infomaniak ikMail - Android
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
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.getAppName
import com.infomaniak.lib.core.utils.goToPlaystore
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@AndroidEntryPoint
class UpdateAvailableBottomSheetDialog : InformationBottomSheetDialog() {

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        title.setText(RCore.string.updateAvailableTitle)
        description.text = resources.getString(RCore.string.updateAvailableDescription, context.getAppName())
        infoIllustration.setBackgroundResource(R.drawable.ic_update_logo)

        actionButton.apply {
            setText(RCore.string.buttonUpdate)
            setOnClickListener {
                localSettings.updateLater = false
                requireContext().goToPlaystore()
                dismiss()
            }
        }

        secondaryActionButton.setOnClickListener {
            localSettings.updateLater = true
            dismiss()
        }
    }
}
