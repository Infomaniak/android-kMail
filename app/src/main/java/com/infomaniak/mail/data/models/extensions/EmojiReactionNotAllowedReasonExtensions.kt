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
package com.infomaniak.mail.data.models.extensions

import com.infomaniak.core.network.utils.ErrorCodeTranslated
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionFolderNotAllowedDraft
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionFolderNotAllowedScheduledDraft
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionFolderNotAllowedSpam
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionFolderNotAllowedTrash
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionMaxRecipient
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionMessageInReplyToEncrypted
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionMessageInReplyToNotAllowed
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionMessageInReplyToNotValid
import com.infomaniak.mail.data.models.message.EmojiReactionNotAllowedReason.EmojiReactionRecipientNotAllowed

fun EmojiReactionNotAllowedReason.getTranslateRes(): Int = when (this) {
    EmojiReactionFolderNotAllowedDraft -> R.string.errorEmojiReactionFolderNotAllowedDraft
    EmojiReactionFolderNotAllowedScheduledDraft -> R.string.errorEmojiReactionFolderNotAllowedScheduledDraft
    EmojiReactionFolderNotAllowedSpam -> R.string.errorEmojiReactionFolderNotAllowedSpam
    EmojiReactionFolderNotAllowedTrash -> R.string.errorEmojiReactionFolderNotAllowedTrash
    EmojiReactionMessageInReplyToNotAllowed -> R.string.errorEmojiReactionMessageInReplyToNotAllowed
    EmojiReactionMessageInReplyToNotValid -> R.string.errorEmojiReactionMessageInReplyToNotValid
    EmojiReactionMessageInReplyToEncrypted -> R.string.errorEmojiReactionMessageInReplyEncrypted
    EmojiReactionMaxRecipient -> R.string.errorEmojiReactionMaxRecipient
    EmojiReactionRecipientNotAllowed -> R.string.errorEmojiReactionRecipientNotAllowed
}

fun EmojiReactionNotAllowedReason.asErrorCodeTranslated(): ErrorCodeTranslated = object : ErrorCodeTranslated {
    override val code: String = "emoji_reaction__$apiValue"
    override val translateRes: Int = getTranslateRes()
}
