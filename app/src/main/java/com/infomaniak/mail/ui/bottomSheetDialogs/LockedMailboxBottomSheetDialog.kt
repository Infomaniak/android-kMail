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
import androidx.core.view.isGone
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.R

class LockedMailboxBottomSheetDialog : InformationBottomSheetDialog() {

    private val navigationArgs: LockedMailboxBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        title.text = getString(R.string.blockedMailboxTitle, navigationArgs.lockedMailboxName)
        description.text = resources.getQuantityText(R.plurals.lockedMailboxDescription, 1)
        infoIllustration.setBackgroundResource(R.drawable.ic_invalid_mailbox)

        actionButton.apply {
            setText(R.string.buttonClose)
            setOnClickListener { dismiss() }
        }

        secondaryActionButton.isGone = true
    }
}
