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
package com.infomaniak.mail.ui.main.newMessage

import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentAiPromptBinding
import com.infomaniak.mail.utils.postfixWithTag
import com.google.android.material.R as RMaterial

class AiPromptFragment : Fragment() {

    private lateinit var binding: FragmentAiPromptBinding
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    private val m3BottomSheetMaxWidthPx by lazy { requireContext().resources.getDimension(RMaterial.dimen.material_bottom_sheet_max_width) }

    private val promptTextWatcher by lazy {
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onPromptChanged(s)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setCorrectSheetMargins()
    }

    private fun setCorrectSheetMargins() = with(binding.root) {
        val screenWidth = requireContext().resources.displayMetrics.widthPixels
        val isScreenTooBig = screenWidth > m3BottomSheetMaxWidthPx
        val horizontalMargin = if (isScreenTooBig) m3BottomSheetHorizontalMarginPx else NO_MARGIN

        setMarginsRelative(start = horizontalMargin, end = horizontalMargin)
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAiPromptBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setCorrectSheetMargins()

        aiPromptTitle.text = requireContext().postfixWithTag(
            getString(R.string.aiPromptTitle),
            R.string.aiPromptTag,
            R.color.aiBetaTagBackground,
            R.color.aiBetaTagTextColor,
        )

        prompt.showKeyboard()
        closeButton.setOnClickListener {
            (parentFragment as NewMessageFragment).closeAiPrompt()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.prompt.setText(newMessageViewModel.aiPrompt)
    }

    override fun onResume() {
        super.onResume()
        binding.prompt.addTextChangedListener(promptTextWatcher)
    }

    override fun onPause() {
        binding.prompt.removeTextChangedListener(promptTextWatcher)
        super.onPause()
    }

    private fun onPromptChanged(it: Editable?) = with(binding) {
        generateButton.isEnabled = it?.isNotEmpty() ?: false
        newMessageViewModel.aiPrompt = it.toString()
    }

    private companion object {
        const val NO_MARGIN = 0
        val m3BottomSheetHorizontalMarginPx = 56.toPx()
    }
}
