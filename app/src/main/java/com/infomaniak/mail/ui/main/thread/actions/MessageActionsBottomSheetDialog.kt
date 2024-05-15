/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.isNightModeEnabled
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.ACTION_ARCHIVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_DELETE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FAVORITE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FORWARD_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MARK_AS_SEEN_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MOVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_POSTPONE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_PRINT_NAME
import com.infomaniak.mail.MatomoMail.ACTION_REPLY_ALL_NAME
import com.infomaniak.mail.MatomoMail.ACTION_REPLY_NAME
import com.infomaniak.mail.MatomoMail.trackBottomSheetMessageActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.menu.MoveFragmentArgs
import com.infomaniak.mail.ui.main.thread.PrintMailFragmentArgs
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.deleteWithConfirmationPopup
import com.infomaniak.mail.utils.extensions.notYetImplemented
import com.infomaniak.mail.utils.extensions.safeNavigateToNewMessageActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MessageActionsBottomSheetDialog : MailActionsBottomSheetDialog() {

    private val navigationArgs: MessageActionsBottomSheetDialogArgs by navArgs()

    private val currentClassName: String by lazy { MessageActionsBottomSheetDialog::class.java.name }

    private var folderRole: FolderRole? = null

    override val shouldCloseMultiSelection: Boolean = false
    
    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)
        binding.print.isVisible = true

        mainViewModel.getMessage(messageUid).observe(viewLifecycleOwner) { message ->

            folderRole = mainViewModel.getActionFolderRole(message)

            setMarkAsReadUi(message.isSeen)
            setArchiveUi(isFromArchive = folderRole == FolderRole.ARCHIVE)
            setFavoriteUi(message.isFavorite)

            if (requireContext().isNightModeEnabled()) {
                binding.lightTheme.apply {
                    isVisible = true
                    setText(if (isThemeTheSame) R.string.actionViewInLight else R.string.actionViewInDark)
                    setClosingOnClickListener { mainViewModel.toggleLightThemeForMessage.value = message }
                }
            }

            initOnClickListener(object : OnActionClick {
                //region Main actions
                override fun onReply() {
                    trackBottomSheetMessageActionsEvent(ACTION_REPLY_NAME)
                    safeNavigateToNewMessageActivity(
                        draftMode = DraftMode.REPLY,
                        previousMessageUid = messageUid,
                        currentClassName = currentClassName,
                        shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
                    )
                }

                override fun onReplyAll() {
                    trackBottomSheetMessageActionsEvent(ACTION_REPLY_ALL_NAME)
                    safeNavigateToNewMessageActivity(
                        draftMode = DraftMode.REPLY_ALL,
                        previousMessageUid = messageUid,
                        currentClassName = currentClassName,
                        shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
                    )
                }

                override fun onForward() {
                    trackBottomSheetMessageActionsEvent(ACTION_FORWARD_NAME)
                    safeNavigateToNewMessageActivity(
                        draftMode = DraftMode.FORWARD,
                        previousMessageUid = messageUid,
                        currentClassName = currentClassName,
                        shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
                    )
                }

                override fun onDelete() {
                    descriptionDialog.deleteWithConfirmationPopup(folderRole, count = 1) {
                        trackBottomSheetMessageActionsEvent(ACTION_DELETE_NAME)
                        mainViewModel.deleteMessage(threadUid, message)
                    }
                }
                //endregion

                //region Actions
                override fun onArchive() {
                    trackBottomSheetMessageActionsEvent(ACTION_ARCHIVE_NAME, message.folder.role == FolderRole.ARCHIVE)
                    mainViewModel.archiveMessage(threadUid, message)
                }

                override fun onReadUnread() {
                    trackBottomSheetMessageActionsEvent(ACTION_MARK_AS_SEEN_NAME, message.isSeen)
                    mainViewModel.toggleMessageSeenStatus(threadUid, message)
                    twoPaneViewModel.closeThread()
                }

                override fun onMove() {
                    trackBottomSheetMessageActionsEvent(ACTION_MOVE_NAME)
                    animatedNavigation(
                        resId = R.id.moveFragment,
                        args = MoveFragmentArgs(arrayOf(threadUid), messageUid).toBundle(),
                        currentClassName = currentClassName,
                    )
                }

                override fun onPostpone() {
                    trackBottomSheetMessageActionsEvent(ACTION_POSTPONE_NAME)
                    notYetImplemented()
                }

                override fun onFavorite() {
                    trackBottomSheetMessageActionsEvent(ACTION_FAVORITE_NAME, message.isFavorite)
                    mainViewModel.toggleMessageFavoriteStatus(threadUid, message)
                }

                override fun onReportJunk() = Unit

                override fun onPrint() {
                    trackBottomSheetMessageActionsEvent(ACTION_PRINT_NAME)
                    safeNavigate(
                        resId = R.id.printMailFragment,
                        args = PrintMailFragmentArgs(messageUid).toBundle(),
                        currentClassName = MessageActionsBottomSheetDialog::class.java.name,
                    )
                }

                override fun onReportDisplayProblem() {
                    notYetImplemented()
                }
                //endregion
            })
        }
    }
}
