/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.extensions.isNightModeEnabled
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackBottomSheetMessageActionsEvent
import com.infomaniak.mail.MatomoMail.trackBottomSheetThreadActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.move.MoveFragmentArgs
import com.infomaniak.mail.ui.main.thread.PrintMailFragmentArgs
import com.infomaniak.mail.ui.main.thread.ThreadFragment.Companion.OPEN_REACTION_BOTTOM_SHEET
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.archiveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.moveWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.navigateToDownloadMessagesProgressDialog
import com.infomaniak.mail.utils.extensions.safeNavigateToNewMessageActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MessageActionsBottomSheetDialog : MailActionsBottomSheetDialog() {

    private val navigationArgs: MessageActionsBottomSheetDialogArgs by navArgs()

    private val currentClassName: String by lazy { MessageActionsBottomSheetDialog::class.java.name }
    override val shouldCloseMultiSelection: Boolean = false

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    @Inject
    lateinit var folderRoleUtils: FolderRoleUtils

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)
        binding.print.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            val message = mainViewModel.getMessage(messageUid)
            val folderRole = folderRoleUtils.getActionFolderRole(message)

            setMarkAsReadUi(message.isSeen)
            setArchiveUi(isFromArchive = folderRole == FolderRole.ARCHIVE)
            setFavoriteUi(message.isFavorite)
            setReactionUi(message.isValidReactionTarget)
            setJunkMessageUi()

            if (requireContext().isNightModeEnabled()) {
                binding.lightTheme.apply {
                    isVisible = true
                    setTitle(if (isThemeTheSame) R.string.actionViewInLight else R.string.actionViewInDark)
                    setClosingOnClickListener { mainViewModel.toggleLightThemeForMessage.value = message }
                }
            }

            initActionClickListener(messageUid, message, threadUid)
        }
        Unit
    }

    private fun initActionClickListener(messageUid: String, message: Message, threadUid: String) {
        initOnClickListener(object : OnActionClick {
            //region Main actions
            override fun onReply() {
                trackBottomSheetMessageActionsEvent(MatomoName.Reply)
                safeNavigateToNewMessageActivity(
                    draftMode = DraftMode.REPLY,
                    previousMessageUid = messageUid,
                    currentClassName = currentClassName,
                    shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
                )
            }

            override fun onReplyAll() {
                trackBottomSheetMessageActionsEvent(MatomoName.ReplyAll)
                safeNavigateToNewMessageActivity(
                    draftMode = DraftMode.REPLY_ALL,
                    previousMessageUid = messageUid,
                    currentClassName = currentClassName,
                    shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
                )
            }

            override fun onForward() {
                trackBottomSheetMessageActionsEvent(MatomoName.Forward)
                safeNavigateToNewMessageActivity(
                    draftMode = DraftMode.FORWARD,
                    previousMessageUid = messageUid,
                    currentClassName = currentClassName,
                    shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
                )
            }

            override fun onDelete() {
                descriptionDialog.deleteWithConfirmationPopup(message.folder.role, count = 1) {
                    trackBottomSheetMessageActionsEvent(MatomoName.Delete)
                    mainViewModel.deleteMessage(threadUid, message)
                }
            }
            //endregion

            //region Actions
            override fun onArchive() {
                descriptionDialog.archiveWithConfirmationPopup(message.folder.role, count = 1) {
                    trackBottomSheetMessageActionsEvent(MatomoName.Archive, message.folder.role == FolderRole.ARCHIVE)
                    mainViewModel.archiveMessage(threadUid, message)
                }
            }

            override fun onReadUnread() {
                trackBottomSheetMessageActionsEvent(MatomoName.MarkAsSeen, message.isSeen)
                mainViewModel.toggleMessageSeenStatus(threadUid, message)
                twoPaneViewModel.closeThread()
            }

            override fun onMove() {
                trackBottomSheetMessageActionsEvent(MatomoName.Move)
                val navController = findNavController()
                descriptionDialog.moveWithConfirmationPopup(message.folder.role, count = 1) {
                    navController.animatedNavigation(
                        resId = R.id.moveFragment,
                        args = MoveFragmentArgs(arrayOf(threadUid), messageUid).toBundle(),
                        currentClassName = currentClassName,
                    )
                }
            }

            override fun onAddReaction() {
                trackBottomSheetMessageActionsEvent(MatomoName.OpenEmojiPicker)
                setBackNavigationResult(OPEN_REACTION_BOTTOM_SHEET, messageUid)
            }

            override fun onSnooze() = Unit

            override fun onModifySnooze() = Unit

            override fun onCancelSnooze() = Unit

            override fun onFavorite() {
                trackBottomSheetMessageActionsEvent(MatomoName.Favorite, message.isFavorite)
                mainViewModel.toggleMessageFavoriteStatus(threadUid, message)
            }

            override fun onSpam() = Unit
            override fun onPhishing() = Unit
            override fun onBlockSender() = Unit

            override fun onPrint() {
                trackBottomSheetMessageActionsEvent(MatomoName.Print)
                safeNavigate(
                    resId = R.id.printMailFragment,
                    args = PrintMailFragmentArgs(messageUid).toBundle(),
                    currentClassName = MessageActionsBottomSheetDialog::class.java.name,
                )
            }

            override fun onShare() {
                activity?.apply {
                    trackBottomSheetThreadActionsEvent(MatomoName.ShareLink)
                    mainViewModel.shareThreadUrl(message.uid)
                }
            }

            override fun onSaveToKDrive() {
                trackBottomSheetThreadActionsEvent(MatomoName.SaveToKDrive)
                navigateToDownloadMessagesProgressDialog(
                    messageUids = listOf(messageUid),
                    currentClassName = MessageActionsBottomSheetDialog::class.java.name,
                )
            }

            override fun onReportDisplayProblem() {
                descriptionDialog.show(
                    title = getString(R.string.reportDisplayProblemTitle),
                    description = getString(R.string.reportDisplayProblemDescription),
                    onPositiveButtonClicked = { mainViewModel.reportDisplayProblem(message.uid) },
                )
            }
            //endregion
        })
    }
}
