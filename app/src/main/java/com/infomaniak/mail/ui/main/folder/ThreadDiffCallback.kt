/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.folder

import androidx.recyclerview.widget.DiffUtil
import com.infomaniak.mail.data.models.isSnoozed
import com.infomaniak.mail.utils.Utils.runCatchingRealm

import com.infomaniak.mail.ui.main.folder.ThreadListItem as Item

class ThreadDiffCallback : DiffUtil.ItemCallback<Item>() {

        override fun areItemsTheSame(oldItem: Item, newItem: Item) = runCatchingRealm {
            when (oldItem) {
                is Item.FlushFolderButton -> newItem is Item.FlushFolderButton && oldItem.folderRole == newItem.folderRole // Flush Folder button
                is Item.DateSeparator -> newItem is Item.DateSeparator && oldItem.title == newItem.title // Date separator
                is Item.Content -> newItem is Item.Content && oldItem.thread.uid == newItem.thread.uid // Thread
                is Item.LoadMore -> newItem is Item.LoadMore // Load more button
            }
        }.getOrDefault(false)

        override fun areContentsTheSame(oldItem: Item, newItem: Item) = runCatchingRealm {
            when (oldItem) {
                is Item.FlushFolderButton -> newItem is Item.FlushFolderButton && oldItem.folderRole == newItem.folderRole // Flush Folder button
                is Item.DateSeparator -> newItem is Item.DateSeparator && oldItem.title == newItem.title // Date separator
                is Item.Content -> { // Thread.
                    // Unfortunately, Thread's equals function doesn't work as it should, so we need all this boilerplate
                    // to work it around.
                    if (newItem !is Item.Content || oldItem.thread.uid != newItem.thread.uid) {
                        false
                    } else {

                        val oldThread = oldItem.thread
                        val newThread = newItem.thread

                        val (oldThreadAvatarRecipient, oldThreadAvatarBimi) = oldThread.computeAvatarRecipient()
                        val (newThreadAvatarRecipient, newThreadAvatarBimi) = newThread.computeAvatarRecipient()

                        val oldDisplayedRecipients = oldThread.computeDisplayedRecipients()
                        val newDisplayedRecipients = newThread.computeDisplayedRecipients()
                        val displayedRecipientsAreTheSame =
                            if (oldDisplayedRecipients.count() == newDisplayedRecipients.count()) {
                                oldDisplayedRecipients.filterIndexed { index, recipient ->
                                    recipient != newDisplayedRecipients[index]
                                }.isEmpty()
                            } else {
                                false
                            }

                        oldThread.uid == newThread.uid &&
                                oldThreadAvatarRecipient == newThreadAvatarRecipient &&
                                oldThreadAvatarBimi?.svgContentUrl == newThreadAvatarBimi?.svgContentUrl &&
                                oldThreadAvatarBimi?.isCertified == newThreadAvatarBimi?.isCertified &&
                                oldThread.from.firstOrNull() == newThread.from.firstOrNull() &&
                                oldThread.folderName == newThread.folderName &&
                                oldDisplayedRecipients.count() == newDisplayedRecipients.count() &&
                                displayedRecipientsAreTheSame &&
                                oldThread.subject == newThread.subject &&
                                oldThread.computePreview() == newThread.computePreview() &&
                                oldThread.numberOfScheduledDrafts == newThread.numberOfScheduledDrafts &&
                                oldThread.isSnoozed() == newThread.isSnoozed() &&
                                oldThread.hasDrafts == newThread.hasDrafts &&
                                oldThread.isAnswered == newThread.isAnswered &&
                                oldThread.isForwarded == newThread.isForwarded &&
                                oldThread.hasAttachable == newThread.hasAttachable &&
                                oldThread.isFavorite == newThread.isFavorite &&
                                oldThread.messages.count() == newThread.messages.count() &&
                                oldThread.unseenMessagesCount == newThread.unseenMessagesCount
                    }
                }
                is Item.LoadMore -> newItem is Item.LoadMore // Load more button
            }
        }.getOrDefault(false)
    }
