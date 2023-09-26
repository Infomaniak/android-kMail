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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AiReplacementDialogVisibility
import com.infomaniak.mail.databinding.DialogAiReplaceContentBinding
import com.infomaniak.mail.databinding.FragmentAiPropositionBinding
import com.infomaniak.mail.ui.main.newMessage.AiViewModel.PropositionStatus
import com.infomaniak.mail.utils.SimpleIconPopupMenu
import com.infomaniak.mail.utils.changeToolbarColorOnScroll
import com.infomaniak.mail.utils.postfixWithTag
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Job
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@AndroidEntryPoint
class AiPropositionFragment : Fragment() {

    private lateinit var binding: FragmentAiPropositionBinding
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val aiViewModel: AiViewModel by activityViewModels()

    private var requestJob: Job? = null

    private val refinePopupMenu by lazy {
        SimpleIconPopupMenu(requireContext(), R.menu.ai_refining_options, binding.refineButton)
    }

    private val replacementDialog by lazy { createReplaceContentDialog(onPositiveButtonClicked = ::choosePropositionAndBack) }

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAiPropositionBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setUi()

        if (aiViewModel.aiProposition.value == null) requestJob = aiViewModel.generateAiProposition()
        observeAiProposition()
    }

    override fun onDestroy() {
        requestJob?.cancel()
        super.onDestroy()
    }

    private fun setUi() = with(binding) {
        setToolbar()

        promptPreview.text = aiViewModel.aiPrompt

        insertPropositionButton.setOnClickListener {
            val doNotAskAgain = localSettings.aiReplacementDialogVisibility == AiReplacementDialogVisibility.HIDE
            val body = newMessageViewModel.draft.uiBody

            if (doNotAskAgain || body.isBlank()) choosePropositionAndBack() else replacementDialog.show()
        }

        refineButton.setOnClickListener { refinePopupMenu.show() }
    }

    private fun setToolbar() = with(binding) {
        changeToolbarColorOnScroll(toolbar, nestedScrollView)
        toolbar.apply {
            setNavigationOnClickListener { findNavController().popBackStack() }
            title = requireContext().postfixWithTag(
                getString(R.string.aiPromptTitle),
                R.string.aiPromptTag,
                R.color.aiBetaTagBackground,
                R.color.aiBetaTagTextColor
            )
        }
    }

    private fun choosePropositionAndBack() {
        aiViewModel.aiOutputToInsert.value = aiViewModel.aiProposition.value!!.second
        findNavController().popBackStack()
    }

    private fun Fragment.createReplaceContentDialog(
        onPositiveButtonClicked: () -> Unit,
    ) = with(DialogAiReplaceContentBinding.inflate(layoutInflater)) {
        dialogDescriptionLayout.apply {
            dialogTitle.text = getString(R.string.aiReplacementDialogTitle)
            dialogDescription.text = getString(R.string.aiReplacementDialogDescription)
        }

        checkbox.apply {
            isChecked = localSettings.aiReplacementDialogVisibility == AiReplacementDialogVisibility.HIDE
            setOnCheckedChangeListener { _, isChecked ->
                localSettings.aiReplacementDialogVisibility = if (isChecked) {
                    AiReplacementDialogVisibility.HIDE
                } else {
                    AiReplacementDialogVisibility.SHOW
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.AiCursorAndPrimaryColorTheme)
            .setView(root)
            .setPositiveButton(R.string.aiReplacementDialogPositiveButton) { _, _ -> onPositiveButtonClicked() }
            .setNegativeButton(RCore.string.buttonCancel, null)
            .create()
    }

    private fun observeAiProposition() = with(binding) {
        aiViewModel.aiProposition.observe(viewLifecycleOwner) { proposition ->
            if (proposition == null) return@observe

            val (status, body) = proposition

            when (status) {
                PropositionStatus.SUCCESS -> {
                    propositionTextView.text = body
                    setUiVisibilityState(UiState.PROPOSITION)
                }
                PropositionStatus.ERROR,
                PropositionStatus.MAX_TOKEN_EXCEEDED,
                PropositionStatus.RATE_LIMIT_EXCEEDED -> {
                    errorMessage.setText(status.errorRes!!)
                    setUiVisibilityState(UiState.ERROR, status)
                }
                PropositionStatus.MISSING_CONTENT -> {
                    Sentry.withScope { scope ->
                        scope.level = SentryLevel.ERROR
                        Sentry.captureMessage("AI call succeeded but no content returned")
                    }
                    errorMessage.setText(status.errorRes!!)
                    setUiVisibilityState(UiState.ERROR, status)
                }
            }
        }
    }

    private fun setUiVisibilityState(state: UiState, errorType: PropositionStatus? = null) = with(binding) {
        fun setAdditionalErrorStyle() {
            val displayRetryButton = errorType == PropositionStatus.MAX_TOKEN_EXCEEDED

            retryButton.isVisible = displayRetryButton
            buttonLayout.isGone = displayRetryButton
            if (!displayRetryButton) {
                insertPropositionButton.isEnabled = false
                refineButton.isGone = true
            }
        }

        when (state) {
            UiState.LOADING -> {
                promptPreview.isVisible = true
                generationLoader.isVisible = true

                propositionTextView.isGone = true
                buttonLayout.isInvisible = true

                errorMessage.isGone = true
                retryButton.isGone = true
            }
            UiState.PROPOSITION -> {
                promptPreview.isGone = true
                generationLoader.isGone = true

                propositionTextView.isVisible = true
                buttonLayout.isVisible = true

                errorMessage.isGone = true
                retryButton.isGone = true

                insertPropositionButton.isEnabled = true
                refineButton.isVisible = true
            }
            UiState.ERROR -> {
                promptPreview.isGone = true
                generationLoader.isGone = true

                propositionTextView.isGone = true

                errorMessage.isVisible = true

                setAdditionalErrorStyle()
            }
        }
    }

    enum class UiState {
        LOADING,
        PROPOSITION,
        ERROR,
    }
}
