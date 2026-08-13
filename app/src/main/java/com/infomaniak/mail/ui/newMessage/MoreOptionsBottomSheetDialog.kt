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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.mail.databinding.BottomSheetMoreOptionsBinding
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_EMAIL_TEMPLATES_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.actions.ActionsBottomSheetDialog
import com.infomaniak.mail.ui.main.thread.actions.multiselection.MultiselectionViewModel

class MoreOptionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetMoreOptionsBinding by safeBinding()
    override val multiselectionViewModel: MultiselectionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetMoreOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sendOptions.setOnClickListener {/* TODO: Complete when send options are implemented */ }
        binding.sendOptions.setOnClickListener {/* TODO: Complete when send options are implemented */ }
    }
}
