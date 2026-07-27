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
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackBottomSheetThreadActionsEvent
import com.infomaniak.mail.data.models.extensions.kSuite
import com.infomaniak.mail.databinding.BottomSheetAskEuriaActionsBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.thread.AiActionNavigationResult
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_AI_REPLY_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_AI_SUMMARY_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_AI_TRANSLATE_BOTTOM_SHEET
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView.TrailingContent
import com.infomaniak.mail.ui.main.thread.actions.multiselection.MultiselectionViewModel
import com.infomaniak.mail.utils.openKSuiteProBottomSheet
import com.infomaniak.mail.utils.openMailPremiumBottomSheet
import com.infomaniak.mail.utils.openMyKSuiteUpgradeBottomSheet

class AskEuriaBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetAskEuriaActionsBinding by safeBinding()
    override val multiselectionViewModel: MultiselectionViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: AskEuriaBottomSheetDialogArgs by navArgs()

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

        setupTrailingContentUi(messageUid)
    }

    private fun setupTrailingContentUi(messageUid: String) {
        val mailbox = mainViewModel.currentMailbox.value
        val kSuite = mailbox?.kSuite

        val matomoName = MatomoName.ReplyWithEuria.value
        binding.reply.trailingContent = when (kSuite) {
            KSuite.Perso.Free -> TrailingContent.KSuitePersoChip
            KSuite.Pro.Free, KSuite.StarterPack -> TrailingContent.KSuiteProChip
            else -> TrailingContent.None
        }

        if (binding.reply.trailingContent != TrailingContent.None) {
            binding.reply.setOnClickListener {
                when (kSuite) {
                    KSuite.Perso.Free -> openMyKSuiteUpgradeBottomSheet(matomoName)
                    KSuite.Pro.Free -> openKSuiteProBottomSheet(kSuite, mailbox.isAdmin, matomoName)
                    KSuite.StarterPack -> openMailPremiumBottomSheet(matomoName)
                    else -> Unit
                }
            }
        } else {
            binding.reply.setOnClickListener {
                trackBottomSheetThreadActionsEvent(MatomoName.ReplyWithEuria)
                setBackNavigationResult(OPEN_AI_REPLY_BOTTOM_SHEET, AiActionNavigationResult(messageUid, false))
            }
        }
    }
}

