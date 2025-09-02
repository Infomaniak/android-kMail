/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.databinding.BottomSheetUserToBlockBinding
import com.infomaniak.mail.ui.MainViewModel

class UserToBlockBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetUserToBlockBinding by safeBinding()

    override val mainViewModel: MainViewModel by activityViewModels()
    private val junkMessagesViewModel: JunkMessagesViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetUserToBlockBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        junkMessagesViewModel.potentialBlockedUsers.value?.let { potentialBlockedUsers ->
            if (potentialBlockedUsers.values.isNotEmpty()) {
                val messagesToRecipients = potentialBlockedUsers.map { (key, value) -> key to value }
                binding.recipients.adapter = UserToBlockAdapter(messagesToRecipients) { message ->
                    junkMessagesViewModel.messageOfUserToBlock.value = message
                    dismiss()
                }
            }
        } ?: findNavController().popBackStack()
    }
}
