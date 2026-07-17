/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.ui.view.extension.setMargins
import com.infomaniak.core.ui.view.extension.setMarginsRelative
import com.infomaniak.core.ui.view.utils.toPx
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewAiPromptBinding
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.ime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.google.android.material.R as RMaterial

@AndroidEntryPoint
class AiPromptFragment : Fragment() {

    private var binding: ViewAiPromptBinding by safeBinding()
    private val aiViewModel: AiViewModel by activityViewModels()
    private val newMessageFragment by lazy { parentFragment as NewMessageFragment }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ViewAiPromptBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.applyWindowInsetsListener { root, insets ->
            val imeBottomInsets = insets.ime().bottom
            root.setMargins(bottom = imeBottomInsets)
        }
        setUi()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setCorrectSheetMargins()
    }

    private fun setUi() = with(binding.root) {
        setCorrectSheetMargins()
        focusPrompt()

        bind(
            prompt = aiViewModel.aiPrompt,
            placeholder = getPromptPlaceholder(),
            onCloseClick = { newMessageFragment.closeAiPrompt() },
            onGenerateClick = {
                viewLifecycleOwner.lifecycleScope.launch {
                    newMessageFragment.navigateToPropositionFragment()
                }
            },
            onPromptChanged = { aiViewModel.aiPrompt = it },
        )
    }

    private fun setCorrectSheetMargins() = with(binding.root) {
        val maxWidthPx = resources.getDimension(RMaterial.dimen.material_bottom_sheet_max_width)
        val horizontalMarginPx = 56.toPx(this)
        val horizontalMargin = if (resources.displayMetrics.widthPixels > maxWidthPx) horizontalMarginPx else 0
        setMarginsRelative(start = horizontalMargin, end = horizontalMargin)
    }

    private fun getPromptPlaceholder(): String {
        val isReplying = aiViewModel.previousMessageBodyPlainText != null
        return if (isReplying) {
            getString(R.string.aiPromptAnswer)
        } else {
            getString(R.string.aiPromptPlaceholder, getString(promptExamples.random()))
        }
    }

    companion object {
        private val promptExamples = listOf(
            R.string.aiPromptExample1,
            R.string.aiPromptExample2,
            R.string.aiPromptExample3,
            R.string.aiPromptExample4,
            R.string.aiPromptExample5,
        )
    }
}
