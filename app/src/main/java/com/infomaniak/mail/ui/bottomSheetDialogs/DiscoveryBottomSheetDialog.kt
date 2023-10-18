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

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import com.infomaniak.mail.MatomoMail.DISCOVER_LATER

abstract class DiscoveryBottomSheetDialog : InformationBottomSheetDialog() {

    abstract val titleRes: Int
    abstract val descriptionRes: Int?
    abstract val illustrationRes: Int

    abstract val positiveButtonRes: Int

    abstract val trackMatomoWithCategory: (name: String) -> Unit

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        title.setText(titleRes)
        descriptionRes?.let(description::setText)
        infoIllustration.setBackgroundResource(illustrationRes)

        actionButton.apply {
            setText(positiveButtonRes)
            setOnClickListener {
                trackMatomoWithCategory("discoverNow")
                onPositiveButtonClicked()
                dismiss()
            }
        }

        secondaryActionButton.setOnClickListener {
            trackMatomoWithCategory(DISCOVER_LATER)
            dismiss()
        }
    }

    abstract fun onPositiveButtonClicked()

    override fun onCancel(dialog: DialogInterface) {
        trackMatomoWithCategory(DISCOVER_LATER)
        super.onCancel(dialog)
    }
}
