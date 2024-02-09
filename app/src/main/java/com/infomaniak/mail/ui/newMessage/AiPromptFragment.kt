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
package com.infomaniak.mail.ui.newMessage

import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.toSpannable
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.FragmentAiPromptBinding
import com.infomaniak.mail.utils.extensions.TagColor
import com.infomaniak.mail.utils.extensions.postfixWithTag
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.android.material.R as RMaterial

@AndroidEntryPoint
class AiPromptFragment : Fragment() {

    @Inject
    lateinit var localSettings: LocalSettings

    private var binding: FragmentAiPromptBinding by safeBinding()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val aiViewModel: AiViewModel by activityViewModels()

    private val currentClassName: String by lazy { AiPromptFragment::class.java.name }

    private val newMessageFragment by lazy { parentFragment as NewMessageFragment }

    private val m3BottomSheetMaxWidthPx by lazy { resources.getDimension(RMaterial.dimen.material_bottom_sheet_max_width) }

    private val promptTextWatcher by lazy {
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onPromptChanged(s)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAiPromptBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUi()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setCorrectSheetMargins()
    }

    private fun setUi() = with(binding) {
        setCorrectSheetMargins()

        aiPromptTitle.text = requireContext().postfixWithTag(
            getString(R.string.aiPromptTitle),
            R.string.aiPromptTag,
            TagColor(R.color.aiBetaTagBackground, R.color.aiBetaTagTextColor)
        )

        prompt.showKeyboard()
        initPromptTextAndPlaceholder()
        closeButton.setOnClickListener { newMessageFragment.closeAiPrompt() }

        generateWithButton.setOnClickListener {
            trackEvent("promptAiEngine", "openEngineChoice")
            safeNavigate(NewMessageFragmentDirections.actionNewMessageFragmentToAiEngineChoiceFragment(), currentClassName)
        }

        aiEngineIcon.setImageResource(localSettings.aiEngine.iconRes)

        generateButton.setOnClickListener {
            newMessageViewModel.shouldExecuteDraftActionWhenStopping = false
            newMessageFragment.navigateToPropositionFragment()
        }

        // When the app is recreated or the prompt is opened when coming back from AiPropositionFragment,
        // the enabled state of the button is not recomputed when using `onPromptChanged()`. This means
        // that the button may remain disabled even though it should be enabled based on the current
        // prompt. `onPromptChanged()` is not enough which is why it's done in `doAfterTextChanged()`.
        prompt.doAfterTextChanged(::updateButtonEnabledState)
    }

    private fun setCorrectSheetMargins() = with(binding.root) {
        val screenWidth = resources.displayMetrics.widthPixels
        val isScreenTooBig = screenWidth > m3BottomSheetMaxWidthPx
        val horizontalMargin = if (isScreenTooBig) m3BottomSheetHorizontalMarginPx else NO_MARGIN

        setMarginsRelative(start = horizontalMargin, end = horizontalMargin)
    }

    private fun initPromptTextAndPlaceholder() = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
        viewLifecycleOwner.withStarted {
            with(binding.prompt) {
                setText(aiViewModel.aiPrompt)
                setSelection(length())

                val isReplying = aiViewModel.previousMessageBodyPlainText != null
                val placeholder = if (isReplying) getReplyPlaceholder() else getPlaceholderFromScratch()
                hint = placeholder
            }
        }
    }

    private fun getReplyPlaceholder(): String = getString(R.string.aiPromptAnswer)

    private fun getPlaceholderFromScratch(): String {
        val promptExample = getString(promptExamples.random())
        return getString(R.string.aiPromptPlaceholder, promptExample)
    }

    private fun updateButtonEnabledState(prompt: Editable?) {
        binding.generateButton.isEnabled = prompt?.isNotEmpty() ?: false
    }

    override fun onResume() {
        super.onResume()
        binding.prompt.addTextChangedListener(promptTextWatcher)
    }

    override fun onPause() {
        binding.prompt.removeTextChangedListener(promptTextWatcher)
        super.onPause()
    }

    private fun onPromptChanged(prompt: Editable?) {
        aiViewModel.aiPrompt = prompt.toString()
    }

    companion object {
        private const val NO_MARGIN = 0

        private val m3BottomSheetHorizontalMarginPx = 56.toPx()
        private val promptExamples = listOf(
            R.string.aiPromptExample1,
            R.string.aiPromptExample2,
            R.string.aiPromptExample3,
            R.string.aiPromptExample4,
            R.string.aiPromptExample5,
        )
    }
}
