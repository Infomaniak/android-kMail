/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.message

import com.infomaniak.core.network.utils.ErrorCodeTranslated
import com.infomaniak.core.common.utils.ApiEnum
import com.infomaniak.mail.R

enum class EmojiReactionNotAllowedReason(
    override val apiValue: String,
    override val translateRes: Int,
) : ApiEnum, ErrorCodeTranslated {
    EmojiReactionFolderNotAllowedDraft("folder_not_allowed_draft", R.string.errorEmojiReactionFolderNotAllowedDraft),
    EmojiReactionFolderNotAllowedScheduledDraft(
        "folder_not_allowed_scheduled_draft",
        R.string.errorEmojiReactionFolderNotAllowedScheduledDraft
    ),
    EmojiReactionFolderNotAllowedSpam("folder_not_allowed_spam", R.string.errorEmojiReactionFolderNotAllowedSpam),
    EmojiReactionFolderNotAllowedTrash("folder_not_allowed_trash", R.string.errorEmojiReactionFolderNotAllowedTrash),
    EmojiReactionMessageInReplyToNotAllowed(
        "message_in_reply_to_not_allowed",
        R.string.errorEmojiReactionMessageInReplyToNotAllowed
    ),
    EmojiReactionMessageInReplyToNotValid(
        "message_in_reply_to_not_valid",
        R.string.errorEmojiReactionMessageInReplyToNotValid
    ),
    EmojiReactionMessageInReplyToEncrypted("message_in_reply_to_encrypted", R.string.errorEmojiReactionMessageInReplyEncrypted),
    EmojiReactionMaxRecipient("max_recipient", R.string.errorEmojiReactionMaxRecipient),
    EmojiReactionRecipientNotAllowed("recipient_not_allowed", R.string.errorEmojiReactionRecipientNotAllowed);

    override val code: String = "emoji_reaction__$apiValue"
}
