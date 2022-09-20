/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.mail.databinding.BottomSheetDetailedContactBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.UiUtils.fillInUserNameAndEmail
import com.infomaniak.mail.utils.notYetImplemented

class DetailedContactBottomSheetDialog : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val navigationArgs: DetailedContactBottomSheetDialogArgs by navArgs()
    lateinit var binding: BottomSheetDetailedContactBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetDetailedContactBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        val recipient = navigationArgs.recipient
        userAvatar.loadAvatar(recipient, viewModel.mergedContacts.value ?: emptyMap())
        fillInUserNameAndEmail(navigationArgs.recipient, name, email)

        writeMail.setOnClickListener { notYetImplemented() }
        addToContacts.setOnClickListener { notYetImplemented() }
        copyAddress.setOnClickListener { notYetImplemented() }
    }
}
