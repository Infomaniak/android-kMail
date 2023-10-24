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

import android.os.Bundle
import android.transition.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail.trackAiWriterEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.AiReplacementDialogVisibility
import com.infomaniak.mail.data.models.ai.AiPromptOpeningStatus
import com.infomaniak.mail.databinding.DialogAiReplaceContentBinding
import com.infomaniak.mail.databinding.FragmentAiPropositionBinding
import com.infomaniak.mail.ui.alertDialogs.AiDescriptionAlertDialog
import com.infomaniak.mail.ui.newMessage.AiViewModel.PropositionStatus
import com.infomaniak.mail.ui.newMessage.AiViewModel.Shortcut
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

    private var binding: FragmentAiPropositionBinding by safeBinding()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val aiViewModel: AiViewModel by activityViewModels()

    private var currentRequestJob: Job? = null

    private val refinePopupMenu by lazy {
        SimpleIconPopupMenu(requireContext(), R.menu.ai_refining_options, binding.refineButton, ::onMenuItemClicked)
    }

    private val replacementDialog by lazy {
        createReplaceContentDialog(onPositiveButtonClicked = {
            trackAiWriterEvent("replacePropositionConfirm")
            choosePropositionAndPopBack()
        })
    }

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var subjectReplacementDialog: AiDescriptionAlertDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAiPropositionBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        handleBackDispatcher()
        setUi()

        if (aiViewModel.aiPropositionStatusLiveData.value == null) {
            currentRequestJob = aiViewModel.generateNewAiProposition(newMessageViewModel.currentMailbox.email)
        }
        observeAiProposition()
    }

    override fun onDestroy() {
        currentRequestJob?.cancel()
        super.onDestroy()
    }

    private fun handleBackDispatcher() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { trackDismissalAndPopBack() }
    }

    private fun setUi() = with(binding) {
        setToolbar()

        loadingPlaceholder.text = aiViewModel.aiPrompt

        insertPropositionButton.setOnClickListener {
            val doNotAskAgain = localSettings.aiReplacementDialogVisibility == AiReplacementDialogVisibility.HIDE
            val body = newMessageViewModel.draft.uiBody

            if (doNotAskAgain || body.isBlank()) {
                choosePropositionAndPopBack()
            } else {
                trackAiWriterEvent("replacePropositionDialog")
                replacementDialog.show()
            }
        }

        refineButton.setOnClickListener {
            trackAiWriterEvent("refine")
            refinePopupMenu.show()
        }

        retryButton.setOnClickListener {
            trackAiWriterEvent("retry")
            aiViewModel.aiPromptOpeningStatus.value = AiPromptOpeningStatus(isOpened = true)
            findNavController().popBackStack()
        }

        errorBlock.setOnCloseListener {
            trackAiWriterEvent("dismissError")
            TransitionManager.beginDelayedTransition(nestedScrollView, ChangeBounds())
            errorBlock.isGone = true
        }
    }

    private fun setToolbar() = with(binding) {
        changeToolbarColorOnScroll(toolbar, nestedScrollView)
        toolbar.apply {
            setNavigationOnClickListener { trackDismissalAndPopBack() }
            title = requireContext().postfixWithTag(
                getString(R.string.aiPromptTitle),
                R.string.aiPromptTag,
                R.color.aiBetaTagBackground,
                R.color.aiBetaTagTextColor
            )
        }
    }

    private fun trackDismissalAndPopBack() {
        trackAiWriterEvent("dismissProposition")
        findNavController().popBackStack()
    }

    private fun choosePropositionAndPopBack() = with(aiViewModel) {

        fun applyProposition(subject: String?, content: String) {
            trackInsertionType()
            aiOutputToInsert.value = subject to content
            findNavController().popBackStack()
        }

        val (subject, content) = splitBodyAndSubject(getLastMessage())

        if (subject == null || newMessageViewModel.draft.subject.isNullOrBlank()) {
            applyProposition(subject, content)
        } else {
            trackAiWriterEvent("replaceSubjectDialog")
            subjectReplacementDialog.show(
                title = getString(R.string.aiReplaceSubjectTitle),
                description = getString(R.string.aiReplaceSubjectDescription, subject),
                displayLoader = false,
                displayCancelButton = true,
                positiveButtonText = R.string.aiReplacementDialogPositiveButton,
                negativeButtonText = R.string.buttonNo,
                onPositiveButtonClicked = {
                    trackAiWriterEvent("replaceSubjectConfirm")
                    applyProposition(subject, content)
                },
                onNegativeButtonClicked = {
                    trackAiWriterEvent("keepSubject")
                    applyProposition(null, content)
                },
            )
        }
    }

    private fun trackInsertionType() {
        val willReplace = newMessageViewModel.draft.uiBody.isNotBlank()
        if (willReplace) {
            trackAiWriterEvent("replaceProposition", TrackerAction.DATA)
        } else {
            trackAiWriterEvent("insertProposition", TrackerAction.DATA)
        }
    }

    private fun splitBodyAndSubject(proposition: String): Pair<String?, String> {
        val match = MATCH_SUBJECT_REGEX.find(proposition)

        val content = match?.groups?.get("content")?.value ?: return null to proposition
        val subject = match.groups["subject"]?.value?.trim()

        if (subject.isNullOrBlank()) return null to proposition

        return subject to content
    }

    private fun onMenuItemClicked(menuItemId: Int) = with(aiViewModel) {
        val shortcut = Shortcut.values().find { it.menuId == menuItemId }!!
        trackAiWriterEvent(shortcut.matomoValue)

        if (shortcut == Shortcut.MODIFY) {
            aiPromptOpeningStatus.value = AiPromptOpeningStatus(isOpened = true, shouldResetPrompt = false)
            findNavController().popBackStack()
        } else {
            binding.loadingPlaceholder.text = getLastMessage()
            aiPropositionStatusLiveData.value = null
            currentRequestJob = performShortcut(shortcut, newMessageViewModel.currentMailbox.email)
        }
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
            .setOnDismissListener { if (checkbox.isChecked) trackAiWriterEvent("doNotShowAgain", TrackerAction.DATA) }
            .create()
    }

    private fun observeAiProposition() {

        fun sendMissingContentSentry(status: PropositionStatus) {
            if (status == PropositionStatus.MISSING_CONTENT) {
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.ERROR
                    Sentry.captureMessage("AI call succeeded but no content returned")
                }
            }
        }

        aiViewModel.aiPropositionStatusLiveData.observe(viewLifecycleOwner) { propositionStatus ->
            if (propositionStatus == null) {
                setUiVisibilityState(UiState.LOADING)
                return@observe
            }

            when (propositionStatus) {
                PropositionStatus.SUCCESS -> {
                    displaySuccess()
                }
                PropositionStatus.ERROR,
                PropositionStatus.PROMPT_TOO_LONG,
                PropositionStatus.RATE_LIMIT_EXCEEDED,
                PropositionStatus.MISSING_CONTENT -> {
                    sendMissingContentSentry(propositionStatus)
                    displayError(propositionStatus)
                }
            }
        }
    }

    private fun displaySuccess() {
        val autoTransition = AutoTransition()
        autoTransition.duration = REPLACEMENT_DURATION
        TransitionManager.beginDelayedTransition(binding.contentLayout, autoTransition)

        binding.propositionTextView.text = aiViewModel.getLastMessage()
        setUiVisibilityState(UiState.PROPOSITION)
    }

    private fun displayError(status: PropositionStatus) {
        binding.errorBlock.setText(status.errorRes!!)
        setUiVisibilityState(UiState.ERROR)
    }

    private fun setUiVisibilityState(state: UiState) {
        when (state) {
            UiState.LOADING -> displayLoadingVisibility()
            UiState.PROPOSITION -> displayPropositionVisibility()
            UiState.ERROR -> {
                binding.nestedScrollView.smoothScrollTo(0, 0)
                displayErrorVisibility()
            }
        }
    }

    private fun displayLoadingVisibility() = with(binding) {
        loadingPlaceholder.isVisible = true
        generationLoader.isVisible = true

        propositionTextView.isGone = true
        buttonLayout.isInvisible = true

        errorBlock.isGone = true
        retryButton.isGone = true

        refinePopupMenu.enableAllItems()
    }

    private fun displayPropositionVisibility() = with(binding) {
        loadingPlaceholder.isGone = true
        generationLoader.isGone = true

        propositionTextView.isVisible = true
        buttonLayout.isVisible = true

        errorBlock.isGone = true
        retryButton.isGone = true

        refinePopupMenu.enableAllItems()
    }

    private fun displayErrorVisibility() = with(binding) {
        val transition = TransitionSet()
            .setOrdering(TransitionSet.ORDERING_SEQUENTIAL)
            .addTransition(ChangeBounds())
            .addTransition(Fade(Fade.IN).addTarget(errorBlock))

        TransitionManager.beginDelayedTransition(nestedScrollView, transition)

        val isFirstTry = aiViewModel.isHistoryEmpty()
        if (isFirstTry) {
            loadingPlaceholder.isVisible = true
            propositionTextView.isGone = true

            buttonLayout.isInvisible = true
            retryButton.isVisible = true
        } else {
            loadingPlaceholder.isGone = true
            propositionTextView.isVisible = true

            buttonLayout.isVisible = true
            retryButton.isGone = true
        }

        generationLoader.isGone = true

        errorBlock.isVisible = true

        refinePopupMenu.disableAllItemButModify()
    }

    enum class UiState {
        LOADING,
        PROPOSITION,
        ERROR,
    }

    private companion object {
        const val REPLACEMENT_DURATION: Long = 150

        val MATCH_SUBJECT_REGEX = Regex("^[^:]+:(?<subject>.+?)\\n\\s*(?<content>.+)", RegexOption.DOT_MATCHES_ALL)
    }
}
