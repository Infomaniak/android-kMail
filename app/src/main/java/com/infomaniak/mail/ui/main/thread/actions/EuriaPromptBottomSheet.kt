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
package com.infomaniak.mail.ui.main.thread.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewAiPromptBinding
import com.infomaniak.mail.ui.bottomSheetDialogs.EdgeToEdgeBottomSheetDialog
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_AI_REPLY_PROPOSITION
import com.infomaniak.mail.ui.newMessage.AiViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EuriaPromptBottomSheet : EdgeToEdgeBottomSheetDialog() {

    private var binding: ViewAiPromptBinding by safeBinding()
    private val aiViewModel: AiViewModel by activityViewModels()
    private val navigationArgs: EuriaPromptBottomSheetArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ViewAiPromptBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.aiPromptView.bind(
            prompt = aiViewModel.aiPrompt,
            placeholder = getString(R.string.aiPromptAnswer),
            onCloseClick = { dismiss() },
            onGenerateClick = {
                setBackNavigationResult(OPEN_AI_REPLY_PROPOSITION, navigationArgs.messageUid)
            },
            onPromptChanged = { aiViewModel.aiPrompt = it },
        )
        binding.aiPromptView.focusPrompt()
    }
}
