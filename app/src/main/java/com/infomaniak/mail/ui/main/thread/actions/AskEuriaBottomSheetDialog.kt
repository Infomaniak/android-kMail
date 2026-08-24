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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackBottomSheetThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.extensions.kSuite
import com.infomaniak.mail.databinding.BottomSheetAskEuriaActionsBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.thread.AiActionNavigationResult
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_AI_SUMMARY_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_AI_TRANSLATE_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView.TrailingContent
import com.infomaniak.mail.ui.main.thread.actions.multiselection.MultiselectionViewModel
import com.infomaniak.mail.ui.newMessage.AiViewModel
import com.infomaniak.mail.utils.SharedUtils
import com.infomaniak.mail.utils.extensions.replyWithConfirmationPopup
import com.infomaniak.mail.utils.openKSuiteUpsellOrElse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AskEuriaBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetAskEuriaActionsBinding by safeBinding()
    private val navigationArgs: AskEuriaBottomSheetDialogArgs by navArgs()
    override val multiselectionViewModel: MultiselectionViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val aiViewModel: AiViewModel by activityViewModels()

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetAskEuriaActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        binding.summary.setOnClickListener {
            trackBottomSheetThreadActionsEvent(MatomoName.Summarize)
            setBackNavigationResult(OPEN_AI_SUMMARY_BOTTOM_SHEET, AiActionNavigationResult(messageUid, isAlreadySummarized))
        }

        binding.translate.setOnClickListener {
            trackBottomSheetThreadActionsEvent(MatomoName.Translate)
            setBackNavigationResult(OPEN_AI_TRANSLATE_BOTTOM_SHEET, AiActionNavigationResult(messageUid, isAlreadyTranslated))
        }

        setupReplyAction(messageUid)
    }

    private fun setupReplyAction(messageUid: String) {
        val mailbox = mainViewModel.currentMailbox.value
        val kSuite = mailbox?.kSuite

        binding.reply.apply {
            trailingContent = when (kSuite) {
                KSuite.Perso.Free -> TrailingContent.KSuitePersoChip
                KSuite.Pro.Free, KSuite.StarterPack -> TrailingContent.KSuiteProChip
                else -> if (localSettings.hasAlreadyUsedReplyWithEuria) TrailingContent.None else TrailingContent.New
            }

            setOnClickListener {
                openKSuiteUpsellOrElse(
                    kSuite = kSuite,
                    isAdmin = mailbox?.isAdmin ?: false,
                    matomoName = MatomoName.ReplyWithEuria.value,
                    onFeatureAvailable = { handleStandardReplyAction(messageUid) }
                )
            }
        }
    }

    private fun handleStandardReplyAction(messageUid: String) {
        lifecycleScope.launch {
            val message = mainViewModel.getMessage(messageUid)
            val hasNoReplyRecipients = message?.let {
                SharedUtils.hasNoReplyRecipients(it, isReplyAll = true)
            } ?: false

            if (message != null) {
                descriptionDialog.replyWithConfirmationPopup(
                    hasNoReplyRecipients = hasNoReplyRecipients,
                    onPositiveButtonClicked = ::executeReplyAction
                )
            } else {
                executeReplyAction()
            }
        }
    }

    private fun executeReplyAction(messageUid: String = navigationArgs.messageUid) {
        trackBottomSheetThreadActionsEvent(MatomoName.ReplyWithEuria)
        localSettings.hasAlreadyUsedReplyWithEuria = true
        binding.reply.newFeatureBadgeVisible = false
        aiViewModel.resetAiState()
        safelyNavigate(
            resId = R.id.action_askEuriaBottomSheetDialog_to_euriaPromptBottomSheetDialog,
            args = EuriaPromptBottomSheetArgs(messageUid = messageUid).toBundle(),
        )
    }
}
