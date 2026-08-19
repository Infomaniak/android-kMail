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
package com.infomaniak.mail.ui.newMessage

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.databinding.BottomSheetMoreOptionsBinding
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_EMAIL_TEMPLATES_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.actions.ActionsBottomSheetDialog
import com.infomaniak.mail.ui.main.thread.actions.multiselection.MultiselectionViewModel

class MoreOptionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetMoreOptionsBinding by safeBinding()
    override val multiselectionViewModel: MultiselectionViewModel by activityViewModels()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    private val isScheduledDraftsEnabledLive by lazy {
        newMessageViewModel.featureFlagsLive.map { it.contains(FeatureFlag.SCHEDULE_DRAFTS) }.distinctUntilChanged()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetMoreOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeFeatureFlagUpdates()

        binding.scheduleSend.isEnabled = newMessageViewModel.isSendingAllowed.value == true

        // TODO: Open sendOptionsFragment instead of ScheduleSendBottomSheet when the new send options are implemented
        binding.scheduleSend.setOnClickListener { setBackNavigationResult(OPEN_SCHEDULE_SEND_BOTTOM_SHEET, true) }
        binding.emailTemplates.setOnClickListener { setBackNavigationResult(OPEN_EMAIL_TEMPLATES_BOTTOM_SHEET, true) }
    }

    private fun observeFeatureFlagUpdates() {
        isScheduledDraftsEnabledLive.observe(viewLifecycleOwner) { isScheduledDraftsEnabled ->
            binding.scheduleSend.isVisible = isScheduledDraftsEnabled
        }
    }

    companion object {
        const val OPEN_SCHEDULE_SEND_BOTTOM_SHEET = "openScheduleSendBottomSheet"
    }
}
