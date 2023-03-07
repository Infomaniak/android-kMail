/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.ui.main.menu.MoveFragmentArgs
import com.infomaniak.mail.utils.animatedNavigation
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity

class MessageActionsBottomSheetDialog : MailActionsBottomSheetDialog() {

    private val navigationArgs: MessageActionsBottomSheetDialogArgs by navArgs()

    override val currentClassName: String by lazy { MessageActionsBottomSheetDialog::class.java.name }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel.getMessage(messageUid).observe(viewLifecycleOwner) { message ->

            setMarkAsReadUi(isSeen)
            setFavoriteUi(isFavorite)

            binding.lightTheme.apply {
                if (requireContext().isNightModeEnabled()) {
                    isVisible = true
                    setText(if (isThemeTheSame) R.string.actionViewInLight else R.string.actionViewInDark)
                    setClosingOnClickListener { mainViewModel.toggleLightThemeForMessage.value = message }
                }
            }

            initOnClickListener(object : OnActionClick {
                //region Main actions
                override fun onReply() {
                    trackMessageActionsEvent("replyMessage")
                    safeNavigateToNewMessageActivity(DraftMode.REPLY, messageUid, currentClassName)
                }

                override fun onReplyAll() {
                    trackMessageActionsEvent("replyAllMessage")
                    safeNavigateToNewMessageActivity(DraftMode.REPLY_ALL, messageUid, currentClassName)
                }

                override fun onForward() {
                    trackMessageActionsEvent("forwardMessage")
                    safeNavigateToNewMessageActivity(DraftMode.FORWARD, messageUid, currentClassName)
                }

                override fun onDelete() {
                    trackMessageActionsEvent("trashMessage")
                    mainViewModel.deleteThreadOrMessage(threadUid, message)
                }
                //endregion

                //region Actions
                override fun onArchive(): Unit = with(mainViewModel) {
                    trackMessageActionsEvent("archiveMessage", message.folder.role == FolderRole.ARCHIVE)
                    archiveThreadOrMessage(threadUid, message)
                }

                override fun onReadUnread() {
                    trackMessageActionsEvent("markAsSeenMessage", message.isSeen)
                    mainViewModel.toggleSeenStatus(threadUid, message)
                }

                override fun onMove() {
                    trackMessageActionsEvent("moveMessage")
                    animatedNavigation(
                        resId = R.id.moveFragment,
                        args = MoveFragmentArgs(threadUid, messageUid).toBundle(),
                        currentClassName = currentClassName,
                    )
                }

                override fun onPostpone() {
                    trackMessageActionsEvent("postponeMessage")
                    notYetImplemented()
                }

                override fun onFavorite() {
                    trackMessageActionsEvent("favoriteMessage", message.isFavorite)
                    mainViewModel.toggleFavoriteStatus(threadUid, message)
                }

                override fun onSpam() = Unit

                override fun onReportJunk() {
                    safeNavigate(
                        resId = R.id.junkBottomSheetDialog,
                        args = JunkBottomSheetDialogArgs(threadUid, messageUid).toBundle(),
                        currentClassName = currentClassName,
                    )
                }

                override fun onPrint() {
                    trackMessageActionsEvent("printMessage")
                    notYetImplemented()
                }

                override fun onReportDisplayProblem() {
                    notYetImplemented()
                }
                //endregion
            })
        }
    }
}
